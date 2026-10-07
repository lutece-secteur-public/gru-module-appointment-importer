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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * Daemon which creates the appointments.
 * <p>
 * A batch is taken atomically before being processed, so that several instances of the webapp never process the same batch. A batch left in
 * {@code PROCESSING} for longer than {@code appointment-importer.processing.timeoutMinutes} (the instance stopped while processing it) is taken over;
 * the row that was being saved at that moment is put in error, since its appointment may have been created.
 * </p>
 */
public final class AppointmentImportDaemon extends Daemon
{
    private static final String PROPERTY_PROCESSING_TIMEOUT = "appointment-importer.processing.timeoutMinutes";
    private static final int DEFAULT_PROCESSING_TIMEOUT = 30;
    private static final String MESSAGE_INTERRUPTED = "module.appointment.importer.error.import.interrupted";

    private final AppointmentServiceImporter _importer = new AppointmentServiceImporter( );

    @Override
    public synchronized void run( )
    {
        String strToken = UUID.randomUUID( ).toString( );
        LocalDateTime dtStaleBefore = LocalDateTime.now( ).minusMinutes( AppPropertiesService.getPropertyInt( PROPERTY_PROCESSING_TIMEOUT,
                DEFAULT_PROCESSING_TIMEOUT ) );
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByStatus( AppointmentImportStatus.PROCESSING ) )
        {
            if ( AppointmentImportHome.claimStaleBatch( batch.getIdImportBatch( ), dtStaleBefore, strToken ) )
            {
                AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + " was abandoned while processing, taking it over" );
                interruptProcessingRows( batch );
                process( batch );
            }
        }
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByStatus( AppointmentImportStatus.PENDING ) )
        {
            if ( AppointmentImportHome.claimPendingBatch( batch.getIdImportBatch( ), strToken ) )
            {
                process( batch );
            }
        }
    }

    /**
     * Puts in error the rows that were being saved when the processing of the batch stopped: their appointment may exist, they must be checked by hand
     * rather than created twice.
     *
     * @param batch the abandoned batch
     */
    private void interruptProcessingRows( AppointmentImportBatch batch )
    {
        for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ),
                AppointmentImportStatus.PROCESSING ) )
        {
            AppointmentImportException e = new AppointmentImportException( AppointmentImportException.INTERRUPTED, MESSAGE_INTERRUPTED );
            AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), e.getCode( ), e.getMessage( ) );
        }
    }

    /**
     * Prepares the appointment slot, then creates one appointment for each pending row.
     * If slot preparation fails every pending row is immediately marked {@code ERROR} and the batch is closed.
     * Each row is processed independently; a failure on one row does not abort the others.
     * The batch status is set to {@code COMPLETED} or {@code COMPLETED_WITH_ERRORS} when all rows are done,
     * then the parent file status is updated accordingly.
     *
     * @param batch the batch to process, taken by this daemon
     */
    private void process( AppointmentImportBatch batch )
    {
        List<Slot> listSlots;
        AppointmentFormEntries formEntries;
        try
        {
            listSlots = _importer.prepareSlots( batch.getIdForm( ), batch.getStartingDateTime( ), batch.getEndingDateTime( ) );
            formEntries = AppointmentFormEntries.load( batch.getIdForm( ), ImportColumns.fromProperties( ) );
        }
        catch( AppointmentImportException e )
        {
            failPendingRows( batch, e.getCode( ), e.getMessage( ) );
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; code=" + e.getCode( ) + "; " + e.getMessage( ), e );
            closeBatch( batch, AppointmentImportStatus.COMPLETED_WITH_ERRORS );
            return;
        }
        catch( RuntimeException e )
        {
            String strMessage = StringUtils.defaultIfBlank( e.getMessage( ), e.getClass( ).getSimpleName( ) );
            failPendingRows( batch, AppointmentImportException.SAVE_FAILED, strMessage );
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; unexpected error; " + strMessage, e );
            closeBatch( batch, AppointmentImportStatus.COMPLETED_WITH_ERRORS );
            return;
        }
        AppointmentImportFile file = AppointmentImportHome.findFile( batch.getIdImportFile( ) );
        String strAdminAccessCode = file == null ? null : file.getAdminAccessCode( );
        for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.PENDING ) )
        {
            processRow( batch, row, strAdminAccessCode, formEntries, listSlots );
            AppointmentImportHome.touchBatch( batch.getIdImportBatch( ) );
        }
        closeBatch( batch, AppointmentImportHome.batchHasErrors( batch.getIdImportBatch( ) ) ? AppointmentImportStatus.COMPLETED_WITH_ERRORS
                : AppointmentImportStatus.COMPLETED );
    }

    /**
     * Creates the appointment of a row and records the outcome.
     *
     * @param batch              the batch of the row
     * @param row                the row
     * @param strAdminAccessCode the administrator who uploaded the file
     * @param formEntries        the fields of the form
     * @param listSlots          the slots of the batch
     */
    private void processRow( AppointmentImportBatch batch, AppointmentImportAppointment row, String strAdminAccessCode,
            AppointmentFormEntries formEntries, List<Slot> listSlots )
    {
        AppointmentImportHome.markAppointmentProcessing( row.getIdImportAppointment( ) );
        try
        {
            Map<String, String> mapGeneric = new LinkedHashMap<>( AppointmentImportJsonService.readMap( row.getGenericAttributesJson( ) ) );
            Map<String, String> mapFormFields = AppointmentImportJsonService.readMap( row.getFormFieldsJson( ) );
            int nAppointmentId = _importer.importAppointment( batch.getIdForm( ), strAdminAccessCode, mapGeneric, mapFormFields, formEntries, listSlots );
            AppointmentImportHome.markAppointmentCreated( row.getIdImportAppointment( ), nAppointmentId );
        }
        catch( AppointmentImportException e )
        {
            AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), e.getCode( ), e.getMessage( ) );
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; line=" + row.getSourceLineNumber( ) + "; code=" + e.getCode( )
                    + "; " + e.getMessage( ), e );
        }
        catch( RuntimeException e )
        {
            String strMessage = StringUtils.defaultIfBlank( e.getMessage( ), e.getClass( ).getSimpleName( ) );
            AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), AppointmentImportException.SAVE_FAILED, strMessage );
            AppLogService.error( "Appointment import: batch=" + batch.getIdImportBatch( ) + "; line=" + row.getSourceLineNumber( ) + "; unexpected error; "
                    + strMessage, e );
        }
    }

    /**
     * Puts every pending row of a batch in error.
     *
     * @param batch      the batch
     * @param strCode    the error code
     * @param strMessage the error message
     */
    private void failPendingRows( AppointmentImportBatch batch, String strCode, String strMessage )
    {
        for ( AppointmentImportAppointment row : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.PENDING ) )
        {
            AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), strCode, strMessage );
        }
    }

    /**
     * Sets the final status of a batch, then of its file once all its batches are done.
     *
     * @param batch     the batch
     * @param strStatus the final status of the batch
     */
    private void closeBatch( AppointmentImportBatch batch, String strStatus )
    {
        AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), strStatus );
        List<AppointmentImportBatch> listBatches = AppointmentImportHome.findBatchesByFile( batch.getIdImportFile( ) );
        boolean bPending = listBatches.stream( ).anyMatch(
                b -> AppointmentImportStatus.PENDING.equals( b.getStatus( ) ) || AppointmentImportStatus.PROCESSING.equals( b.getStatus( ) ) );
        if ( !bPending )
        {
            AppointmentImportHome.updateFileStatus( batch.getIdImportFile( ),
                    AppointmentImportHome.fileHasErrors( batch.getIdImportFile( ) ) ? AppointmentImportStatus.COMPLETED_WITH_ERRORS
                            : AppointmentImportStatus.COMPLETED );
        }
    }
}
