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

import java.time.LocalDateTime;
import java.util.List;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * Purges completed import batches older than the configured retention period.
 * Client data (appointments) is deleted; the batch record is kept with status ARCHIVED.
 * When all batches of a file are archived, the file record is archived.
 */
public final class AppointmentImportPurgeDaemon extends Daemon
{
    private static final String PROPERTY_RETENTION_DAYS = "appointment-importer.purge.retentionDays";
    private static final int DEFAULT_RETENTION_DAYS = 90;

    /**
     * Finds all completed batches older than the configured retention period and purges them one by one.
     */
    @Override
    public synchronized void run( )
    {
        int nRetentionDays = AppPropertiesService.getPropertyInt( PROPERTY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS );
        LocalDateTime dtThreshold = LocalDateTime.now( ).minusDays( nRetentionDays );
        List<Integer> listBatchIds = AppointmentImportHome.findBatchIdsToPurge( dtThreshold );
        if ( listBatchIds.isEmpty( ) )
        {
            AppLogService.info( "Appointment import purge: nothing to purge (retention: " + nRetentionDays + " days)" );
            return;
        }
        int nSuccess = 0;
        int nErrors = 0;
        for ( int nBatchId : listBatchIds )
        {
            if ( purge( nBatchId ) )
            {
                nSuccess++;
            }
            else
            {
                nErrors++;
            }
        }
        AppLogService.info( "Appointment import purge: " + nSuccess + " batch(es) archived, "
                + nErrors + " error(s) (retention: " + nRetentionDays + " days)" );
    }

    /**
     * Deletes appointment rows for the given batch, archives the batch, then checks if the parent file can be archived too.
     *
     * @param nBatchId the {@code id_import_batch} to purge
     * @return true if the batch was successfully archived, false on error
     */
    private boolean purge( int nBatchId )
    {
        try
        {
            AppointmentImportBatch batch = AppointmentImportHome.findBatch( nBatchId );
            if ( batch == null )
            {
                return false;
            }
            AppointmentImportHome.purgeAppointmentsByBatch( nBatchId );
            AppointmentImportHome.archiveBatch( nBatchId );
            archiveFileIfComplete( batch.getIdImportFile( ) );
            return true;
        }
        catch( RuntimeException e )
        {
            AppLogService.error( "Appointment import purge: error purging batch " + nBatchId, e );
            return false;
        }
    }

    /**
     * Archives the file record if all its batches are already archived.
     *
     * @param nFileId the {@code id_import_file} to check and potentially archive
     */
    private void archiveFileIfComplete( int nFileId )
    {
        if ( !AppointmentImportHome.fileAllBatchesArchived( nFileId ) )
        {
            return;
        }
        AppointmentImportHome.archiveFile( nFileId );
    }
}
