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

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentExcelValidationResult;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.util.sql.TransactionManager;

/** Validates an upload then persists its immutable source data for daemon processing. */
public final class AppointmentImportService
{
    private final AppointmentExcelReader _reader = new AppointmentExcelReader( );

    /**
     * Computes the SHA-256 hex digest of the given bytes.
     * Used to detect duplicate uploads before registering a new file.
     *
     * @param bytes the content
     * @return the digest
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
     * @param nFormId            the form
     * @param strFileName        the name of the uploaded file
     * @param sourceFile         the content of the file
     * @param strFileHash        pre-computed SHA-256 hex digest of {@code sourceFile}
     * @param strAdminAccessCode the access code of the administrator who uploads the file
     * @param locale             the locale of the messages
     * @return the persisted {@link AppointmentImportFile} (check {@link AppointmentImportFile#getStatus()} to detect failures)
     */
    public AppointmentImportFile register( int nFormId, String strFileName, byte [ ] sourceFile, String strFileHash, String strAdminAccessCode,
            Locale locale )
    {
        ImportValidationSettings settings = ImportValidationSettings.fromLutece( locale );
        AppointmentExcelValidationResult validation = _reader.read( sourceFile, settings );
        List<AppointmentValidationError> listErrors = new ArrayList<>( validation.getErrors( ) );
        AppointmentFormEntries formEntries = AppointmentFormEntries.load( nFormId, settings.getColumns( ) );
        listErrors.addAll( formEntries.validateColumns( validation.getOtherColumnNames( ), settings ) );
        if ( listErrors.isEmpty( ) )
        {
            for ( AppointmentImportRow row : validation.getValidRows( ) )
            {
                listErrors.addAll( formEntries.validateRow( row, settings ) );
            }
        }
        Map<String, List<AppointmentImportRow>> mapRowsBySlot = groupBySlot( validation.getValidRows( ) );
        AppLogService.debug( "Appointment import [" + strFileName + "]: " + validation.getValidRows( ).size( ) + " rows, " + listErrors.size( ) + " errors" );

        AppointmentImportFile file = new AppointmentImportFile( );
        file.setIdForm( nFormId );
        file.setImportFileName( strFileName );
        file.setAdminAccessCode( strAdminAccessCode );
        file.setCreationDate( LocalDateTime.now( ) );
        file.setFileHash( strFileHash );
        file.setValidationReport( AppointmentImportJsonService.writeErrors( listErrors ) );
        file.setStatus( listErrors.isEmpty( ) ? AppointmentImportStatus.PENDING : AppointmentImportStatus.VALIDATION_FAILED );
        // The daemon must not see a pending batch before all its rows are stored
        Plugin plugin = PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME );
        TransactionManager.beginTransaction( plugin );
        try
        {
            file.setIdImportFile( AppointmentImportHome.createFile( file ) );
            if ( listErrors.isEmpty( ) )
            {
                persistRows( file, mapRowsBySlot );
            }
            TransactionManager.commitTransaction( plugin );
        }
        catch( RuntimeException e )
        {
            TransactionManager.rollBack( plugin, e );
            throw e;
        }
        return file;
    }

    /**
     * Groups the rows by slot interval, in the order of the workbook.
     *
     * @param listRows the validated rows
     * @return the rows of each interval
     */
    private static Map<String, List<AppointmentImportRow>> groupBySlot( List<AppointmentImportRow> listRows )
    {
        Map<String, List<AppointmentImportRow>> mapRowsBySlot = new LinkedHashMap<>( );
        for ( AppointmentImportRow row : listRows )
        {
            String strKey = row.getAppointmentDate( ).atTime( row.getStartingTime( ) ) + "/" + row.getAppointmentDate( ).atTime( row.getEndingTime( ) );
            mapRowsBySlot.computeIfAbsent( strKey, unused -> new ArrayList<>( ) ).add( row );
        }
        return mapRowsBySlot;
    }

    /**
     * Creates one batch per slot interval, then batch-inserts all appointment rows.
     *
     * @param file          the parent file record
     * @param mapRowsBySlot the rows of each interval
     */
    private void persistRows( AppointmentImportFile file, Map<String, List<AppointmentImportRow>> mapRowsBySlot )
    {
        List<AppointmentImportAppointment> listAppointments = new ArrayList<>( );
        LocalDateTime dtNow = LocalDateTime.now( );
        for ( List<AppointmentImportRow> listRows : mapRowsBySlot.values( ) )
        {
            AppointmentImportRow first = listRows.get( 0 );
            int nBatchId = createBatch( file, first.getAppointmentDate( ).atTime( first.getStartingTime( ) ),
                    first.getAppointmentDate( ).atTime( first.getEndingTime( ) ) );
            for ( AppointmentImportRow row : listRows )
            {
                AppointmentImportAppointment appointment = new AppointmentImportAppointment( );
                appointment.setIdImportBatch( nBatchId );
                appointment.setSourceLineNumber( row.getLineNumber( ) );
                appointment.setGenericAttributesJson( AppointmentImportJsonService.writeMap( row.getGenericAttributes( ) ) );
                appointment.setFormFieldsJson( AppointmentImportJsonService.writeMap( row.getFormFields( ) ) );
                appointment.setStatus( AppointmentImportStatus.PENDING );
                appointment.setCreationDate( dtNow );
                listAppointments.add( appointment );
            }
        }
        if ( !listAppointments.isEmpty( ) )
        {
            AppointmentImportHome.createAppointments( listAppointments );
        }
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
}
