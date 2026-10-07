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
package fr.paris.lutece.plugins.appointment.modules.importer.business;

import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.util.sql.DAOUtil;

/**
 * SQL implementation of {@link IAppointmentImportDAO}.
 */
public final class AppointmentImportDAO implements IAppointmentImportDAO
{
    private static final String SQL_COLS_FILE = "SELECT id_import_file, import_file_name, id_form, admin_access_code, status, file_hash, validation_report,"
            + " creation_date, last_exec_date FROM appointment_import_file";
    private static final String SQL_COLS_BATCH = "SELECT id_import_batch, id_import_file, import_file_name, id_form, starting_datetime, ending_datetime,"
            + " status, creation_date, last_exec_date FROM appointment_import_batch";
    private static final String SQL_COLS_APPOINTMENT = "SELECT id_import_appointment, id_import_batch, source_line_number, generic_attributes_data,"
            + " form_fields_data, status, error_code, error_message, id_appointment, creation_date, last_exec_date FROM appointment_import_appointment";

    private static final String SQL_INSERT_FILE = "INSERT INTO appointment_import_file"
            + " (import_file_name, id_form, admin_access_code, status, file_hash, validation_report, creation_date, last_exec_date)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_SELECT_FILE = SQL_COLS_FILE + " WHERE id_import_file = ?";
    private static final String SQL_SELECT_FILE_IDS = "SELECT id_import_file FROM appointment_import_file WHERE id_form IN (";
    // A file stays pending while its batches are processed: "processing" means one of them is
    private static final String SQL_AND_FILE_IS_PROCESSING = " AND EXISTS ( SELECT b.id_import_batch FROM appointment_import_batch b"
            + " WHERE b.id_import_file = appointment_import_file.id_import_file AND b.status = '" + AppointmentImportStatus.PROCESSING + "' )";
    private static final String SQL_AND_FILE_HAS_DATE = " AND EXISTS ( SELECT b.id_import_batch FROM appointment_import_batch b"
            + " WHERE b.id_import_file = appointment_import_file.id_import_file AND b.starting_datetime >= ? AND b.starting_datetime < ? )";
    private static final String SQL_SELECT_FILES_BY_IDS = SQL_COLS_FILE + " WHERE id_import_file IN (";
    private static final String SQL_SELECT_FILE_NAMES = "SELECT DISTINCT import_file_name FROM appointment_import_file WHERE id_form IN (";
    private static final String SQL_EXISTS_DUPLICATE_FILE = "SELECT id_import_file FROM appointment_import_file"
            + " WHERE file_hash = ? AND id_form = ? AND status != '" + AppointmentImportStatus.VALIDATION_FAILED + "'";
    private static final String SQL_UPDATE_FILE_STATUS = "UPDATE appointment_import_file SET status = ?, last_exec_date = ? WHERE id_import_file = ?";
    private static final String SQL_EXISTS_FILE_ERROR = "SELECT a.id_import_appointment FROM appointment_import_appointment a"
            + " JOIN appointment_import_batch b ON b.id_import_batch = a.id_import_batch WHERE b.id_import_file = ? AND a.status = '"
            + AppointmentImportStatus.ERROR + "'";
    private static final String SQL_COUNT_FILE_BATCHES_NOT_ARCHIVED = "SELECT COUNT(*) FROM appointment_import_batch WHERE id_import_file = ? AND status != '"
            + AppointmentImportStatus.ARCHIVED + "'";
    // A rejected file loses its hash so that the same content can be submitted again once corrected
    private static final String SQL_ARCHIVE_FILE = "UPDATE appointment_import_file SET file_hash = CASE WHEN status = '"
            + AppointmentImportStatus.VALIDATION_FAILED + "' THEN NULL ELSE file_hash END, status = '" + AppointmentImportStatus.ARCHIVED
            + "', validation_report = NULL, last_exec_date = ? WHERE id_import_file = ?";
    private static final String SQL_SELECT_REJECTED_FILES_TO_PURGE = "SELECT id_import_file FROM appointment_import_file WHERE status = '"
            + AppointmentImportStatus.VALIDATION_FAILED + "' AND creation_date < ?";

    private static final String SQL_INSERT_BATCH = "INSERT INTO appointment_import_batch"
            + " (id_import_file, import_file_name, id_form, starting_datetime, ending_datetime, status, creation_date, last_exec_date)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_SELECT_BATCH = SQL_COLS_BATCH + " WHERE id_import_batch = ?";
    private static final String SQL_SELECT_BATCHES_BY_FILE = SQL_COLS_BATCH + " WHERE id_import_file = ? ORDER BY starting_datetime";
    private static final String SQL_SELECT_BATCHES_BY_STATUS = SQL_COLS_BATCH + " WHERE status = ? ORDER BY id_import_batch";
    private static final String SQL_SELECT_BATCH_IDS = "SELECT id_import_batch FROM appointment_import_batch WHERE id_form IN (";
    private static final String SQL_SELECT_BATCHES_BY_IDS = SQL_COLS_BATCH + " WHERE id_import_batch IN (";
    private static final String SQL_CLAIM_BATCH = "UPDATE appointment_import_batch SET status = '" + AppointmentImportStatus.PROCESSING
            + "', processing_token = ?, last_exec_date = ? WHERE id_import_batch = ? AND status = ?";
    private static final String SQL_CLAIM_BATCH_LAST_EXEC_FILTER = " AND last_exec_date < ?";
    private static final String SQL_SELECT_BATCH_TOKEN = "SELECT processing_token FROM appointment_import_batch WHERE id_import_batch = ? AND status = '"
            + AppointmentImportStatus.PROCESSING + "'";
    private static final String SQL_TOUCH_BATCH = "UPDATE appointment_import_batch SET last_exec_date = ? WHERE id_import_batch = ?";
    private static final String SQL_UPDATE_BATCH_STATUS = "UPDATE appointment_import_batch SET status = ?, processing_token = NULL, last_exec_date = ?"
            + " WHERE id_import_batch = ?";
    private static final String SQL_EXISTS_BATCH_ERROR = "SELECT id_import_appointment FROM appointment_import_appointment WHERE id_import_batch = ? AND status = '"
            + AppointmentImportStatus.ERROR + "'";
    private static final String SQL_SELECT_BATCH_IDS_TO_PURGE = "SELECT id_import_batch FROM appointment_import_batch WHERE last_exec_date < ? AND status IN ('"
            + AppointmentImportStatus.COMPLETED + "','" + AppointmentImportStatus.COMPLETED_WITH_ERRORS + "')";
    // Only a batch still completed: a batch retried meanwhile must keep its rows
    private static final String SQL_ARCHIVE_BATCH = "UPDATE appointment_import_batch SET status = '" + AppointmentImportStatus.ARCHIVED
            + "', processing_token = NULL, last_exec_date = ? WHERE id_import_batch = ? AND status IN ('" + AppointmentImportStatus.COMPLETED + "','"
            + AppointmentImportStatus.COMPLETED_WITH_ERRORS + "')";

    private static final String SQL_INSERT_APPOINTMENT = "INSERT INTO appointment_import_appointment"
            + " (id_import_batch, source_line_number, generic_attributes_data, form_fields_data, status, error_code, error_message, id_appointment,"
            + " creation_date, last_exec_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_SELECT_APPOINTMENTS_BY_BATCH = SQL_COLS_APPOINTMENT + " WHERE id_import_batch = ?";
    private static final String SQL_FILTER_STATUS = " AND status = ?";
    private static final String SQL_ORDER_BY_LINE = " ORDER BY source_line_number";
    private static final String SQL_COUNT_APPOINTMENTS_BY_BATCH = "SELECT COUNT(*) FROM appointment_import_appointment WHERE id_import_batch = ?";
    private static final String SQL_FILTER_PROCESSED = " AND status IN ('" + AppointmentImportStatus.CREATED + "','" + AppointmentImportStatus.ERROR + "')";
    private static final String SQL_UPDATE_APPOINTMENT = "UPDATE appointment_import_appointment"
            + " SET status = ?, error_code = ?, error_message = ?, id_appointment = ?, last_exec_date = ? WHERE id_import_appointment = ?";
    private static final String SQL_DELETE_APPOINTMENTS_BY_BATCH = "DELETE FROM appointment_import_appointment WHERE id_import_batch = ?";
    private static final String SQL_SELECT_APPOINTMENT = SQL_COLS_APPOINTMENT + " WHERE id_import_appointment = ?";
    private static final String SQL_UPDATE_APPOINTMENT_DATA = "UPDATE appointment_import_appointment SET generic_attributes_data = ?, form_fields_data = ?"
            + " WHERE id_import_appointment = ?";
    private static final String SQL_REQUEUE_ERROR_ROWS = "UPDATE appointment_import_appointment SET status = '" + AppointmentImportStatus.PENDING
            + "', error_code = NULL, error_message = NULL, last_exec_date = ? WHERE id_import_batch = ? AND status = '" + AppointmentImportStatus.ERROR
            + "' AND ( error_code IS NULL OR error_code <> ? )";
    private static final String SQL_REQUEUE_ROW = "UPDATE appointment_import_appointment SET status = '" + AppointmentImportStatus.PENDING
            + "', error_code = NULL, error_message = NULL, last_exec_date = ? WHERE id_import_appointment = ? AND status = '"
            + AppointmentImportStatus.ERROR + "'";
    private static final String SQL_REQUEUE_BATCH = "UPDATE appointment_import_batch SET status = '" + AppointmentImportStatus.PENDING
            + "', processing_token = NULL, last_exec_date = ? WHERE id_import_batch = ? AND status IN ('" + AppointmentImportStatus.COMPLETED + "','"
            + AppointmentImportStatus.COMPLETED_WITH_ERRORS + "','" + AppointmentImportStatus.PENDING + "')";
    private static final String SQL_COUNT_BY_FILES = "SELECT b.id_import_file, a.status, COUNT(*) FROM appointment_import_appointment a"
            + " JOIN appointment_import_batch b ON b.id_import_batch = a.id_import_batch WHERE b.id_import_file IN (";
    private static final String SQL_COUNT_BY_FILES_GROUP = ") GROUP BY b.id_import_file, a.status";
    private static final String SQL_COUNT_BY_BATCHES = "SELECT id_import_batch, status, COUNT(*) FROM appointment_import_appointment WHERE id_import_batch IN (";
    private static final String SQL_COUNT_BY_BATCHES_GROUP = ") GROUP BY id_import_batch, status";
    // A file whose batches are all done but which is still pending: the instance that closed its last batch missed it
    private static final String SQL_SELECT_FILE_IDS_TO_CLOSE = "SELECT f.id_import_file FROM appointment_import_file f WHERE f.status = '"
            + AppointmentImportStatus.PENDING + "' AND EXISTS ( SELECT b.id_import_batch FROM appointment_import_batch b WHERE b.id_import_file = f.id_import_file )"
            + " AND NOT EXISTS ( SELECT b.id_import_batch FROM appointment_import_batch b WHERE b.id_import_file = f.id_import_file AND b.status IN ('"
            + AppointmentImportStatus.PENDING + "','" + AppointmentImportStatus.PROCESSING + "') )";

    private static final String SQL_AND_FILE_NAME = " AND import_file_name = ?";
    private static final String SQL_AND_STATUS = " AND status = ?";
    private static final String SQL_AND_FORM = " AND id_form = ?";
    // A range rather than DATE( ), which is MySQL only
    private static final String SQL_AND_DATE = " AND starting_datetime >= ? AND starting_datetime < ?";
    private static final String SQL_ORDER_BY_FILE_DESC = " ORDER BY id_import_file DESC";
    private static final String SQL_ORDER_BY_BATCH_DESC = " ORDER BY id_import_batch DESC";
    private static final String SQL_ORDER_BY_FILE_NAME = " ORDER BY import_file_name";

    // Files

    @Override
    public int insertFile( AppointmentImportFile file, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_INSERT_FILE, Statement.RETURN_GENERATED_KEYS, plugin ) )
        {
            int nIndex = 1;
            daoUtil.setString( nIndex++, file.getImportFileName( ) );
            daoUtil.setInt( nIndex++, file.getIdForm( ) );
            daoUtil.setString( nIndex++, file.getAdminAccessCode( ) );
            daoUtil.setString( nIndex++, file.getStatus( ) );
            daoUtil.setString( nIndex++, file.getFileHash( ) );
            daoUtil.setString( nIndex++, file.getValidationReport( ) );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( file.getCreationDate( ) ) );
            setTimestampOrNull( daoUtil, nIndex, file.getLastExecDate( ) );
            daoUtil.executeUpdate( );
            if ( daoUtil.nextGeneratedKey( ) )
            {
                return daoUtil.getGeneratedKeyInt( 1 );
            }
            throw new IllegalStateException( "Missing generated key for import file" );
        }
    }

    @Override
    public AppointmentImportFile loadFile( int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_FILE, plugin ) )
        {
            daoUtil.setInt( 1, nId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) ? mapFile( daoUtil ) : null;
        }
    }

    @Override
    public List<Integer> selectFileIds( List<Integer> listFormIds, String strFormId, String strFileName, String strStatus, String strDate,
            Plugin plugin )
    {
        if ( listFormIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        StringBuilder sbSql = new StringBuilder( SQL_SELECT_FILE_IDS ).append( placeholders( listFormIds.size( ) ) ).append( ')' );
        if ( StringUtils.isNotBlank( strFormId ) )
        {
            sbSql.append( SQL_AND_FORM );
        }
        if ( StringUtils.isNotBlank( strFileName ) )
        {
            sbSql.append( SQL_AND_FILE_NAME );
        }
        boolean bProcessing = AppointmentImportStatus.PROCESSING.equals( strStatus );
        if ( bProcessing )
        {
            sbSql.append( SQL_AND_FILE_IS_PROCESSING );
        }
        else
            if ( StringUtils.isNotBlank( strStatus ) )
            {
                sbSql.append( SQL_AND_STATUS );
            }
        if ( StringUtils.isNotBlank( strDate ) )
        {
            sbSql.append( SQL_AND_FILE_HAS_DATE );
        }
        sbSql.append( SQL_ORDER_BY_FILE_DESC );
        try ( DAOUtil daoUtil = new DAOUtil( sbSql.toString( ), plugin ) )
        {
            int nIndex = bindInts( daoUtil, 1, listFormIds );
            if ( StringUtils.isNotBlank( strFormId ) )
            {
                daoUtil.setInt( nIndex++, Integer.parseInt( strFormId ) );
            }
            if ( StringUtils.isNotBlank( strFileName ) )
            {
                daoUtil.setString( nIndex++, strFileName );
            }
            if ( !bProcessing && StringUtils.isNotBlank( strStatus ) )
            {
                daoUtil.setString( nIndex++, strStatus );
            }
            if ( StringUtils.isNotBlank( strDate ) )
            {
                LocalDate date = LocalDate.parse( strDate );
                daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( date.atStartOfDay( ) ) );
                daoUtil.setTimestamp( nIndex, Timestamp.valueOf( date.plusDays( 1 ).atStartOfDay( ) ) );
            }
            return selectIds( daoUtil );
        }
    }

    @Override
    public List<AppointmentImportFile> selectFilesByIds( List<Integer> listIds, Plugin plugin )
    {
        if ( listIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        Map<Integer, AppointmentImportFile> mapFiles = new HashMap<>( );
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_FILES_BY_IDS + placeholders( listIds.size( ) ) + ")", plugin ) )
        {
            bindInts( daoUtil, 1, listIds );
            daoUtil.executeQuery( );
            while ( daoUtil.next( ) )
            {
                AppointmentImportFile file = mapFile( daoUtil );
                mapFiles.put( file.getIdImportFile( ), file );
            }
        }
        return orderByIds( listIds, mapFiles );
    }

    @Override
    public List<String> selectFileNames( List<Integer> listFormIds, Plugin plugin )
    {
        List<String> listNames = new ArrayList<>( );
        if ( listFormIds.isEmpty( ) )
        {
            return listNames;
        }
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_FILE_NAMES + placeholders( listFormIds.size( ) ) + ")" + SQL_ORDER_BY_FILE_NAME, plugin ) )
        {
            bindInts( daoUtil, 1, listFormIds );
            daoUtil.executeQuery( );
            while ( daoUtil.next( ) )
            {
                listNames.add( daoUtil.getString( 1 ) );
            }
        }
        return listNames;
    }

    @Override
    public boolean existsDuplicateFile( String strHash, int nFormId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_EXISTS_DUPLICATE_FILE, plugin ) )
        {
            daoUtil.setString( 1, strHash );
            daoUtil.setInt( 2, nFormId );
            daoUtil.executeQuery( );
            return daoUtil.next( );
        }
    }

    @Override
    public void updateFileStatus( int nId, String strStatus, Plugin plugin )
    {
        updateStatus( SQL_UPDATE_FILE_STATUS, nId, strStatus, plugin );
    }

    @Override
    public List<Integer> selectFileIdsToClose( Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_FILE_IDS_TO_CLOSE, plugin ) )
        {
            return selectIds( daoUtil );
        }
    }

    @Override
    public Map<Integer, ImportCounts> countAppointmentsByFiles( List<Integer> listFileIds, Plugin plugin )
    {
        return countAppointments( SQL_COUNT_BY_FILES, SQL_COUNT_BY_FILES_GROUP, listFileIds, plugin );
    }

    @Override
    public boolean fileHasErrors( int nFileId, Plugin plugin )
    {
        return exists( SQL_EXISTS_FILE_ERROR, nFileId, plugin );
    }

    @Override
    public boolean fileAllBatchesArchived( int nFileId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_COUNT_FILE_BATCHES_NOT_ARCHIVED, plugin ) )
        {
            daoUtil.setInt( 1, nFileId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) && daoUtil.getInt( 1 ) == 0;
        }
    }

    @Override
    public void archiveFile( int nFileId, Plugin plugin )
    {
        archive( SQL_ARCHIVE_FILE, nFileId, plugin );
    }

    @Override
    public List<Integer> selectRejectedFileIdsToPurge( LocalDateTime dtBefore, Plugin plugin )
    {
        return selectIdsBefore( SQL_SELECT_REJECTED_FILES_TO_PURGE, dtBefore, plugin );
    }

    // Batches

    @Override
    public int insertBatch( AppointmentImportBatch batch, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_INSERT_BATCH, Statement.RETURN_GENERATED_KEYS, plugin ) )
        {
            int nIndex = 1;
            daoUtil.setInt( nIndex++, batch.getIdImportFile( ) );
            daoUtil.setString( nIndex++, batch.getImportFileName( ) );
            daoUtil.setInt( nIndex++, batch.getIdForm( ) );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( batch.getStartingDateTime( ) ) );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( batch.getEndingDateTime( ) ) );
            daoUtil.setString( nIndex++, batch.getStatus( ) );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( batch.getCreationDate( ) ) );
            setTimestampOrNull( daoUtil, nIndex, batch.getLastExecDate( ) );
            daoUtil.executeUpdate( );
            if ( daoUtil.nextGeneratedKey( ) )
            {
                return daoUtil.getGeneratedKeyInt( 1 );
            }
            throw new IllegalStateException( "Missing generated key for import batch" );
        }
    }

    @Override
    public AppointmentImportBatch loadBatch( int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_BATCH, plugin ) )
        {
            daoUtil.setInt( 1, nId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) ? mapBatch( daoUtil ) : null;
        }
    }

    @Override
    public List<AppointmentImportBatch> selectBatchesByFile( int nFileId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_BATCHES_BY_FILE, plugin ) )
        {
            daoUtil.setInt( 1, nFileId );
            return selectBatches( daoUtil );
        }
    }

    @Override
    public List<AppointmentImportBatch> selectBatchesByStatus( String strStatus, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_BATCHES_BY_STATUS, plugin ) )
        {
            daoUtil.setString( 1, strStatus );
            return selectBatches( daoUtil );
        }
    }

    @Override
    public List<Integer> selectBatchIds( List<Integer> listFormIds, String strFormId, String strStatus, String strFileName, String strDate, Plugin plugin )
    {
        if ( listFormIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        StringBuilder sbSql = new StringBuilder( SQL_SELECT_BATCH_IDS ).append( placeholders( listFormIds.size( ) ) ).append( ')' );
        if ( StringUtils.isNotBlank( strFormId ) )
        {
            sbSql.append( SQL_AND_FORM );
        }
        if ( StringUtils.isNotBlank( strStatus ) )
        {
            sbSql.append( SQL_AND_STATUS );
        }
        if ( StringUtils.isNotBlank( strFileName ) )
        {
            sbSql.append( SQL_AND_FILE_NAME );
        }
        if ( StringUtils.isNotBlank( strDate ) )
        {
            sbSql.append( SQL_AND_DATE );
        }
        sbSql.append( SQL_ORDER_BY_BATCH_DESC );
        try ( DAOUtil daoUtil = new DAOUtil( sbSql.toString( ), plugin ) )
        {
            int nIndex = bindInts( daoUtil, 1, listFormIds );
            if ( StringUtils.isNotBlank( strFormId ) )
            {
                daoUtil.setInt( nIndex++, Integer.parseInt( strFormId ) );
            }
            if ( StringUtils.isNotBlank( strStatus ) )
            {
                daoUtil.setString( nIndex++, strStatus );
            }
            if ( StringUtils.isNotBlank( strFileName ) )
            {
                daoUtil.setString( nIndex++, strFileName );
            }
            if ( StringUtils.isNotBlank( strDate ) )
            {
                LocalDate date = LocalDate.parse( strDate );
                daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( date.atStartOfDay( ) ) );
                daoUtil.setTimestamp( nIndex, Timestamp.valueOf( date.plusDays( 1 ).atStartOfDay( ) ) );
            }
            return selectIds( daoUtil );
        }
    }

    @Override
    public List<AppointmentImportBatch> selectBatchesByIds( List<Integer> listIds, Plugin plugin )
    {
        if ( listIds.isEmpty( ) )
        {
            return new ArrayList<>( );
        }
        Map<Integer, AppointmentImportBatch> mapBatches = new HashMap<>( );
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_BATCHES_BY_IDS + placeholders( listIds.size( ) ) + ")", plugin ) )
        {
            bindInts( daoUtil, 1, listIds );
            for ( AppointmentImportBatch batch : selectBatches( daoUtil ) )
            {
                mapBatches.put( batch.getIdImportBatch( ), batch );
            }
        }
        return orderByIds( listIds, mapBatches );
    }

    @Override
    public boolean claimBatch( int nId, String strFromStatus, LocalDateTime dtLastExecBefore, String strToken, Plugin plugin )
    {
        String strSql = dtLastExecBefore == null ? SQL_CLAIM_BATCH : SQL_CLAIM_BATCH + SQL_CLAIM_BATCH_LAST_EXEC_FILTER;
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            int nIndex = 1;
            daoUtil.setString( nIndex++, strToken );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( nIndex++, nId );
            daoUtil.setString( nIndex++, strFromStatus );
            if ( dtLastExecBefore != null )
            {
                daoUtil.setTimestamp( nIndex, Timestamp.valueOf( dtLastExecBefore ) );
            }
            daoUtil.executeUpdate( );
        }
        // The UPDATE only matches while the batch is still free: whoever's token is stored owns the batch
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_BATCH_TOKEN, plugin ) )
        {
            daoUtil.setInt( 1, nId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) && strToken.equals( daoUtil.getString( 1 ) );
        }
    }

    @Override
    public void touchBatch( int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_TOUCH_BATCH, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 2, nId );
            daoUtil.executeUpdate( );
        }
    }

    @Override
    public void updateBatchStatus( int nId, String strStatus, Plugin plugin )
    {
        updateStatus( SQL_UPDATE_BATCH_STATUS, nId, strStatus, plugin );
    }

    @Override
    public boolean requeueBatch( int nBatchId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_REQUEUE_BATCH, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 2, nBatchId );
            daoUtil.executeUpdate( );
        }
        AppointmentImportBatch batch = loadBatch( nBatchId, plugin );
        return batch != null && AppointmentImportStatus.PENDING.equals( batch.getStatus( ) );
    }

    @Override
    public Map<Integer, ImportCounts> countAppointmentsByBatches( List<Integer> listBatchIds, Plugin plugin )
    {
        return countAppointments( SQL_COUNT_BY_BATCHES, SQL_COUNT_BY_BATCHES_GROUP, listBatchIds, plugin );
    }

    @Override
    public boolean batchHasErrors( int nBatchId, Plugin plugin )
    {
        return exists( SQL_EXISTS_BATCH_ERROR, nBatchId, plugin );
    }

    @Override
    public List<Integer> selectBatchIdsToPurge( LocalDateTime dtBefore, Plugin plugin )
    {
        return selectIdsBefore( SQL_SELECT_BATCH_IDS_TO_PURGE, dtBefore, plugin );
    }

    @Override
    public boolean archiveBatch( int nBatchId, Plugin plugin )
    {
        archive( SQL_ARCHIVE_BATCH, nBatchId, plugin );
        AppointmentImportBatch batch = loadBatch( nBatchId, plugin );
        return batch != null && AppointmentImportStatus.ARCHIVED.equals( batch.getStatus( ) );
    }

    // Rows

    @Override
    public void insertAppointments( List<AppointmentImportAppointment> listAppointments, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_INSERT_APPOINTMENT, plugin ) )
        {
            for ( AppointmentImportAppointment appointment : listAppointments )
            {
                int nIndex = 1;
                daoUtil.setInt( nIndex++, appointment.getIdImportBatch( ) );
                daoUtil.setInt( nIndex++, appointment.getSourceLineNumber( ) );
                daoUtil.setString( nIndex++, appointment.getGenericAttributesJson( ) );
                daoUtil.setString( nIndex++, appointment.getFormFieldsJson( ) );
                daoUtil.setString( nIndex++, appointment.getStatus( ) );
                daoUtil.setString( nIndex++, appointment.getErrorCode( ) );
                daoUtil.setString( nIndex++, appointment.getErrorMessage( ) );
                setIntOrNull( daoUtil, nIndex++, appointment.getIdAppointment( ) );
                daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( appointment.getCreationDate( ) ) );
                setTimestampOrNull( daoUtil, nIndex, appointment.getLastExecDate( ) );
                daoUtil.addBatch( );
            }
            daoUtil.executeBatch( );
        }
    }

    @Override
    public List<AppointmentImportAppointment> selectAppointmentsByBatch( int nBatchId, String strStatus, Plugin plugin )
    {
        String strSql = SQL_SELECT_APPOINTMENTS_BY_BATCH + ( strStatus == null ? "" : SQL_FILTER_STATUS ) + SQL_ORDER_BY_LINE;
        List<AppointmentImportAppointment> listResult = new ArrayList<>( );
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setInt( 1, nBatchId );
            if ( strStatus != null )
            {
                daoUtil.setString( 2, strStatus );
            }
            daoUtil.executeQuery( );
            while ( daoUtil.next( ) )
            {
                listResult.add( mapAppointment( daoUtil ) );
            }
        }
        return listResult;
    }

    @Override
    public int countAppointmentsByBatch( int nBatchId, boolean bProcessedOnly, Plugin plugin )
    {
        String strSql = SQL_COUNT_APPOINTMENTS_BY_BATCH + ( bProcessedOnly ? SQL_FILTER_PROCESSED : "" );
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setInt( 1, nBatchId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) ? daoUtil.getInt( 1 ) : 0;
        }
    }

    @Override
    public void updateAppointment( int nId, String strStatus, String strCode, String strMessage, Integer nAppointmentId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_UPDATE_APPOINTMENT, plugin ) )
        {
            int nIndex = 1;
            daoUtil.setString( nIndex++, strStatus );
            daoUtil.setString( nIndex++, strCode );
            daoUtil.setString( nIndex++, strMessage );
            setIntOrNull( daoUtil, nIndex++, nAppointmentId );
            daoUtil.setTimestamp( nIndex++, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( nIndex, nId );
            daoUtil.executeUpdate( );
        }
    }

    @Override
    public AppointmentImportAppointment loadAppointment( int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_SELECT_APPOINTMENT, plugin ) )
        {
            daoUtil.setInt( 1, nId );
            daoUtil.executeQuery( );
            return daoUtil.next( ) ? mapAppointment( daoUtil ) : null;
        }
    }

    @Override
    public void updateAppointmentData( int nId, String strGenericAttributesData, String strFormFieldsData, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_UPDATE_APPOINTMENT_DATA, plugin ) )
        {
            daoUtil.setString( 1, strGenericAttributesData );
            daoUtil.setString( 2, strFormFieldsData );
            daoUtil.setInt( 3, nId );
            daoUtil.executeUpdate( );
        }
    }

    @Override
    public void requeueErrorRows( int nBatchId, String strExcludedErrorCode, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_REQUEUE_ERROR_ROWS, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 2, nBatchId );
            daoUtil.setString( 3, strExcludedErrorCode );
            daoUtil.executeUpdate( );
        }
    }

    @Override
    public void requeueRow( int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_REQUEUE_ROW, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 2, nId );
            daoUtil.executeUpdate( );
        }
    }

    @Override
    public void deleteAppointmentsByBatch( int nBatchId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( SQL_DELETE_APPOINTMENTS_BY_BATCH, plugin ) )
        {
            daoUtil.setInt( 1, nBatchId );
            daoUtil.executeUpdate( );
        }
    }

    // Helpers

    private static void updateStatus( String strSql, int nId, String strStatus, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setString( 1, strStatus );
            daoUtil.setTimestamp( 2, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 3, nId );
            daoUtil.executeUpdate( );
        }
    }

    private static void archive( String strSql, int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( LocalDateTime.now( ) ) );
            daoUtil.setInt( 2, nId );
            daoUtil.executeUpdate( );
        }
    }

    private static boolean exists( String strSql, int nId, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setInt( 1, nId );
            daoUtil.executeQuery( );
            return daoUtil.next( );
        }
    }

    private static Map<Integer, ImportCounts> countAppointments( String strSqlStart, String strSqlEnd, List<Integer> listIds, Plugin plugin )
    {
        Map<Integer, ImportCounts> mapCounts = new HashMap<>( );
        if ( listIds.isEmpty( ) )
        {
            return mapCounts;
        }
        try ( DAOUtil daoUtil = new DAOUtil( strSqlStart + placeholders( listIds.size( ) ) + strSqlEnd, plugin ) )
        {
            bindInts( daoUtil, 1, listIds );
            daoUtil.executeQuery( );
            while ( daoUtil.next( ) )
            {
                mapCounts.computeIfAbsent( daoUtil.getInt( 1 ), nId -> new ImportCounts( ) ).add( daoUtil.getString( 2 ), daoUtil.getInt( 3 ) );
            }
        }
        return mapCounts;
    }

    private static List<Integer> selectIdsBefore( String strSql, LocalDateTime dtBefore, Plugin plugin )
    {
        try ( DAOUtil daoUtil = new DAOUtil( strSql, plugin ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( dtBefore ) );
            return selectIds( daoUtil );
        }
    }

    private static List<Integer> selectIds( DAOUtil daoUtil )
    {
        List<Integer> listIds = new ArrayList<>( );
        daoUtil.executeQuery( );
        while ( daoUtil.next( ) )
        {
            listIds.add( daoUtil.getInt( 1 ) );
        }
        return listIds;
    }

    private static List<AppointmentImportBatch> selectBatches( DAOUtil daoUtil )
    {
        List<AppointmentImportBatch> listBatches = new ArrayList<>( );
        daoUtil.executeQuery( );
        while ( daoUtil.next( ) )
        {
            listBatches.add( mapBatch( daoUtil ) );
        }
        return listBatches;
    }

    private static <T> List<T> orderByIds( List<Integer> listIds, Map<Integer, T> mapItems )
    {
        List<T> listResult = new ArrayList<>( listIds.size( ) );
        for ( Integer nId : listIds )
        {
            T item = mapItems.get( nId );
            if ( item != null )
            {
                listResult.add( item );
            }
        }
        return listResult;
    }

    private static String placeholders( int nCount )
    {
        return String.join( ",", Collections.nCopies( nCount, "?" ) );
    }

    private static int bindInts( DAOUtil daoUtil, int nStartIndex, List<Integer> listValues )
    {
        int nIndex = nStartIndex;
        for ( Integer nValue : listValues )
        {
            daoUtil.setInt( nIndex++, nValue );
        }
        return nIndex;
    }

    private static void setIntOrNull( DAOUtil daoUtil, int nIndex, Integer nValue )
    {
        if ( nValue == null )
        {
            daoUtil.setIntNull( nIndex );
        }
        else
        {
            daoUtil.setInt( nIndex, nValue );
        }
    }

    private static void setTimestampOrNull( DAOUtil daoUtil, int nIndex, LocalDateTime dtValue )
    {
        if ( dtValue == null )
        {
            daoUtil.setNull( nIndex, Types.TIMESTAMP );
        }
        else
        {
            daoUtil.setTimestamp( nIndex, Timestamp.valueOf( dtValue ) );
        }
    }

    private static LocalDateTime toLocalDateTime( Timestamp tsValue )
    {
        return tsValue == null ? null : tsValue.toLocalDateTime( );
    }

    private static AppointmentImportFile mapFile( DAOUtil daoUtil )
    {
        int nIndex = 1;
        AppointmentImportFile file = new AppointmentImportFile( );
        file.setIdImportFile( daoUtil.getInt( nIndex++ ) );
        file.setImportFileName( daoUtil.getString( nIndex++ ) );
        file.setIdForm( daoUtil.getInt( nIndex++ ) );
        file.setAdminAccessCode( daoUtil.getString( nIndex++ ) );
        file.setStatus( daoUtil.getString( nIndex++ ) );
        file.setFileHash( daoUtil.getString( nIndex++ ) );
        file.setValidationReport( daoUtil.getString( nIndex++ ) );
        file.setCreationDate( toLocalDateTime( daoUtil.getTimestamp( nIndex++ ) ) );
        file.setLastExecDate( toLocalDateTime( daoUtil.getTimestamp( nIndex ) ) );
        return file;
    }

    private static AppointmentImportBatch mapBatch( DAOUtil daoUtil )
    {
        int nIndex = 1;
        AppointmentImportBatch batch = new AppointmentImportBatch( );
        batch.setIdImportBatch( daoUtil.getInt( nIndex++ ) );
        batch.setIdImportFile( daoUtil.getInt( nIndex++ ) );
        batch.setImportFileName( daoUtil.getString( nIndex++ ) );
        batch.setIdForm( daoUtil.getInt( nIndex++ ) );
        batch.setStartingDateTime( toLocalDateTime( daoUtil.getTimestamp( nIndex++ ) ) );
        batch.setEndingDateTime( toLocalDateTime( daoUtil.getTimestamp( nIndex++ ) ) );
        batch.setStatus( daoUtil.getString( nIndex++ ) );
        batch.setCreationDate( toLocalDateTime( daoUtil.getTimestamp( nIndex++ ) ) );
        batch.setLastExecDate( toLocalDateTime( daoUtil.getTimestamp( nIndex ) ) );
        return batch;
    }

    private static AppointmentImportAppointment mapAppointment( DAOUtil daoUtil )
    {
        int nIndex = 1;
        AppointmentImportAppointment appointment = new AppointmentImportAppointment( );
        appointment.setIdImportAppointment( daoUtil.getInt( nIndex++ ) );
        appointment.setIdImportBatch( daoUtil.getInt( nIndex++ ) );
        appointment.setSourceLineNumber( daoUtil.getInt( nIndex++ ) );
        appointment.setGenericAttributesJson( daoUtil.getString( nIndex++ ) );
        appointment.setFormFieldsJson( daoUtil.getString( nIndex++ ) );
        appointment.setStatus( daoUtil.getString( nIndex++ ) );
        appointment.setErrorCode( daoUtil.getString( nIndex++ ) );
        appointment.setErrorMessage( daoUtil.getString( nIndex++ ) );
        Object objIdAppointment = daoUtil.getObject( nIndex++ );
        appointment.setIdAppointment( objIdAppointment == null ? null : ( (Number) objIdAppointment ).intValue( ) );
        appointment.setCreationDate( toLocalDateTime( daoUtil.getTimestamp( nIndex++ ) ) );
        appointment.setLastExecDate( toLocalDateTime( daoUtil.getTimestamp( nIndex ) ) );
        return appointment;
    }
}
