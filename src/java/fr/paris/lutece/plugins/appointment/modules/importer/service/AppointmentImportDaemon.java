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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;

/** Daemon which creates the appointments. */
public final class AppointmentImportDaemon extends Daemon
{
    private final AppointmentServiceImporter _importer = new AppointmentServiceImporter( );

    @Override
    public synchronized void run( )
    {
        for ( AppointmentImportBatch batch : AppointmentImportHome.findPendingBatches( ) )
        {
            process( batch );
        }
    }

    /**
     * Prepares the appointment slot, then creates one appointment for one row.
     * If slot preparation fails every pending row is immediately marked {@code ERROR} and the batch is closed.
     * Each row is processed independently; a failure on one row does not abort the others.
     * The batch status is set to {@code COMPLETED} or {@code COMPLETED_WITH_ERRORS} when all rows are done,
     * then the parent file status is updated accordingly.
     *
     * @param batch the batch to process; its status must be {@code PENDING}
     */
    private void process( AppointmentImportBatch batch )
    {
        AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), AppointmentImportStatus.PROCESSING );
        List<Slot> listSlots;
        try
        {
            listSlots = _importer.prepareSlots( batch.getIdForm( ), batch.getStartingDateTime( ), batch.getEndingDateTime( ) );
        }
        catch( AppointmentImportException e )
        {
            for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.PENDING ) )
            {
                AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), e.getCode( ), e.getMessage( ) );
            }
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; code=" + e.getCode( ) + "; " + e.getMessage( ), e );
            AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );
            updateFileStatus( batch.getIdImportFile( ) );
            return;
        }
        catch( RuntimeException e )
        {
            String strMessage = StringUtils.defaultIfBlank( e.getMessage( ), e.getClass( ).getSimpleName( ) );
            for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.PENDING ) )
            {
                AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), AppointmentImportException.SAVE_FAILED, strMessage );
            }
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; unexpected error; " + strMessage, e );
            AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );
            updateFileStatus( batch.getIdImportFile( ) );
            return;
        }
        for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.PENDING ) )
        {
            try
            {
                Map<String, String> mapGeneric = AppointmentImportJsonService.readMap( row.getGenericAttributesJson( ) );
                // Merge form-specific fields (extra generic attributes) so they are saved as responses
                Map<String, String> mapFormFields = AppointmentImportJsonService.readMap( row.getFormFieldsJson( ) );
                Map<String, String> mapAll = new java.util.LinkedHashMap<>( mapGeneric );
                mapAll.putAll( mapFormFields );
                int nAppointmentId = _importer.importAppointment( batch.getIdForm( ), mapAll, listSlots );
                AppointmentImportHome.markAppointmentCreated( row.getIdImportAppointment( ), nAppointmentId );
            }
            catch( AppointmentImportException e )
            {
                AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), e.getCode( ), e.getMessage( ) );
                AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; line=" + row.getSourceLineNumber( ) + "; code=" + e.getCode( ) + "; " + e.getMessage( ), e );
            }
            catch( RuntimeException e )
            {
                String strMessage = StringUtils.defaultIfBlank( e.getMessage( ), e.getClass( ).getSimpleName( ) );
                AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), AppointmentImportException.SAVE_FAILED, strMessage );
                AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; line=" + row.getSourceLineNumber( ) + "; unexpected error; " + strMessage, e );
            }
        }
        String strFinalStatus = AppointmentImportHome.batchHasErrors( batch.getIdImportBatch( ) )
                ? AppointmentImportStatus.COMPLETED_WITH_ERRORS
                : AppointmentImportStatus.COMPLETED;
        AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), strFinalStatus );
        updateFileStatus( batch.getIdImportFile( ) );
    }

    /**
     * Update the status of the file after an import.
     * The file status is updated only when all its batches have left the {@code PENDING} / {@code PROCESSING} state.
     * It becomes {@code COMPLETED_WITH_ERRORS} if any appointment row failed, or {@code COMPLETED} otherwise.
     *
     * @param nFileId the {@code id_import_file} of the file whose status may need updating
     */
    private void updateFileStatus( int nFileId )
    {
        List<AppointmentImportBatch> listBatches = AppointmentImportHome.findBatchesByFile( nFileId );
        boolean bPending = listBatches.stream( ).anyMatch(
                b -> AppointmentImportStatus.PENDING.equals( b.getStatus( ) ) || AppointmentImportStatus.PROCESSING.equals( b.getStatus( ) ) );
        if ( !bPending )
        {
            String strFileStatus = AppointmentImportHome.fileHasErrors( nFileId )
                    ? AppointmentImportStatus.COMPLETED_WITH_ERRORS
                    : AppointmentImportStatus.COMPLETED;
            AppointmentImportHome.updateFileStatus( nFileId, strFileStatus );
        }
    }
}
