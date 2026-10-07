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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.spring.SpringContextService;

/**
 * Home for uploaded import files, slot batches and source rows.
 */
public final class AppointmentImportHome
{
    /** Name of the module plugin */
    public static final String PLUGIN_NAME = "appointment-importer";

    private static final IAppointmentImportDAO _dao = SpringContextService.getBean( "appointment-importer.appointmentImportDAO" );
    private static final Plugin _plugin = PluginService.getPlugin( PLUGIN_NAME );

    private AppointmentImportHome( )
    {
    }

    // Files

    /**
     * Creates an import file.
     *
     * @param file
     *            the file
     * @return the generated id
     */
    public static int createFile( AppointmentImportFile file )
    {
        return _dao.insertFile( file, _plugin );
    }

    /**
     * Finds an import file.
     *
     * @param nId
     *            the {@code id_import_file}
     * @return the file, or null
     */
    public static AppointmentImportFile findFile( int nId )
    {
        return _dao.loadFile( nId, _plugin );
    }

    /**
     * Finds the ids of the files matching the filters, newest first.
     *
     * @param listFormIds
     *            the forms the files must belong to
     * @param strFileName
     *            optional file name
     * @param strStatus
     *            optional status
     * @return the ids
     */
    public static List<Integer> findFileIds( List<Integer> listFormIds, String strFileName, String strStatus )
    {
        return _dao.selectFileIds( listFormIds, strFileName, strStatus, _plugin );
    }

    /**
     * Finds the files with the given ids, in the order of the list.
     *
     * @param listIds
     *            the ids
     * @return the files
     */
    public static List<AppointmentImportFile> findFilesByIds( List<Integer> listIds )
    {
        return _dao.selectFilesByIds( listIds, _plugin );
    }

    /**
     * Finds the distinct names of the files imported on the given forms.
     *
     * @param listFormIds
     *            the forms
     * @return the names
     */
    public static List<String> findFileNames( List<Integer> listFormIds )
    {
        return _dao.selectFileNames( listFormIds, _plugin );
    }

    /**
     * Tells whether the same content was already accepted for the form.
     *
     * @param strHash
     *            the SHA-256 of the content
     * @param nFormId
     *            the form
     * @return true if a duplicate exists
     */
    public static boolean existsDuplicateFile( String strHash, int nFormId )
    {
        return _dao.existsDuplicateFile( strHash, nFormId, _plugin );
    }

    /**
     * Updates the status of a file.
     *
     * @param nId
     *            the {@code id_import_file}
     * @param strStatus
     *            the status
     */
    public static void updateFileStatus( int nId, String strStatus )
    {
        _dao.updateFileStatus( nId, strStatus, _plugin );
    }

    /**
     * Finds the files still pending whose batches are all done.
     *
     * @return the ids
     */
    public static List<Integer> findFileIdsToClose( )
    {
        return _dao.selectFileIdsToClose( _plugin );
    }

    /**
     * Fills the number of rows by outcome of each file.
     *
     * @param listFiles
     *            the files
     */
    public static void fillCounts( List<AppointmentImportFile> listFiles )
    {
        Map<Integer, ImportCounts> mapCounts = _dao.countAppointmentsByFiles(
                listFiles.stream( ).map( AppointmentImportFile::getIdImportFile ).collect( Collectors.toList( ) ), _plugin );
        listFiles.forEach( file -> file.setCounts( mapCounts.getOrDefault( file.getIdImportFile( ), new ImportCounts( ) ) ) );
    }

    /**
     * Tells whether a row of the file failed.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @return true if at least one row failed
     */
    public static boolean fileHasErrors( int nFileId )
    {
        return _dao.fileHasErrors( nFileId, _plugin );
    }

    /**
     * Tells whether every batch of the file is archived.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @return true if no batch is left to archive
     */
    public static boolean fileAllBatchesArchived( int nFileId )
    {
        return _dao.fileAllBatchesArchived( nFileId, _plugin );
    }

    /**
     * Archives a file and removes its validation report.
     *
     * @param nFileId
     *            the {@code id_import_file}
     */
    public static void archiveFile( int nFileId )
    {
        _dao.archiveFile( nFileId, _plugin );
    }

    /**
     * Finds the files rejected at validation before the given date.
     *
     * @param dtBefore
     *            the threshold
     * @return the ids
     */
    public static List<Integer> findRejectedFileIdsToPurge( LocalDateTime dtBefore )
    {
        return _dao.selectRejectedFileIdsToPurge( dtBefore, _plugin );
    }

    // Batches

    /**
     * Creates a batch.
     *
     * @param batch
     *            the batch
     * @return the generated id
     */
    public static int createBatch( AppointmentImportBatch batch )
    {
        return _dao.insertBatch( batch, _plugin );
    }

    /**
     * Finds a batch.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @return the batch, or null
     */
    public static AppointmentImportBatch findBatch( int nId )
    {
        return _dao.loadBatch( nId, _plugin );
    }

    /**
     * Finds the batches of a file, by starting date.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @return the batches
     */
    public static List<AppointmentImportBatch> findBatchesByFile( int nFileId )
    {
        return _dao.selectBatchesByFile( nFileId, _plugin );
    }

    /**
     * Finds the batches with the given status, oldest first.
     *
     * @param strStatus
     *            the status
     * @return the batches
     */
    public static List<AppointmentImportBatch> findBatchesByStatus( String strStatus )
    {
        return _dao.selectBatchesByStatus( strStatus, _plugin );
    }

    /**
     * Finds the ids of the batches matching the filters, newest first.
     *
     * @param listFormIds
     *            the forms the batches must belong to
     * @param strFormId
     *            optional form id
     * @param strStatus
     *            optional status
     * @param strFileName
     *            optional file name
     * @param strDate
     *            optional starting date (yyyy-MM-dd)
     * @return the ids
     */
    public static List<Integer> findBatchIds( List<Integer> listFormIds, String strFormId, String strStatus, String strFileName, String strDate )
    {
        return _dao.selectBatchIds( listFormIds, strFormId, strStatus, strFileName, strDate, _plugin );
    }

    /**
     * Finds the batches with the given ids, in the order of the list.
     *
     * @param listIds
     *            the ids
     * @return the batches
     */
    public static List<AppointmentImportBatch> findBatchesByIds( List<Integer> listIds )
    {
        return _dao.selectBatchesByIds( listIds, _plugin );
    }

    /**
     * Takes a pending batch for processing.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param strToken
     *            the token identifying the caller
     * @return true if the caller now owns the batch
     */
    public static boolean claimPendingBatch( int nId, String strToken )
    {
        return _dao.claimBatch( nId, AppointmentImportStatus.PENDING, null, strToken, _plugin );
    }

    /**
     * Takes over a batch whose processing stopped: it is still {@code PROCESSING} but was not touched since the given date.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param dtLastExecBefore
     *            the date before which the batch is considered abandoned
     * @param strToken
     *            the token identifying the caller
     * @return true if the caller now owns the batch
     */
    public static boolean claimStaleBatch( int nId, LocalDateTime dtLastExecBefore, String strToken )
    {
        return _dao.claimBatch( nId, AppointmentImportStatus.PROCESSING, dtLastExecBefore, strToken, _plugin );
    }

    /**
     * Records that a batch is still being processed.
     *
     * @param nId
     *            the {@code id_import_batch}
     */
    public static void touchBatch( int nId )
    {
        _dao.touchBatch( nId, _plugin );
    }

    /**
     * Updates the status of a batch and releases it.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param strStatus
     *            the status
     */
    public static void updateBatchStatus( int nId, String strStatus )
    {
        _dao.updateBatchStatus( nId, strStatus, _plugin );
    }

    /**
     * Puts a completed or pending batch back in the queue of the daemon; a batch being processed or archived is left as it is.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @return true if the batch is now pending, false if it is being processed or archived
     */
    public static boolean requeueBatch( int nBatchId )
    {
        return _dao.requeueBatch( nBatchId, _plugin );
    }

    /**
     * Fills the number of rows by outcome of each batch.
     *
     * @param listBatches
     *            the batches
     */
    public static void fillBatchCounts( List<AppointmentImportBatch> listBatches )
    {
        Map<Integer, ImportCounts> mapCounts = _dao.countAppointmentsByBatches(
                listBatches.stream( ).map( AppointmentImportBatch::getIdImportBatch ).collect( Collectors.toList( ) ), _plugin );
        listBatches.forEach( batch -> batch.setCounts( mapCounts.getOrDefault( batch.getIdImportBatch( ), new ImportCounts( ) ) ) );
    }

    /**
     * Tells whether a row of the batch failed.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @return true if at least one row failed
     */
    public static boolean batchHasErrors( int nBatchId )
    {
        return _dao.batchHasErrors( nBatchId, _plugin );
    }

    /**
     * Finds the completed batches last updated before the given date.
     *
     * @param dtBefore
     *            the threshold
     * @return the ids
     */
    public static List<Integer> findBatchIdsToPurge( LocalDateTime dtBefore )
    {
        return _dao.selectBatchIdsToPurge( dtBefore, _plugin );
    }

    /**
     * Deletes the rows of a batch, then archives it.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     */
    public static void purgeBatch( int nBatchId )
    {
        _dao.deleteAppointmentsByBatch( nBatchId, _plugin );
        _dao.archiveBatch( nBatchId, _plugin );
    }

    // Rows

    /**
     * Creates the rows of an import.
     *
     * @param listAppointments
     *            the rows
     */
    public static void createAppointments( List<AppointmentImportAppointment> listAppointments )
    {
        _dao.insertAppointments( listAppointments, _plugin );
    }

    /**
     * Finds the rows of a batch, by source line.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param strStatus
     *            optional status, null for every row
     * @return the rows
     */
    public static List<AppointmentImportAppointment> findAppointmentsByBatch( int nBatchId, String strStatus )
    {
        return _dao.selectAppointmentsByBatch( nBatchId, strStatus, _plugin );
    }

    /**
     * Counts the rows of a batch.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @return the count
     */
    public static int countAppointmentsByBatch( int nBatchId )
    {
        return _dao.countAppointmentsByBatch( nBatchId, false, _plugin );
    }

    /**
     * Counts the rows of a batch that are created or in error.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @return the count
     */
    public static int countProcessedAppointmentsByBatch( int nBatchId )
    {
        return _dao.countAppointmentsByBatch( nBatchId, true, _plugin );
    }

    /**
     * Finds a row.
     *
     * @param nId
     *            the {@code id_import_appointment}
     * @return the row, or null
     */
    public static AppointmentImportAppointment findAppointment( int nId )
    {
        return _dao.loadAppointment( nId, _plugin );
    }

    /**
     * Replaces the values of a row.
     *
     * @param nId
     *            the {@code id_import_appointment}
     * @param strGenericAttributesData
     *            the values of the standard columns, as JSON
     * @param strFormFieldsData
     *            the values of the form fields, as JSON
     */
    public static void updateAppointmentData( int nId, String strGenericAttributesData, String strFormFieldsData )
    {
        _dao.updateAppointmentData( nId, strGenericAttributesData, strFormFieldsData, _plugin );
    }

    /**
     * Puts the rows in error of a batch back to pending, except those with the given error code.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param strExcludedErrorCode
     *            the error code of the rows to leave in error
     */
    public static void requeueErrorRows( int nBatchId, String strExcludedErrorCode )
    {
        _dao.requeueErrorRows( nBatchId, strExcludedErrorCode, _plugin );
    }

    /**
     * Puts a row in error back to pending.
     *
     * @param nId
     *            the {@code id_import_appointment}
     */
    public static void requeueRow( int nId )
    {
        _dao.requeueRow( nId, _plugin );
    }

    /**
     * Marks a row as being processed, before the appointment is saved.
     *
     * @param nId
     *            the {@code id_import_appointment}
     */
    public static void markAppointmentProcessing( int nId )
    {
        _dao.updateAppointment( nId, AppointmentImportStatus.PROCESSING, null, null, null, _plugin );
    }

    /**
     * Marks a row as created.
     *
     * @param nId
     *            the {@code id_import_appointment}
     * @param nAppointmentId
     *            the created appointment
     */
    public static void markAppointmentCreated( int nId, int nAppointmentId )
    {
        _dao.updateAppointment( nId, AppointmentImportStatus.CREATED, null, null, nAppointmentId, _plugin );
    }

    /**
     * Marks a row as failed.
     *
     * @param nId
     *            the {@code id_import_appointment}
     * @param strCode
     *            the error code
     * @param strMessage
     *            the error message
     */
    public static void markAppointmentError( int nId, String strCode, String strMessage )
    {
        _dao.updateAppointment( nId, AppointmentImportStatus.ERROR, strCode, strMessage, null, _plugin );
    }
}
