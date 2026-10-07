/*
 * Copyright (c) 2002-2026, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.appointment.modules.importer.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentExcelValidationResult;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.genericattributes.business.EntryFilter;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.util.AppLogService;

/** Validates an upload then persists its immutable source data for daemon processing. */
public final class AppointmentImportService
{
    private static final String ERROR_COLUMN_NOT_IN_FORM = "module.appointment.importer.error.column.notInForm";
    private static final String ERROR_FORM_ENTRY_MISSING = "module.appointment.importer.error.column.missingInFile";

    private final AppointmentExcelReader _reader = new AppointmentExcelReader( );

    /**
     * Computes the SHA-256 hex digest of the given bytes.
     * Used to detect duplicate uploads before registering a new file.
     */
    public static String computeFileHash( byte [ ] bytes )
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
            byte [ ] hash = digest.digest( bytes );
            StringBuilder sb = new StringBuilder( hash.length * 2 );
            for ( byte b : hash )
            {
                sb.append( String.format( "%02x", b ) );
            }
            return sb.toString( );
        }
        catch( NoSuchAlgorithmException e )
        {
            // SHA-256 is guaranteed by the JVM spec — this never happens
            throw new IllegalStateException( "SHA-256 not available", e );
        }
    }

    /**
     * Reads and validates the workbook, then persists the file record and all appointment rows.
     * If validation fails the file is stored with status {@code VALIDATION_FAILED} and no rows are created.
     *
     * @param strFileHash pre-computed SHA-256 hex digest of {@code sourceFile}
     * @return the persisted {@link AppointmentImportFile} (check {@link AppointmentImportFile#getStatus()} to detect failures)
     */
    public AppointmentImportFile register( int nFormId, String strFileName, byte [ ] sourceFile, String strFileHash, Locale locale )
    {
        long lTotal = System.nanoTime( );

        long lT = System.nanoTime( );
        AppointmentExcelValidationResult validation = _reader.read( sourceFile, locale );
        AppLogService.info( "Appointment import [{}] — parsing/validation: {} ms ({} rows, {} errors)",
                strFileName, ms( lT ), validation.getValidRows( ).size( ), validation.getErrors( ).size( ) );

        List<AppointmentValidationError> colErrors = validateFormColumns( validation.getOtherColumnNames( ), nFormId, locale );
        if ( !colErrors.isEmpty( ) )
        {
            List<AppointmentValidationError> allErrors = new ArrayList<>( validation.getErrors( ) );
            allErrors.addAll( colErrors );
            validation = new AppointmentExcelValidationResult( validation.getValidRows( ), allErrors, validation.getOtherColumnNames( ) );
        }

        lT = System.nanoTime( );
        AppointmentImportFile file = new AppointmentImportFile( );
        file.setIdForm( nFormId );
        file.setImportFileName( strFileName );
        file.setCreationDate( LocalDateTime.now( ) );
        file.setFileHash( strFileHash );
        file.setValidationReport( AppointmentImportJsonService.writeErrors( validation.getErrors( ) ) );
        file.setStatus( validation.hasErrors( ) ? AppointmentImportStatus.VALIDATION_FAILED : AppointmentImportStatus.PENDING );
        file.setIdImportFile( AppointmentImportHome.createFile( file ) );
        AppLogService.info( "Appointment import [{}] — createFile (DB): {} ms", strFileName, ms( lT ) );

        if ( !validation.hasErrors( ) )
        {
            persistRows( file, validation.getValidRows( ) );
        }

        AppLogService.info( "Appointment import [{}] — TOTAL: {} ms", strFileName, ms( lTotal ) );
        return file;
    }

    /**
     * Groups rows by slot, creates the corresponding batches, then batch-inserts all appointment rows.
     *
     * @param file     the parent file record
     * @param listRows validated rows to persist
     */
    private void persistRows( AppointmentImportFile file, List<AppointmentImportRow> listRows )
    {
        long lT = System.nanoTime( );
        Map<String, Integer> mapBatchIds = new LinkedHashMap<>( );
        List<AppointmentImportAppointment> listAppointments = new ArrayList<>( );
        LocalDateTime dtNow = LocalDateTime.now( );
        for ( AppointmentImportRow row : listRows )
        {
            LocalDateTime dtStart = row.getAppointmentDate( ).atTime( row.getStartingTime( ) );
            LocalDateTime dtEnd = row.getAppointmentDate( ).atTime( row.getEndingTime( ) );
            String strKey = dtStart + "\u0000" + dtEnd;
            int nBatchId = mapBatchIds.computeIfAbsent( strKey, unused -> createBatch( file, dtStart, dtEnd ) );
            AppointmentImportAppointment appointment = new AppointmentImportAppointment( );
            appointment.setIdImportBatch( nBatchId );
            appointment.setSourceLineNumber( row.getLineNumber( ) );
            appointment.setGenericAttributesJson( AppointmentImportJsonService.writeMap( row.getGenericAttributes( ) ) );
            appointment.setFormFieldsJson( AppointmentImportJsonService.writeMap( row.getFormFields( ) ) );
            appointment.setStatus( AppointmentImportStatus.PENDING );
            appointment.setCreationDate( dtNow );
            listAppointments.add( appointment );
        }
        AppLogService.info( "Appointment import [{}] — createBatches ({} batches) + JSON serialization ({} rows): {} ms",
                file.getImportFileName( ), mapBatchIds.size( ), listAppointments.size( ), ms( lT ) );

        if ( !listAppointments.isEmpty( ) )
        {
            lT = System.nanoTime( );
            AppointmentImportHome.createAppointments( listAppointments );
            AppLogService.info( "Appointment import [{}] — createAppointments batch INSERT ({} rows): {} ms",
                    file.getImportFileName( ), listAppointments.size( ), ms( lT ) );
        }
    }

    /**
     * Returns the elapsed time in milliseconds since {@code lNanoStart}.
     *
     * @param lNanoStart a timestamp captured with {@link System#nanoTime()}
     * @return elapsed milliseconds
     */
    private static long ms( long lNanoStart )
    {
        return ( System.nanoTime( ) - lNanoStart ) / 1_000_000L;
    }

    /**
     * Inserts a new batch record for the given file and slot window and returns its generated id.
     *
     * @param file    the parent file record
     * @param dtStart the slot start datetime
     * @param dtEnd   the slot end datetime
     * @return the generated {@code id_import_batch}
     */
    private int createBatch( AppointmentImportFile file, LocalDateTime dtStart, LocalDateTime dtEnd )
    {
        AppointmentImportBatch batch = new AppointmentImportBatch( );
        batch.setIdImportFile( file.getIdImportFile( ) );
        batch.setImportFileName( file.getImportFileName( ) );
        batch.setIdForm( file.getIdForm( ) );
        batch.setStartingDateTime( dtStart );
        batch.setEndingDateTime( dtEnd );
        batch.setStatus( AppointmentImportStatus.PENDING );
        batch.setCreationDate( LocalDateTime.now( ) );
        return AppointmentImportHome.createBatch( batch );
    }

    /**
     * Compares the extra Excel columns against the generic-attribute entries of the form.
     * Returns one error per missing or unknown column.
     */
    private static List<AppointmentValidationError> validateFormColumns( Set<String> excelOtherCols, int nFormId, Locale locale )
    {
        EntryFilter filter = new EntryFilter( );
        filter.setIdResource( nFormId );
        filter.setResourceType( AppointmentFormDTO.RESOURCE_TYPE );
        filter.setEntryParentNull( EntryFilter.FILTER_TRUE );
        filter.setFieldDependNull( EntryFilter.FILTER_TRUE );
        filter.setIdIsComment( EntryFilter.FILTER_FALSE );
        filter.setIsOnlyDisplayInBack( EntryFilter.FILTER_FALSE );

        // Entry.getTitle() returns HTML-encoded values (e.g. "CASPE d&#39;affectation").
        // Unescape before comparing with the raw Excel column header.
        Set<String> formEntryTitles = EntryHome.getEntryList( filter ).stream( )
                .map( e -> StringEscapeUtils.unescapeHtml4( e.getTitle( ) ) )
                .collect( Collectors.toCollection( java.util.LinkedHashSet::new ) );

        // Columns defined in ImportColumn are handled separately and never appear in excelOtherCols.
        // Exclude them from the bidirectional check so that a form entry whose title matches
        // a standard column header is not flagged as missing from the Excel file.
        Set<String> normalizedStandardHeaders = java.util.Arrays.stream( ImportColumn.values( ) )
                .map( ImportColumn::getNormalizedHeader )
                .collect( Collectors.toSet( ) );

        List<AppointmentValidationError> errors = new ArrayList<>( );

        for ( String col : excelOtherCols )
        {
            if ( !formEntryTitles.contains( col ) )
            {
                errors.add( AppointmentValidationError.workbook( col,
                        I18nService.getLocalizedString( ERROR_COLUMN_NOT_IN_FORM, locale ) ) );
            }
        }
        for ( String entry : formEntryTitles )
        {
            if ( normalizedStandardHeaders.contains( ImportTextUtils.normalize( entry ) ) )
            {
                continue; // Handled as a standard column — not required in the "other columns" section
            }
            if ( !excelOtherCols.contains( entry ) )
            {
                errors.add( AppointmentValidationError.workbook( entry,
                        I18nService.getLocalizedString( ERROR_FORM_ENTRY_MISSING, locale ) ) );
            }
        }
        return errors;
    }
}
