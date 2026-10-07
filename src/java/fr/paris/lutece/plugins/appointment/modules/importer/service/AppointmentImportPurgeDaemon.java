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
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * Purges the personal data of the imports older than the configured retention period.
 * <ul>
 * <li>A completed batch loses its rows and is kept with status ARCHIVED; when all the batches of a file are archived, the file is archived.</li>
 * <li>A file rejected at validation loses its validation report, which quotes the rows, and is archived.</li>
 * </ul>
 */
public final class AppointmentImportPurgeDaemon extends Daemon
{
    private static final String PROPERTY_RETENTION_DAYS = "appointment-importer.purge.retentionDays";
    private static final int DEFAULT_RETENTION_DAYS = 90;

    @Override
    public synchronized void run( )
    {
        int nRetentionDays = AppPropertiesService.getPropertyInt( PROPERTY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS );
        LocalDateTime dtThreshold = LocalDateTime.now( ).minusDays( nRetentionDays );
        int nBatches = 0;
        int nFiles = 0;
        int nErrors = 0;
        for ( int nBatchId : AppointmentImportHome.findBatchIdsToPurge( dtThreshold ) )
        {
            if ( purgeBatch( nBatchId ) )
            {
                nBatches++;
            }
            else
            {
                nErrors++;
            }
        }
        List<Integer> listRejectedFileIds = AppointmentImportHome.findRejectedFileIdsToPurge( dtThreshold );
        for ( int nFileId : listRejectedFileIds )
        {
            AppointmentImportHome.archiveFile( nFileId );
            nFiles++;
        }
        setLastRunLogs( "Appointment import purge: " + nBatches + " batch(es) and " + nFiles + " rejected file(s) archived, " + nErrors
                + " error(s) (retention: " + nRetentionDays + " days)" );
    }

    /**
     * Deletes appointment rows for the given batch, archives the batch, then archives the parent file if all its batches are archived.
     *
     * @param nBatchId the {@code id_import_batch} to purge
     * @return true if the batch was successfully archived, false on error
     */
    private boolean purgeBatch( int nBatchId )
    {
        try
        {
            AppointmentImportBatch batch = AppointmentImportHome.findBatch( nBatchId );
            if ( batch == null )
            {
                return false;
            }
            AppointmentImportHome.purgeBatch( nBatchId );
            if ( AppointmentImportHome.fileAllBatchesArchived( batch.getIdImportFile( ) ) )
            {
                AppointmentImportHome.archiveFile( batch.getIdImportFile( ) );
            }
            return true;
        }
        catch( RuntimeException e )
        {
            AppLogService.error( "Appointment import purge: error purging batch " + nBatchId, e );
            return false;
        }
    }
}
