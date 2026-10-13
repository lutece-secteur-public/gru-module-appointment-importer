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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentExcelValidationResult;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFingerprint;
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
    private static final String MESSAGE_FIELD_LINE = "module.appointment.importer.line";
    private static final String ERROR_ROW_ALREADY_IMPORTED = "module.appointment.importer.error.row.alreadyImported";
    private static final String MESSAGE_ROW_REPLACED = "module.appointment.importer.message.rowReplaced";
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern( "dd/MM/uuuu" );

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
        // Without a header there are no columns to compare with the form
        List<AppointmentValidationError> listColumnErrors = validation.isHeaderRead( )
                ? formEntries.validateColumns( validation.getOtherColumnNames( ), settings )
                : List.of( );
        listErrors.addAll( listColumnErrors );
        // The rows are checked against the form whatever the other errors, so that all of them are reported at once, but only once the
        // columns match: a missing column would put every row in error
        if ( listColumnErrors.isEmpty( ) )
        {
            for ( AppointmentImportRow row : validation.getValidRows( ) )
            {
                listErrors.addAll( formEntries.validateRow( row, settings ) );
            }
        }
        Map<String, List<AppointmentImportRow>> mapRowsBySlot = groupBySlot( validation.getValidRows( ) );
        Map<String, AppointmentImportRow> mapRowsByFingerprint = new LinkedHashMap<>( );
        // The duplicates of the file are not among the valid rows: each fingerprint is one row
        validation.getValidRows( ).forEach( row -> mapRowsByFingerprint.put( ImportRowFingerprint.of( row ), row ) );
        AppLogService.debug( "Appointment import [" + strFileName + "]: " + validation.getValidRows( ).size( ) + " rows, " + listErrors.size( ) + " errors" );

        try
        {
            return persist( nFormId, strFileName, strFileHash, strAdminAccessCode, listErrors, mapRowsBySlot, mapRowsByFingerprint, settings );
        }
        catch( RuntimeException e )
        {
            // Another file imported one of the lines, or retried a row it replaces, since they were checked: checked again, they are refused
            AppLogService.info( "Appointment import [" + strFileName + "]: lines imported meanwhile, checking them again (" + e.getMessage( ) + ")" );
            return persist( nFormId, strFileName, strFileHash, strAdminAccessCode, listErrors, mapRowsBySlot, mapRowsByFingerprint, settings );
        }
    }

    /**
     * Checks the lines against the lines already imported on the form, then persists the file with its rows, or as rejected, in one transaction.
     *
     * @param nFormId
     *            the form
     * @param strFileName
     *            the name of the uploaded file
     * @param strFileHash
     *            the digest of the file
     * @param strAdminAccessCode
     *            the access code of the administrator who uploads the file
     * @param listErrors
     *            the errors of the workbook
     * @param mapRowsBySlot
     *            the valid rows of each interval
     * @param mapRowsByFingerprint
     *            the valid rows, by fingerprint
     * @param settings
     *            what the validation depends on
     * @return the persisted file
     */
    private AppointmentImportFile persist( int nFormId, String strFileName, String strFileHash, String strAdminAccessCode,
            List<AppointmentValidationError> listErrors, Map<String, List<AppointmentImportRow>> mapRowsBySlot,
            Map<String, AppointmentImportRow> mapRowsByFingerprint, ImportValidationSettings settings )
    {
        Map<String, AppointmentImportFingerprint> mapReplaced = new HashMap<>( );
        List<AppointmentValidationError> listAllErrors = new ArrayList<>( listErrors );
        listAllErrors.addAll( checkImportedLines( nFormId, mapRowsByFingerprint, mapReplaced, settings ) );

        AppointmentImportFile file = new AppointmentImportFile( );
        file.setIdForm( nFormId );
        file.setImportFileName( strFileName );
        file.setAdminAccessCode( strAdminAccessCode );
        file.setCreationDate( LocalDateTime.now( ) );
        file.setFileHash( strFileHash );
        file.setValidationReport( AppointmentImportJsonService.writeErrors( listAllErrors ) );
        file.setStatus( listAllErrors.isEmpty( ) ? AppointmentImportStatus.PENDING : AppointmentImportStatus.VALIDATION_FAILED );
        // The daemon must not see a pending batch before all its rows are stored
        Plugin plugin = PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME );
        TransactionManager.beginTransaction( plugin );
        try
        {
            file.setIdImportFile( AppointmentImportHome.createFile( file ) );
            if ( listAllErrors.isEmpty( ) )
            {
                persistRows( file, mapRowsBySlot );
                claimLines( file, mapRowsByFingerprint, mapReplaced, settings );
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
     * Finds the lines of the file already imported on the form. A line whose row is in error without an appointment is not refused: its row is
     * replaced by the row of this file.
     *
     * @param nFormId
     *            the form
     * @param mapRowsByFingerprint
     *            the valid rows, by fingerprint
     * @param mapReplaced
     *            filled with the lines whose row is replaced, by fingerprint
     * @param settings
     *            what the validation depends on
     * @return one error per line already imported
     */
    private static List<AppointmentValidationError> checkImportedLines( int nFormId, Map<String, AppointmentImportRow> mapRowsByFingerprint,
            Map<String, AppointmentImportFingerprint> mapReplaced, ImportValidationSettings settings )
    {
        List<AppointmentValidationError> listErrors = new ArrayList<>( );
        if ( mapRowsByFingerprint.isEmpty( ) )
        {
            return listErrors;
        }
        for ( AppointmentImportFingerprint imported : AppointmentImportHome.findFingerprints( nFormId, mapRowsByFingerprint.keySet( ) ) )
        {
            if ( isReplaceable( imported ) )
            {
                mapReplaced.put( imported.getFingerprint( ), imported );
                continue;
            }
            String strDate = imported.getCreationDate( ) == null ? "" : imported.getCreationDate( ).format( FORMAT_DATE );
            listErrors.add( AppointmentValidationError.row( mapRowsByFingerprint.get( imported.getFingerprint( ) ).getLineNumber( ),
                    settings.message( MESSAGE_FIELD_LINE ), settings.message( ERROR_ROW_ALREADY_IMPORTED, imported.getImportFileName( ),
                            Integer.toString( imported.getSourceLineNumber( ) ), strDate ) ) );
        }
        listErrors.sort( ( e1, e2 ) -> Integer.compare( e1.getLineNumber( ), e2.getLineNumber( ) ) );
        return listErrors;
    }

    /**
     * A line can be imported again by another file only while its row is in error: a row created or waiting for the daemon would give two
     * appointments, and a row interrupted while its appointment was being saved may have it already.
     *
     * @param imported
     *            the line already imported
     * @return true if its row can be replaced
     */
    static boolean isReplaceable( AppointmentImportFingerprint imported )
    {
        return AppointmentImportStatus.ERROR.equals( imported.getRowStatus( ) ) && !AppointmentImportException.INTERRUPTED.equals( imported.getRowErrorCode( ) );
    }

    /**
     * Records the fingerprints of the rows of the file. A line imported before by a row in error takes its fingerprint, and the old row is replaced: it
     * is never retried, and its batch and file are completed if it was their last error.
     *
     * @param file
     *            the file, with its rows stored
     * @param mapRowsByFingerprint
     *            the rows of the file, by fingerprint
     * @param mapReplaced
     *            the lines whose row is replaced, by fingerprint
     * @param settings
     *            what the validation depends on
     * @throws IllegalStateException
     *             if a line was imported, or its old row retried, since it was checked
     */
    private static void claimLines( AppointmentImportFile file, Map<String, AppointmentImportRow> mapRowsByFingerprint,
            Map<String, AppointmentImportFingerprint> mapReplaced, ImportValidationSettings settings )
    {
        Map<Integer, Integer> mapRowIds = AppointmentImportHome.findAppointmentIdsByFile( file.getIdImportFile( ) );
        LocalDateTime dtNow = LocalDateTime.now( );
        List<AppointmentImportFingerprint> listNew = new ArrayList<>( );
        for ( Map.Entry<String, AppointmentImportRow> entry : mapRowsByFingerprint.entrySet( ) )
        {
            AppointmentImportRow row = entry.getValue( );
            AppointmentImportFingerprint fingerprint = new AppointmentImportFingerprint( );
            fingerprint.setIdForm( file.getIdForm( ) );
            fingerprint.setFingerprint( entry.getKey( ) );
            fingerprint.setIdImportFile( file.getIdImportFile( ) );
            fingerprint.setIdImportAppointment( mapRowIds.get( row.getLineNumber( ) ) );
            fingerprint.setSourceLineNumber( row.getLineNumber( ) );
            fingerprint.setAppointmentDate( row.getAppointmentDate( ) );
            fingerprint.setCreationDate( dtNow );
            AppointmentImportFingerprint replaced = mapReplaced.get( entry.getKey( ) );
            if ( replaced == null )
            {
                listNew.add( fingerprint );
                continue;
            }
            // The fingerprint first: of two files replacing the same row, only one gets it
            if ( !AppointmentImportHome.reassignFingerprint( fingerprint, replaced.getIdImportAppointment( ) )
                    || !AppointmentImportHome.replaceAppointment( replaced.getIdImportAppointment( ), AppointmentImportException.INTERRUPTED,
                            settings.message( MESSAGE_ROW_REPLACED, Integer.toString( row.getLineNumber( ) ), file.getImportFileName( ) ) ) )
            {
                throw new IllegalStateException( "line " + row.getLineNumber( ) + " imported or retried meanwhile" );
            }
            AppointmentImportBatch batch = AppointmentImportHome.findBatch( AppointmentImportHome.findAppointment( replaced.getIdImportAppointment( ) )
                    .getIdImportBatch( ) );
            AppointmentImportHome.completeWithoutErrors( batch );
        }
        // Fails on a line imported meanwhile by another file: its fingerprint is there already
        AppointmentImportHome.createFingerprints( listNew );
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
