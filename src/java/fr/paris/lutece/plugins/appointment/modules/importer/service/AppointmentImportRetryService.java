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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.util.sql.TransactionManager;

/**
 * Puts the rows in error back in the queue of the daemon, after the cause has been fixed (a slot opened, a value corrected).
 * <p>
 * A batch being processed is left to the daemon: its rows are retried once it is done. A row retried while the daemon takes its batch is not lost: the
 * daemon puts back in the queue a batch that still has pending rows when it is done with it. A row interrupted while its appointment was being
 * saved ({@code INTERRUPTED}) may have its appointment already: it is never retried with the others, only on its own, once checked.
 * </p>
 */
public final class AppointmentImportRetryService
{
    /** Error code of a row interrupted while its appointment was being saved */
    public static final String INTERRUPTED = AppointmentImportException.INTERRUPTED;

    private static final String ERROR_VALUE_REQUIRED = "module.appointment.importer.error.value.required";
    private static final String ERROR_VALUE_TOO_LONG = "module.appointment.importer.error.value.tooLong";
    private static final String ERROR_VALUE_EMAIL = "module.appointment.importer.error.value.email";
    private static final String ERROR_VALUE_PHONE = "module.appointment.importer.error.value.phone";
    private static final String ERROR_VALUE_DATE = "module.appointment.importer.error.value.date";
    private static final String ERROR_VALUE_BIRTH_DATE_FUTURE = "module.appointment.importer.error.value.birthDateFuture";
    private static final DateTimeFormatter FORMAT_DATE_INPUT = DateTimeFormatter.ofPattern( "d/M/uuuu" ).withResolverStyle( ResolverStyle.STRICT );
    private static final DateTimeFormatter FORMAT_DATE_OUTPUT = DateTimeFormatter.ofPattern( "dd/MM/uuuu" );
    private static final List<ImportColumn> NAME_COLUMNS = Arrays.asList( ImportColumn.LAST_NAME, ImportColumn.FIRST_NAME );

    private AppointmentImportRetryService( )
    {
    }

    /**
     * Puts the rows in error of a batch back in the queue, except the interrupted ones.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @return true if the batch was put back in the queue, false if it is being processed or archived
     */
    public static boolean retryBatch( int nBatchId )
    {
        AppointmentImportBatch batch = AppointmentImportHome.findBatch( nBatchId );
        if ( batch == null || !isRetryable( batch ) )
        {
            return false;
        }
        Plugin plugin = PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME );
        TransactionManager.beginTransaction( plugin );
        try
        {
            // The rows first, then the batch: a completed batch is never taken by the daemon before it is pending again
            AppointmentImportHome.requeueErrorRows( nBatchId, AppointmentImportException.INTERRUPTED );
            boolean bRequeued = AppointmentImportHome.requeueBatch( nBatchId );
            if ( bRequeued )
            {
                AppointmentImportHome.updateFileStatus( batch.getIdImportFile( ), AppointmentImportStatus.PENDING );
            }
            TransactionManager.commitTransaction( plugin );
            return bRequeued;
        }
        catch( RuntimeException e )
        {
            TransactionManager.rollBack( plugin, e );
            throw e;
        }
    }

    /**
     * Puts the rows in error of every completed batch of a file back in the queue, except the interrupted ones.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @return the number of batches put back in the queue
     */
    public static int retryFile( int nFileId )
    {
        int nBatches = 0;
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByFile( nFileId ) )
        {
            if ( AppointmentImportStatus.COMPLETED_WITH_ERRORS.equals( batch.getStatus( ) ) && retryBatch( batch.getIdImportBatch( ) ) )
            {
                nBatches++;
            }
        }
        return nBatches;
    }

    /**
     * Puts one row in error back in the queue, whatever its error.
     *
     * @param nRowId
     *            the {@code id_import_appointment}
     * @return true if the row was put back in the queue, false if it is not in error or its batch is being processed or archived
     */
    public static boolean retryRow( int nRowId )
    {
        AppointmentImportAppointment row = AppointmentImportHome.findAppointment( nRowId );
        if ( row == null || !AppointmentImportStatus.ERROR.equals( row.getStatus( ) ) )
        {
            return false;
        }
        AppointmentImportBatch batch = AppointmentImportHome.findBatch( row.getIdImportBatch( ) );
        if ( batch == null || !isRetryable( batch ) )
        {
            return false;
        }
        Plugin plugin = PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME );
        TransactionManager.beginTransaction( plugin );
        try
        {
            AppointmentImportHome.requeueRow( nRowId );
            boolean bRequeued = AppointmentImportHome.requeueBatch( batch.getIdImportBatch( ) );
            if ( bRequeued )
            {
                AppointmentImportHome.updateFileStatus( batch.getIdImportFile( ), AppointmentImportStatus.PENDING );
            }
            TransactionManager.commitTransaction( plugin );
            return bRequeued;
        }
        catch( RuntimeException e )
        {
            TransactionManager.rollBack( plugin, e );
            throw e;
        }
    }

    /**
     * Corrects the values of a row in error, checked as on upload, then puts it back in the queue. The slot of a row cannot be changed: it is the slot of
     * its batch.
     *
     * @param nRowId
     *            the {@code id_import_appointment}
     * @param mapGenericAttributes
     *            the new values of the standard columns, by attribute key
     * @param mapFormFields
     *            the new values of the form fields, by column
     * @param locale
     *            the locale of the messages
     * @return the errors of the new values; empty if the row was corrected and put back in the queue
     */
    public static List<AppointmentValidationError> correctRow( int nRowId, Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields,
            Locale locale )
    {
        AppointmentImportAppointment row = AppointmentImportHome.findAppointment( nRowId );
        AppointmentImportBatch batch = AppointmentImportHome.findBatch( row.getIdImportBatch( ) );
        ImportValidationSettings settings = ImportValidationSettings.fromLutece( locale );
        return correctRow( nRowId, mapGenericAttributes, mapFormFields, settings, AppointmentFormEntries.load( batch.getIdForm( ), settings.getColumns( ) ) );
    }

    /**
     * Corrects the values of a row in error with the given settings and form fields.
     *
     * @param nRowId
     *            the {@code id_import_appointment}
     * @param mapGenericAttributes
     *            the new values of the standard columns, by attribute key
     * @param mapFormFields
     *            the new values of the form fields, by column
     * @param settings
     *            what the validation depends on
     * @param formEntries
     *            the fields of the form of the row
     * @return the errors of the new values; empty if the row was corrected and put back in the queue
     */
    static List<AppointmentValidationError> correctRow( int nRowId, Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields,
            ImportValidationSettings settings, AppointmentFormEntries formEntries )
    {
        AppointmentImportAppointment row = AppointmentImportHome.findAppointment( nRowId );
        Map<String, String> mapGeneric = new LinkedHashMap<>( mapGenericAttributes );
        List<AppointmentValidationError> listErrors = validate( row.getSourceLineNumber( ), mapGeneric, mapFormFields, settings, formEntries );
        if ( listErrors.isEmpty( ) )
        {
            AppointmentImportHome.updateAppointmentData( nRowId, AppointmentImportJsonService.writeMap( mapGeneric ),
                    AppointmentImportJsonService.writeMap( mapFormFields ) );
            retryRow( nRowId );
        }
        return listErrors;
    }

    /**
     * Checks the corrected values of a row with the rules of the upload, and normalizes them as the upload does.
     *
     * @param nLine
     *            the line of the row in the workbook
     * @param mapGeneric
     *            the values of the standard columns, normalized in place
     * @param mapFormFields
     *            the values of the form fields
     * @param settings
     *            what the validation depends on
     * @param formEntries
     *            the fields of the form
     * @return the errors
     */
    static List<AppointmentValidationError> validate( int nLine, Map<String, String> mapGeneric, Map<String, String> mapFormFields,
            ImportValidationSettings settings, AppointmentFormEntries formEntries )
    {
        ImportColumns columns = settings.getColumns( );
        List<AppointmentValidationError> listErrors = new ArrayList<>( );
        for ( ImportColumn column : ImportColumn.values( ) )
        {
            if ( column.getAttributeKey( ) == null )
            {
                continue;
            }
            String strValue = mapGeneric.getOrDefault( column.getAttributeKey( ), "" ).trim( );
            mapGeneric.put( column.getAttributeKey( ), strValue );
            if ( strValue.isEmpty( ) )
            {
                if ( columns.isMandatory( column ) )
                {
                    listErrors.add( AppointmentValidationError.row( nLine, columns.getHeader( column ), settings.message( ERROR_VALUE_REQUIRED ) ) );
                }
                continue;
            }
            String strError = checkValue( column, strValue, mapGeneric, settings );
            if ( strError != null )
            {
                listErrors.add( AppointmentValidationError.row( nLine, columns.getHeader( column ), strError ) );
            }
        }
        if ( listErrors.isEmpty( ) )
        {
            listErrors.addAll( formEntries.validateRow( new AppointmentImportRow( nLine, mapGeneric, mapFormFields, null, null, null ), settings ) );
        }
        return listErrors;
    }

    /**
     * Checks the value of a standard column, normalizing the phone number and the birth date in the map.
     *
     * @return the error message, or null if the value is valid
     */
    private static String checkValue( ImportColumn column, String strValue, Map<String, String> mapGeneric, ImportValidationSettings settings )
    {
        if ( NAME_COLUMNS.contains( column ) && strValue.length( ) > settings.getMaxNameLength( ) )
        {
            return settings.message( ERROR_VALUE_TOO_LONG, Integer.toString( settings.getMaxNameLength( ) ) );
        }
        if ( column == ImportColumn.EMAIL && !settings.isValidEmail( strValue ) )
        {
            return settings.message( ERROR_VALUE_EMAIL );
        }
        if ( column == ImportColumn.PHONE_NUMBER )
        {
            String strPhoneNumber = ImportTextUtils.normalizePhoneNumber( strValue );
            mapGeneric.put( column.getAttributeKey( ), strPhoneNumber );
            return settings.isValidPhoneNumber( strPhoneNumber ) ? null : settings.message( ERROR_VALUE_PHONE );
        }
        if ( column == ImportColumn.BIRTH_DATE )
        {
            try
            {
                LocalDate birthDate = LocalDate.parse( strValue, FORMAT_DATE_INPUT );
                mapGeneric.put( column.getAttributeKey( ), birthDate.format( FORMAT_DATE_OUTPUT ) );
                return birthDate.isAfter( settings.getNow( ).toLocalDate( ) ) ? settings.message( ERROR_VALUE_BIRTH_DATE_FUTURE ) : null;
            }
            catch( DateTimeParseException e )
            {
                return settings.message( ERROR_VALUE_DATE );
            }
        }
        return null;
    }

    private static boolean isRetryable( AppointmentImportBatch batch )
    {
        return AppointmentImportStatus.COMPLETED.equals( batch.getStatus( ) ) || AppointmentImportStatus.COMPLETED_WITH_ERRORS.equals( batch.getStatus( ) )
                || AppointmentImportStatus.PENDING.equals( batch.getStatus( ) );
    }
}
