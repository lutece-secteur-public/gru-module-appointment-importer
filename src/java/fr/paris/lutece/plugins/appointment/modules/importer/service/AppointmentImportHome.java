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

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.util.sql.DAOUtil;

/** Data access for uploaded import files, slot batches and source rows. */
public final class AppointmentImportHome
{
    // -- SQL column list fragments ------------------------------------------

    private static final String SQL_COLS_FILE =
            "id_import_file, import_file_name, id_form, status, file_hash, validation_report, creation_date, last_exec_date"
            + " FROM appointment_import_file";

    private static final String SQL_COLS_BATCH =
            "id_import_batch, id_import_file, import_file_name, id_form, starting_datetime, ending_datetime,"
            + " status, creation_date, last_exec_date FROM appointment_import_batch";

    private static final String SQL_COLS_APPOINTMENT =
            "id_import_appointment, id_import_batch, source_line_number, generic_attributes_json, form_fields_json,"
            + " status, error_code, error_message, id_appointment, creation_date, last_exec_date"
            + " FROM appointment_import_appointment";

    // -- SQL query constants ------------------------------------------------

    private static final String SQL_INSERT_FILE =
            "INSERT INTO appointment_import_file"
            + " (import_file_name, id_form, status, file_hash, validation_report, creation_date, last_exec_date)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_EXISTS_DUPLICATE_FILE =
            "SELECT id_import_file FROM appointment_import_file"
            + " WHERE file_hash=? AND id_form=? AND status != 'VALIDATION_FAILED' LIMIT 1";

    private static final String SQL_INSERT_BATCH =
            "INSERT INTO appointment_import_batch"
            + " (id_import_file, import_file_name, id_form, starting_datetime, ending_datetime, status, creation_date, last_exec_date)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_INSERT_APPOINTMENT =
            "INSERT INTO appointment_import_appointment"
            + " (id_import_batch, source_line_number, generic_attributes_json, form_fields_json, status, error_code, error_message, id_appointment, creation_date, last_exec_date)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_ALL_FILES =
            "SELECT " + SQL_COLS_FILE + " ORDER BY id_import_file DESC";

    private static final String SQL_SELECT_FILE_BY_ID =
            "SELECT " + SQL_COLS_FILE + " WHERE id_import_file = ?";

    private static final String SQL_SELECT_ALL_BATCHES =
            "SELECT " + SQL_COLS_BATCH + " ORDER BY id_import_batch DESC";

    private static final String SQL_SELECT_BATCHES_BY_FILE =
            "SELECT " + SQL_COLS_BATCH + " WHERE id_import_file=? ORDER BY starting_datetime";

    private static final String SQL_SELECT_PENDING_BATCHES =
            "SELECT " + SQL_COLS_BATCH + " WHERE status='PENDING' ORDER BY id_import_batch";

    private static final String SQL_SELECT_BATCH_BY_ID =
            "SELECT " + SQL_COLS_BATCH + " WHERE id_import_batch=?";

    private static final String SQL_SELECT_APPOINTMENTS_BY_BATCH_BASE =
            "SELECT " + SQL_COLS_APPOINTMENT + " WHERE id_import_batch=?";

    private static final String SQL_SELECT_APPOINTMENTS_BY_BATCH_STATUS_SUFFIX =
            " AND status=?";

    private static final String SQL_SELECT_APPOINTMENTS_ORDER =
            " ORDER BY source_line_number";

    private static final String SQL_COUNT_APPOINTMENTS_BY_BATCH =
            "SELECT COUNT(*) FROM appointment_import_appointment WHERE id_import_batch=?";

    private static final String SQL_COUNT_PROCESSED_APPOINTMENTS_SUFFIX =
            " AND status IN ('CREATED', 'ERROR')";

    private static final String SQL_UPDATE_BATCH_STATUS =
            "UPDATE appointment_import_batch SET status=?, last_exec_date=? WHERE id_import_batch=?";

    private static final String SQL_UPDATE_FILE_STATUS =
            "UPDATE appointment_import_file SET status=?, last_exec_date=? WHERE id_import_file=?";

    private static final String SQL_UPDATE_APPOINTMENT =
            "UPDATE appointment_import_appointment"
            + " SET status=?, error_code=?, error_message=?, id_appointment=?, last_exec_date=?"
            + " WHERE id_import_appointment=?";

    private static final String SQL_EXISTS_BATCH_ERROR =
            "SELECT id_import_appointment FROM appointment_import_appointment"
            + " WHERE id_import_batch=? AND status='ERROR'";

    private static final String SQL_EXISTS_FILE_ERROR =
            "SELECT a.id_import_appointment FROM appointment_import_appointment a"
            + " JOIN appointment_import_batch b ON b.id_import_batch=a.id_import_batch"
            + " WHERE b.id_import_file=? AND a.status='ERROR'";

    // -- SQL purge constants (Point 5) --------------------------------------

    private static final String SQL_SELECT_BATCH_IDS_TO_PURGE =
            "SELECT id_import_batch FROM appointment_import_batch"
            + " WHERE last_exec_date < ? AND status IN ('" + AppointmentImportStatus.COMPLETED + "','" + AppointmentImportStatus.COMPLETED_WITH_ERRORS + "')";

    private static final String SQL_DELETE_APPOINTMENTS_BY_BATCH =
            "DELETE FROM appointment_import_appointment WHERE id_import_batch=?";

    private static final String SQL_ARCHIVE_BATCH =
            "UPDATE appointment_import_batch SET status='" + AppointmentImportStatus.ARCHIVED + "', last_exec_date=? WHERE id_import_batch=?";

    private static final String SQL_SELECT_FILE_ALL_BATCHES_ARCHIVED =
            "SELECT COUNT(*) FROM appointment_import_batch"
            + " WHERE id_import_file=? AND status != '" + AppointmentImportStatus.ARCHIVED + "'";

    private static final String SQL_ARCHIVE_FILE =
            "UPDATE appointment_import_file SET status='" + AppointmentImportStatus.ARCHIVED + "', last_exec_date=? WHERE id_import_file=?";

    // -- Infrastructure ----------------------------------------------------

    private static final Plugin PLUGIN = PluginService.getPlugin( "appointment-importer" );

    private AppointmentImportHome( )
    {
    }

    // -- Write operations --------------------------------------------------

    /**
     * Inserts a new import file record and returns its generated primary key.
     *
     * @param file the file to persist
     * @return the generated {@code id_import_file}
     */
    public static int createFile( AppointmentImportFile file )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_INSERT_FILE, Statement.RETURN_GENERATED_KEYS, PLUGIN ) )
        {
            int nIndex = 1;
            dao.setString( nIndex++, file.getImportFileName( ) );
            dao.setInt( nIndex++, file.getIdForm( ) );
            dao.setString( nIndex++, file.getStatus( ) );
            dao.setString( nIndex++, file.getFileHash( ) );
            dao.setString( nIndex++, file.getValidationReport( ) );
            dao.setTimestamp( nIndex++, timestamp( file.getCreationDate( ) ) );
            setTimestampOrNull( dao, nIndex, file.getLastExecDate( ) );
            dao.executeUpdate( );
            if ( dao.nextGeneratedKey( ) )
            {
                return dao.getGeneratedKeyInt( 1 );
            }
            throw new IllegalStateException( "Missing generated key for import file" );
        }
    }

    /**
     * Inserts a new import batch.
     *
     * @param batch the batch to persist
     * @return the generated {@code id_import_batch}
     */
    public static int createBatch( AppointmentImportBatch batch )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_INSERT_BATCH, Statement.RETURN_GENERATED_KEYS, PLUGIN ) )
        {
            int nIndex = 1;
            dao.setInt( nIndex++, batch.getIdImportFile( ) );
            dao.setString( nIndex++, batch.getImportFileName( ) );
            dao.setInt( nIndex++, batch.getIdForm( ) );
            dao.setTimestamp( nIndex++, timestamp( batch.getStartingDateTime( ) ) );
            dao.setTimestamp( nIndex++, timestamp( batch.getEndingDateTime( ) ) );
            dao.setString( nIndex++, batch.getStatus( ) );
            dao.setTimestamp( nIndex++, timestamp( batch.getCreationDate( ) ) );
            setTimestampOrNull( dao, nIndex, batch.getLastExecDate( ) );
            dao.executeUpdate( );
            if ( dao.nextGeneratedKey( ) )
            {
                return dao.getGeneratedKeyInt( 1 );
            }
            throw new IllegalStateException( "Missing generated key for import batch" );
        }
    }

    /**
     * Insert a list of appointment.
     *
     * @param listAppointments the rows to insert
     */
    public static void createAppointments( List<AppointmentImportAppointment> listAppointments )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_INSERT_APPOINTMENT, PLUGIN ) )
        {
            for ( AppointmentImportAppointment appointment : listAppointments )
            {
                int nIndex = 1;
                dao.setInt( nIndex++, appointment.getIdImportBatch( ) );
                dao.setInt( nIndex++, appointment.getSourceLineNumber( ) );
                dao.setString( nIndex++, appointment.getGenericAttributesJson( ) );
                dao.setString( nIndex++, appointment.getFormFieldsJson( ) );
                dao.setString( nIndex++, appointment.getStatus( ) );
                dao.setString( nIndex++, appointment.getErrorCode( ) );
                dao.setString( nIndex++, appointment.getErrorMessage( ) );
                if ( appointment.getIdAppointment( ) == null )
                {
                    dao.setIntNull( nIndex++ );
                }
                else
                {
                    dao.setInt( nIndex++, appointment.getIdAppointment( ) );
                }
                dao.setTimestamp( nIndex++, timestamp( appointment.getCreationDate( ) ) );
                setTimestampOrNull( dao, nIndex, appointment.getLastExecDate( ) );
                dao.addBatch( );
            }
            dao.executeBatch( );
        }
    }

    // -- File read operations ----------------------------------------------

    /** Returns all import file records ordered by id descending. */
    public static List<AppointmentImportFile> findFiles( )
    {
        return findFiles( SQL_SELECT_ALL_FILES );
    }

    /**
     * Returns the import file with the given id, or {@code null} if not found.
     *
     * @param nId the {@code id_import_file}
     */
    public static AppointmentImportFile findFile( int nId )
    {
        List<AppointmentImportFile> listFiles = findFiles( SQL_SELECT_FILE_BY_ID, nId );
        return listFiles.isEmpty( ) ? null : listFiles.get( 0 );
    }

    /**
     * Runs a SELECT query and returns the mapped results.
     *
     * @param strSql the SELECT statement
     * @param params int bind values, in placeholder order
     * @return the matching files, or an empty list
     */
    private static List<AppointmentImportFile> findFiles( String strSql, Object... params )
    {
        List<AppointmentImportFile> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( strSql, PLUGIN ) )
        {
            bindInts( dao, params );
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( mapFile( dao ) );
            }
        }
        return listResult;
    }

    /**
     * Finds files matching the given authorized form IDs and optional filters, using SQL-level filtering.
     *
     * @param listAuthorizedFormIds non-null, non-empty list of authorized form IDs
     * @param strFileName           optional; exact match on import_file_name
     * @param strStatus             optional; exact match on status
     * @return filtered list ordered by id_import_file DESC
     */
    public static List<AppointmentImportFile> findFiles( List<Integer> listAuthorizedFormIds, String strFileName, String strStatus )
    {
        if ( listAuthorizedFormIds == null || listAuthorizedFormIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        StringBuilder sbSql = new StringBuilder( "SELECT " ).append( SQL_COLS_FILE ).append( " WHERE id_form IN (" );
        for ( int nIndex = 0; nIndex < listAuthorizedFormIds.size( ); nIndex++ )
        {
            if ( nIndex > 0 )
            {
                sbSql.append( ',' );
            }
            sbSql.append( '?' );
        }
        sbSql.append( ')' );
        if ( StringUtils.isNotBlank( strFileName ) )
        {
            sbSql.append( " AND import_file_name=?" );
        }
        if ( StringUtils.isNotBlank( strStatus ) )
        {
            sbSql.append( " AND status=?" );
        }
        sbSql.append( " ORDER BY id_import_file DESC" );
        List<AppointmentImportFile> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( sbSql.toString( ), PLUGIN ) )
        {
            int nIndex = 1;
            for ( int nFormId : listAuthorizedFormIds )
            {
                dao.setInt( nIndex++, nFormId );
            }
            if ( StringUtils.isNotBlank( strFileName ) )
            {
                dao.setString( nIndex++, strFileName );
            }
            if ( StringUtils.isNotBlank( strStatus ) )
            {
                dao.setString( nIndex, strStatus );
            }
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( mapFile( dao ) );
            }
        }
        return listResult;
    }

    // -- Batch read operations ---------------------------------------------

    /** Returns all import batch records ordered by id descending. */
    public static List<AppointmentImportBatch> findBatches( )
    {
        return findBatches( SQL_SELECT_ALL_BATCHES );
    }

    /**
     * Returns all batches belonging to the given file, ordered by starting datetime ascending.
     *
     * @param nFileId the {@code id_import_file}
     */
    public static List<AppointmentImportBatch> findBatchesByFile( int nFileId )
    {
        return findBatches( SQL_SELECT_BATCHES_BY_FILE, nFileId );
    }

    /** Returns all batches whose status is {@code PENDING}, ordered by id ascending (FIFO processing order). */
    public static List<AppointmentImportBatch> findPendingBatches( )
    {
        return findBatches( SQL_SELECT_PENDING_BATCHES );
    }

    /**
     * Returns the batch with the given id, or {@code null} if not found.
     *
     * @param nId the {@code id_import_batch}
     */
    public static AppointmentImportBatch findBatch( int nId )
    {
        List<AppointmentImportBatch> listBatches = findBatches( SQL_SELECT_BATCH_BY_ID, nId );
        return listBatches.isEmpty( ) ? null : listBatches.get( 0 );
    }

    /**
     * Runs a SELECT query and returns the mapped results. All public findBatches overloads delegate here.
     *
     * @param strSql the SELECT statement
     * @param params int bind values, in placeholder order
     * @return the matching batches, or an empty list
     */
    private static List<AppointmentImportBatch> findBatches( String strSql, Object... params )
    {
        List<AppointmentImportBatch> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( strSql, PLUGIN ) )
        {
            bindInts( dao, params );
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( mapBatch( dao ) );
            }
        }
        return listResult;
    }

    /**
     * Finds batches matching the given authorized form IDs and optional filters, using SQL-level filtering.
     *
     * @param listAuthorizedFormIds non-null, non-empty list of authorized form IDs
     * @param strFormId             optional; exact match on id_form
     * @param strStatus             optional; exact match on status
     * @param strFileName           optional; exact match on import_file_name
     * @param strDate               optional; matches DATE(starting_datetime) as a string (yyyy-MM-dd)
     * @return filtered list ordered by id_import_batch DESC
     */
    public static List<AppointmentImportBatch> findBatches( List<Integer> listAuthorizedFormIds, String strFormId, String strStatus, String strFileName, String strDate )
    {
        if ( listAuthorizedFormIds == null || listAuthorizedFormIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        StringBuilder sbSql = new StringBuilder( "SELECT " ).append( SQL_COLS_BATCH ).append( " WHERE id_form IN (" );
        for ( int nIndex = 0; nIndex < listAuthorizedFormIds.size( ); nIndex++ )
        {
            if ( nIndex > 0 )
            {
                sbSql.append( ',' );
            }
            sbSql.append( '?' );
        }
        sbSql.append( ')' );
        if ( StringUtils.isNotBlank( strFormId ) )
        {
            sbSql.append( " AND id_form=?" );
        }
        if ( StringUtils.isNotBlank( strStatus ) )
        {
            sbSql.append( " AND status=?" );
        }
        if ( StringUtils.isNotBlank( strFileName ) )
        {
            sbSql.append( " AND import_file_name=?" );
        }
        if ( StringUtils.isNotBlank( strDate ) )
        {
            sbSql.append( " AND DATE(starting_datetime)=?" );
        }
        sbSql.append( " ORDER BY id_import_batch DESC" );
        List<AppointmentImportBatch> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( sbSql.toString( ), PLUGIN ) )
        {
            int nIndex = 1;
            for ( int nFormId : listAuthorizedFormIds )
            {
                dao.setInt( nIndex++, nFormId );
            }
            if ( StringUtils.isNotBlank( strFormId ) )
            {
                dao.setInt( nIndex++, Integer.parseInt( strFormId ) );
            }
            if ( StringUtils.isNotBlank( strStatus ) )
            {
                dao.setString( nIndex++, strStatus );
            }
            if ( StringUtils.isNotBlank( strFileName ) )
            {
                dao.setString( nIndex++, strFileName );
            }
            if ( StringUtils.isNotBlank( strDate ) )
            {
                dao.setString( nIndex, strDate );
            }
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( mapBatch( dao ) );
            }
        }
        return listResult;
    }

    // -- Appointment read operations ---------------------------------------

    /**
     * Returns the appointment rows belonging to the given batch.
     *
     * @param nBatchId  the {@code id_import_batch}
     * @param strStatus optional status filter ({@link AppointmentImportStatus} constant); pass {@code null} to return all rows
     * @return rows ordered by source line number ascending
     */
    public static List<AppointmentImportAppointment> findAppointmentsByBatch( int nBatchId, String strStatus )
    {
        String strSql = SQL_SELECT_APPOINTMENTS_BY_BATCH_BASE
                + ( strStatus == null ? "" : SQL_SELECT_APPOINTMENTS_BY_BATCH_STATUS_SUFFIX )
                + SQL_SELECT_APPOINTMENTS_ORDER;
        List<AppointmentImportAppointment> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( strSql, PLUGIN ) )
        {
            dao.setInt( 1, nBatchId );
            if ( strStatus != null )
            {
                dao.setString( 2, strStatus );
            }
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( appointment( dao ) );
            }
        }
        return listResult;
    }

    /**
     * Returns the total number of appointment rows in the given batch.
     *
     * @param nBatchId the {@code id_import_batch}
     */
    public static int countAppointmentsByBatch( int nBatchId )
    {
        return countAppointmentsByBatch( nBatchId, false );
    }

    /**
     * Returns the number of appointment rows in the given batch that have already been processed
     * (status {@code CREATED} or {@code ERROR}).
     *
     * @param nBatchId the {@code id_import_batch}
     */
    public static int countProcessedAppointmentsByBatch( int nBatchId )
    {
        return countAppointmentsByBatch( nBatchId, true );
    }

    /**
     * Counts appointment rows in the given batch, optionally restricting to processed rows only.
     *
     * @param nBatchId       the {@code id_import_batch}
     * @param bProcessedOnly if true, only counts rows with status CREATED or ERROR
     * @return the row count
     */
    private static int countAppointmentsByBatch( int nBatchId, boolean bProcessedOnly )
    {
        String strSql = SQL_COUNT_APPOINTMENTS_BY_BATCH
                + ( bProcessedOnly ? SQL_COUNT_PROCESSED_APPOINTMENTS_SUFFIX : "" );
        try ( DAOUtil dao = new DAOUtil( strSql, PLUGIN ) )
        {
            dao.setInt( 1, nBatchId );
            dao.executeQuery( );
            return dao.next( ) ? dao.getInt( 1 ) : 0;
        }
    }

    // -- Status update operations ------------------------------------------

    /**
     * Updates the status and {@code last_exec_date} of a batch record.
     *
     * @param nId       the {@code id_import_batch}
     * @param strStatus the new status ({@link AppointmentImportStatus} constant)
     */
    public static void updateBatchStatus( int nId, String strStatus )
    {
        updateStatus( SQL_UPDATE_BATCH_STATUS, nId, strStatus );
    }

    /**
     * Updates the status and {@code last_exec_date} of a file record.
     *
     * @param nId       the {@code id_import_file}
     * @param strStatus the new status ({@link AppointmentImportStatus} constant)
     */
    public static void updateFileStatus( int nId, String strStatus )
    {
        updateStatus( SQL_UPDATE_FILE_STATUS, nId, strStatus );
    }

    /**
     * Updates the status and last_exec_date of a file or batch record.
     *
     * @param strSql    the UPDATE statement (file or batch)
     * @param nId       the record id
     * @param strStatus the new status
     */
    private static void updateStatus( String strSql, int nId, String strStatus )
    {
        try ( DAOUtil dao = new DAOUtil( strSql, PLUGIN ) )
        {
            dao.setString( 1, strStatus );
            dao.setTimestamp( 2, Timestamp.valueOf( LocalDateTime.now( ) ) );
            dao.setInt( 3, nId );
            dao.executeUpdate( );
        }
    }

    /**
     * Marks an appointment row as successfully created and stores the generated appointment id.
     *
     * @param nId             the {@code id_import_appointment}
     * @param nAppointmentId  the id returned by the appointment plugin after creation
     */
    public static void markAppointmentCreated( int nId, int nAppointmentId )
    {
        updateAppointment( nId, AppointmentImportStatus.CREATED, null, null, nAppointmentId );
    }

    /**
     * Marks an appointment row as failed and records the error details.
     *
     * @param nId        the {@code id_import_appointment}
     * @param strCode    short error code (e.g. exception class name)
     * @param strMessage human-readable error message
     */
    public static void markAppointmentError( int nId, String strCode, String strMessage )
    {
        updateAppointment( nId, AppointmentImportStatus.ERROR, strCode, strMessage, null );
    }

    /**
     * Updates an appointment row's status, error details and appointment id.
     *
     * @param nId            the {@code id_import_appointment}
     * @param strStatus      the new status
     * @param strCode        error code, or null on success
     * @param strMessage     error message, or null on success
     * @param nAppointmentId created appointment id, or null on error
     */
    private static void updateAppointment( int nId, String strStatus, String strCode, String strMessage, Integer nAppointmentId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_UPDATE_APPOINTMENT, PLUGIN ) )
        {
            dao.setString( 1, strStatus );
            dao.setString( 2, strCode );
            dao.setString( 3, strMessage );
            if ( nAppointmentId == null )
            {
                dao.setIntNull( 4 );
            }
            else
            {
                dao.setInt( 4, nAppointmentId );
            }
            dao.setTimestamp( 5, Timestamp.valueOf( LocalDateTime.now( ) ) );
            dao.setInt( 6, nId );
            dao.executeUpdate( );
        }
    }

    // -- Existence checks --------------------------------------------------

    /**
     * Returns {@code true} if at least one appointment row in the given batch has status {@code ERROR}.
     *
     * @param nBatchId the {@code id_import_batch}
     */
    public static boolean batchHasErrors( int nBatchId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_EXISTS_BATCH_ERROR, PLUGIN ) )
        {
            dao.setInt( 1, nBatchId );
            dao.executeQuery( );
            return dao.next( );
        }
    }

    /**
     * Returns {@code true} if at least one appointment row in any batch of the given file has status {@code ERROR}.
     *
     * @param nFileId the {@code id_import_file}
     */
    public static boolean fileHasErrors( int nFileId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_EXISTS_FILE_ERROR, PLUGIN ) )
        {
            dao.setInt( 1, nFileId );
            dao.executeQuery( );
            return dao.next( );
        }
    }

    // -- Purge operations (Point 5) ----------------------------------------

    /**
     * Returns the ids of all completed batches whose {@code last_exec_date} is older than {@code dtBefore}.
     * Used by the purge daemon to identify records eligible for archiving.
     *
     * @param dtBefore the cutoff datetime; only batches with {@code last_exec_date < dtBefore} are returned
     */
    public static List<Integer> findBatchIdsToPurge( LocalDateTime dtBefore )
    {
        List<Integer> listResult = new ArrayList<>( );
        try ( DAOUtil dao = new DAOUtil( SQL_SELECT_BATCH_IDS_TO_PURGE, PLUGIN ) )
        {
            dao.setTimestamp( 1, timestamp( dtBefore ) );
            dao.executeQuery( );
            while ( dao.next( ) )
            {
                listResult.add( dao.getInt( 1 ) );
            }
        }
        return listResult;
    }

    /**
     * Deletes all appointment rows belonging to the given batch.
     * Called before archiving the batch to remove sensitive personal data.
     *
     * @param nBatchId the {@code id_import_batch}
     */
    public static void purgeAppointmentsByBatch( int nBatchId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_DELETE_APPOINTMENTS_BY_BATCH, PLUGIN ) )
        {
            dao.setInt( 1, nBatchId );
            dao.executeUpdate( );
        }
    }

    /**
     * Sets the batch status to {@code ARCHIVED} and records the current datetime as {@code last_exec_date}.
     * Appointment rows must have been purged before calling this method.
     *
     * @param nBatchId the {@code id_import_batch}
     */
    public static void archiveBatch( int nBatchId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_ARCHIVE_BATCH, PLUGIN ) )
        {
            dao.setTimestamp( 1, timestamp( LocalDateTime.now( ) ) );
            dao.setInt( 2, nBatchId );
            dao.executeUpdate( );
        }
    }

    /**
     * Returns {@code true} if every batch belonging to the given file has status {@code ARCHIVED}.
     * Used to decide whether the parent file can also be archived.
     *
     * @param nFileId the {@code id_import_file}
     */
    public static boolean fileAllBatchesArchived( int nFileId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_SELECT_FILE_ALL_BATCHES_ARCHIVED, PLUGIN ) )
        {
            dao.setInt( 1, nFileId );
            dao.executeQuery( );
            return dao.next( ) && dao.getInt( 1 ) == 0;
        }
    }

    /**
     * Sets the file status to {@code ARCHIVED}, clears the source file path (disk reference removed),
     * and records the current datetime as {@code last_exec_date}.
     *
     * @param nFileId the {@code id_import_file}
     */
    public static void archiveFile( int nFileId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_ARCHIVE_FILE, PLUGIN ) )
        {
            dao.setTimestamp( 1, timestamp( LocalDateTime.now( ) ) );
            dao.setInt( 2, nFileId );
            dao.executeUpdate( );
        }
    }

    // -- Mapping helpers ---------------------------------------------------

    /**
     * Maps the current DAOUtil row to an AppointmentImportFile.
     *
     * @param dao a DAOUtil positioned on a valid row
     * @return the mapped file
     */
    private static AppointmentImportFile mapFile( DAOUtil dao )
    {
        AppointmentImportFile file = new AppointmentImportFile( );
        file.setIdImportFile( dao.getInt( 1 ) );
        file.setImportFileName( dao.getString( 2 ) );
        file.setIdForm( dao.getInt( 3 ) );
        file.setStatus( dao.getString( 4 ) );
        file.setFileHash( dao.getString( 5 ) );
        file.setValidationReport( dao.getString( 6 ) );
        file.setCreationDate( local( dao.getTimestamp( 7 ) ) );
        file.setLastExecDate( local( dao.getTimestamp( 8 ) ) );
        return file;
    }

    /**
     * Returns {@code true} if a non-failed import with the same content hash already exists for this form.
     * Files with status {@code VALIDATION_FAILED} are excluded so the user can re-upload a corrected file.
     */
    public static boolean existsDuplicateFile( String strHash, int nFormId )
    {
        try ( DAOUtil dao = new DAOUtil( SQL_EXISTS_DUPLICATE_FILE, PLUGIN ) )
        {
            dao.setString( 1, strHash );
            dao.setInt( 2, nFormId );
            dao.executeQuery( );
            return dao.next( );
        }
    }

    /**
     * Maps the current DAOUtil row to an AppointmentImportBatch.
     *
     * @param dao a DAOUtil positioned on a valid row
     * @return the mapped batch
     */
    private static AppointmentImportBatch mapBatch( DAOUtil dao )
    {
        AppointmentImportBatch batch = new AppointmentImportBatch( );
        batch.setIdImportBatch( dao.getInt( 1 ) );
        batch.setIdImportFile( dao.getInt( 2 ) );
        batch.setImportFileName( dao.getString( 3 ) );
        batch.setIdForm( dao.getInt( 4 ) );
        batch.setStartingDateTime( local( dao.getTimestamp( 5 ) ) );
        batch.setEndingDateTime( local( dao.getTimestamp( 6 ) ) );
        batch.setStatus( dao.getString( 7 ) );
        batch.setCreationDate( local( dao.getTimestamp( 8 ) ) );
        batch.setLastExecDate( local( dao.getTimestamp( 9 ) ) );
        return batch;
    }

    /**
     * Maps the current DAOUtil row to an AppointmentImportAppointment.
     *
     * @param dao a DAOUtil positioned on a valid row
     * @return the mapped appointment row
     */
    private static AppointmentImportAppointment appointment( DAOUtil dao )
    {
        AppointmentImportAppointment appointment = new AppointmentImportAppointment( );
        appointment.setIdImportAppointment( dao.getInt( 1 ) );
        appointment.setIdImportBatch( dao.getInt( 2 ) );
        appointment.setSourceLineNumber( dao.getInt( 3 ) );
        appointment.setGenericAttributesJson( dao.getString( 4 ) );
        appointment.setFormFieldsJson( dao.getString( 5 ) );
        appointment.setStatus( dao.getString( 6 ) );
        appointment.setErrorCode( dao.getString( 7 ) );
        appointment.setErrorMessage( dao.getString( 8 ) );
        Object objId = dao.getObject( 9 );
        appointment.setIdAppointment( objId == null ? null : ( (Number) objId ).intValue( ) );
        appointment.setCreationDate( local( dao.getTimestamp( 10 ) ) );
        appointment.setLastExecDate( local( dao.getTimestamp( 11 ) ) );
        return appointment;
    }

    /**
     * Binds each value in {@code params} as an int parameter, starting at index 1.
     *
     * @param dao    the DAOUtil to bind into
     * @param params int/Integer values in placeholder order
     */
    private static void bindInts( DAOUtil dao, Object... params )
    {
        for ( int nIndex = 0; nIndex < params.length; nIndex++ )
        {
            dao.setInt( nIndex + 1, (Integer) params [ nIndex ] );
        }
    }

    /**
     * Converts a LocalDateTime to a SQL Timestamp.
     *
     * @param dtValue the datetime to convert
     * @return the corresponding Timestamp
     */
    private static Timestamp timestamp( LocalDateTime dtValue )
    {
        return Timestamp.valueOf( dtValue );
    }

    /**
     * Converts a SQL Timestamp to a LocalDateTime, or returns null if the value is null.
     *
     * @param tsValue the timestamp to convert
     * @return the corresponding LocalDateTime, or null
     */
    private static LocalDateTime local( Timestamp tsValue )
    {
        return tsValue == null ? null : tsValue.toLocalDateTime( );
    }

    /**
     * Binds a Timestamp parameter, or SQL NULL if the value is null.
     *
     * @param dao     the DAOUtil to bind into
     * @param nIndex  the 1-based parameter index
     * @param dtValue the datetime to bind, or null
     */
    private static void setTimestampOrNull( DAOUtil dao, int nIndex, LocalDateTime dtValue )
    {
        if ( dtValue == null )
        {
            dao.setNull( nIndex, java.sql.Types.TIMESTAMP );
        }
        else
        {
            dao.setTimestamp( nIndex, timestamp( dtValue ) );
        }
    }
}
