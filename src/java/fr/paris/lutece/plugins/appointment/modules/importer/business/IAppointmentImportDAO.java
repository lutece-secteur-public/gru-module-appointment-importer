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

import fr.paris.lutece.portal.service.plugin.Plugin;

/**
 * Data access for uploaded import files, slot batches and source rows.
 */
public interface IAppointmentImportDAO
{
    // Files

    /**
     * Inserts a new import file record.
     *
     * @param file
     *            the file to persist
     * @param plugin
     *            the plugin
     * @return the generated {@code id_import_file}
     */
    int insertFile( AppointmentImportFile file, Plugin plugin );

    /**
     * Loads an import file.
     *
     * @param nId
     *            the {@code id_import_file}
     * @param plugin
     *            the plugin
     * @return the file, or null if not found
     */
    AppointmentImportFile loadFile( int nId, Plugin plugin );

    /**
     * Selects the ids of the files matching the filters, newest first.
     *
     * @param listFormIds
     *            the forms the files must belong to
     * @param strFileName
     *            optional exact file name
     * @param strStatus
     *            optional exact status
     * @param plugin
     *            the plugin
     * @return the matching ids
     */
    List<Integer> selectFileIds( List<Integer> listFormIds, String strFileName, String strStatus, Plugin plugin );

    /**
     * Loads the files with the given ids, in the order of the list.
     *
     * @param listIds
     *            the ids
     * @param plugin
     *            the plugin
     * @return the files
     */
    List<AppointmentImportFile> selectFilesByIds( List<Integer> listIds, Plugin plugin );

    /**
     * Selects the distinct names of the files imported on the given forms.
     *
     * @param listFormIds
     *            the forms
     * @param plugin
     *            the plugin
     * @return the file names, sorted
     */
    List<String> selectFileNames( List<Integer> listFormIds, Plugin plugin );

    /**
     * Tells whether a file with the same content was already accepted for the form.
     *
     * @param strHash
     *            the SHA-256 of the content
     * @param nFormId
     *            the form
     * @param plugin
     *            the plugin
     * @return true if a duplicate exists
     */
    boolean existsDuplicateFile( String strHash, int nFormId, Plugin plugin );

    /**
     * Updates the status of a file.
     *
     * @param nId
     *            the {@code id_import_file}
     * @param strStatus
     *            the new status
     * @param plugin
     *            the plugin
     */
    void updateFileStatus( int nId, String strStatus, Plugin plugin );

    /**
     * Tells whether a row of the file is in error.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @param plugin
     *            the plugin
     * @return true if at least one row failed
     */
    boolean fileHasErrors( int nFileId, Plugin plugin );

    /**
     * Tells whether every batch of the file is archived.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @param plugin
     *            the plugin
     * @return true if no batch is left to archive
     */
    boolean fileAllBatchesArchived( int nFileId, Plugin plugin );

    /**
     * Archives a file and removes its validation report.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @param plugin
     *            the plugin
     */
    void archiveFile( int nFileId, Plugin plugin );

    /**
     * Selects the files rejected at validation before the given date.
     *
     * @param dtBefore
     *            the threshold
     * @param plugin
     *            the plugin
     * @return the ids of the files to archive
     */
    List<Integer> selectRejectedFileIdsToPurge( LocalDateTime dtBefore, Plugin plugin );

    // Batches

    /**
     * Inserts a new batch.
     *
     * @param batch
     *            the batch
     * @param plugin
     *            the plugin
     * @return the generated {@code id_import_batch}
     */
    int insertBatch( AppointmentImportBatch batch, Plugin plugin );

    /**
     * Loads a batch.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param plugin
     *            the plugin
     * @return the batch, or null if not found
     */
    AppointmentImportBatch loadBatch( int nId, Plugin plugin );

    /**
     * Selects the batches of a file, by starting date.
     *
     * @param nFileId
     *            the {@code id_import_file}
     * @param plugin
     *            the plugin
     * @return the batches
     */
    List<AppointmentImportBatch> selectBatchesByFile( int nFileId, Plugin plugin );

    /**
     * Selects the batches with the given status, oldest first.
     *
     * @param strStatus
     *            the status
     * @param plugin
     *            the plugin
     * @return the batches
     */
    List<AppointmentImportBatch> selectBatchesByStatus( String strStatus, Plugin plugin );

    /**
     * Selects the ids of the batches matching the filters, newest first.
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
     * @param plugin
     *            the plugin
     * @return the ids
     */
    List<Integer> selectBatchIds( List<Integer> listFormIds, String strFormId, String strStatus, String strFileName, String strDate, Plugin plugin );

    /**
     * Loads the batches with the given ids, in the order of the list.
     *
     * @param listIds
     *            the ids
     * @param plugin
     *            the plugin
     * @return the batches
     */
    List<AppointmentImportBatch> selectBatchesByIds( List<Integer> listIds, Plugin plugin );

    /**
     * Takes a batch for processing: moves it from {@code strFromStatus} to {@code PROCESSING} with the given token, unless another process took it first.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param strFromStatus
     *            the status the batch must still have
     * @param dtLastExecBefore
     *            if not null, the batch must not have been touched since this date
     * @param strToken
     *            the token identifying the caller
     * @param plugin
     *            the plugin
     * @return true if the caller now owns the batch
     */
    boolean claimBatch( int nId, String strFromStatus, LocalDateTime dtLastExecBefore, String strToken, Plugin plugin );

    /**
     * Records that a batch is still being processed.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param plugin
     *            the plugin
     */
    void touchBatch( int nId, Plugin plugin );

    /**
     * Updates the status of a batch and releases its token.
     *
     * @param nId
     *            the {@code id_import_batch}
     * @param strStatus
     *            the new status
     * @param plugin
     *            the plugin
     */
    void updateBatchStatus( int nId, String strStatus, Plugin plugin );

    /**
     * Tells whether a row of the batch is in error.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param plugin
     *            the plugin
     * @return true if at least one row failed
     */
    boolean batchHasErrors( int nBatchId, Plugin plugin );

    /**
     * Selects the completed batches last updated before the given date.
     *
     * @param dtBefore
     *            the threshold
     * @param plugin
     *            the plugin
     * @return the ids
     */
    List<Integer> selectBatchIdsToPurge( LocalDateTime dtBefore, Plugin plugin );

    /**
     * Archives a batch.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param plugin
     *            the plugin
     */
    void archiveBatch( int nBatchId, Plugin plugin );

    // Rows

    /**
     * Inserts the rows of an import.
     *
     * @param listAppointments
     *            the rows
     * @param plugin
     *            the plugin
     */
    void insertAppointments( List<AppointmentImportAppointment> listAppointments, Plugin plugin );

    /**
     * Selects the rows of a batch, by source line.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param strStatus
     *            optional status
     * @param plugin
     *            the plugin
     * @return the rows
     */
    List<AppointmentImportAppointment> selectAppointmentsByBatch( int nBatchId, String strStatus, Plugin plugin );

    /**
     * Counts the rows of a batch.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param bProcessedOnly
     *            true to count only the rows created or in error
     * @param plugin
     *            the plugin
     * @return the count
     */
    int countAppointmentsByBatch( int nBatchId, boolean bProcessedOnly, Plugin plugin );

    /**
     * Updates the state of a row.
     *
     * @param nId
     *            the {@code id_import_appointment}
     * @param strStatus
     *            the new status
     * @param strCode
     *            the error code, or null
     * @param strMessage
     *            the error message, or null
     * @param nAppointmentId
     *            the created appointment, or null
     * @param plugin
     *            the plugin
     */
    void updateAppointment( int nId, String strStatus, String strCode, String strMessage, Integer nAppointmentId, Plugin plugin );

    /**
     * Deletes the rows of a batch.
     *
     * @param nBatchId
     *            the {@code id_import_batch}
     * @param plugin
     *            the plugin
     */
    void deleteAppointmentsByBatch( int nBatchId, Plugin plugin );
}
