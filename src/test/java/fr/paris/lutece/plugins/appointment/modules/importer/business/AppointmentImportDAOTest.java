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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.paris.lutece.test.LuteceTestCase;

import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.util.sql.DAOUtil;

/**
 * Tests the SQL of the module on the database of the tests.
 */
public class AppointmentImportDAOTest extends LuteceTestCase
{
    private static final int FORM_ID = 9001;
    private static final LocalDateTime SLOT_START = LocalDateTime.of( 2030, 3, 4, 10, 0 );

    @Test
    public void testFileAndCounts( )
    {
        int nFileId = createFile( AppointmentImportStatus.PENDING, "hash-counts" );
        int nBatchId = createBatch( nFileId, AppointmentImportStatus.PENDING );
        createRows( nBatchId, AppointmentImportStatus.CREATED, AppointmentImportStatus.CREATED, AppointmentImportStatus.ERROR,
                AppointmentImportStatus.PENDING );

        AppointmentImportFile file = AppointmentImportHome.findFile( nFileId );
        assertEquals( "admin", file.getAdminAccessCode( ) );
        assertTrue( AppointmentImportHome.existsDuplicateFile( "hash-counts", FORM_ID ) );
        assertTrue( AppointmentImportHome.findFileNames( Collections.singletonList( FORM_ID ) ).contains( "file-" + nFileId + ".xlsx" ) );

        List<AppointmentImportFile> listFiles = AppointmentImportHome.findFilesByIds( Collections.singletonList( nFileId ) );
        AppointmentImportHome.fillCounts( listFiles );
        ImportCounts counts = listFiles.get( 0 ).getCounts( );
        assertEquals( 2, counts.getCreated( ) );
        assertEquals( 1, counts.getErrors( ) );
        assertEquals( 1, counts.getPending( ) );
        assertEquals( 4, counts.getTotal( ) );

        List<AppointmentImportBatch> listBatches = AppointmentImportHome.findBatchesByIds( Collections.singletonList( nBatchId ) );
        AppointmentImportHome.fillBatchCounts( listBatches );
        assertEquals( 2, listBatches.get( 0 ).getCounts( ).getCreated( ) );
        assertEquals( 3, AppointmentImportHome.countProcessedAppointmentsByBatch( nBatchId ) );
    }

    @Test
    public void testFiltersOfTheResults( )
    {
        int nFileId = createFile( AppointmentImportStatus.PENDING, "hash-filters" );
        int nBatchId = createBatch( nFileId, AppointmentImportStatus.COMPLETED );
        List<Integer> listForms = Collections.singletonList( FORM_ID );

        assertTrue( AppointmentImportHome.findBatchIds( listForms, Integer.toString( FORM_ID ), AppointmentImportStatus.COMPLETED,
                "file-" + nFileId + ".xlsx", SLOT_START.toLocalDate( ).toString( ) ).contains( nBatchId ) );
        assertFalse( AppointmentImportHome.findBatchIds( listForms, null, null, null, SLOT_START.toLocalDate( ).plusDays( 1 ).toString( ) )
                .contains( nBatchId ) );
        assertTrue( AppointmentImportHome.findFileIds( listForms, null, "file-" + nFileId + ".xlsx", null, null ).contains( nFileId ) );
        // The files are filtered on the form and on the date of their slots, as the batches
        assertTrue( AppointmentImportHome.findFileIds( listForms, Integer.toString( FORM_ID ), null, null, SLOT_START.toLocalDate( ).toString( ) )
                .contains( nFileId ) );
        assertFalse( AppointmentImportHome.findFileIds( listForms, null, null, null, SLOT_START.toLocalDate( ).plusDays( 1 ).toString( ) )
                .contains( nFileId ) );
        assertTrue( AppointmentImportHome.findFileIds( Arrays.asList( FORM_ID, FORM_ID + 1 ), Integer.toString( FORM_ID + 1 ), null, null, null )
                .isEmpty( ) );
        assertTrue( AppointmentImportHome.findBatchIds( Collections.singletonList( FORM_ID + 1 ), null, null, null, null ).isEmpty( ) );
    }

    @Test
    public void testPendingBatchIsTakenOnce( )
    {
        int nBatchId = createBatch( createFile( AppointmentImportStatus.PENDING, "hash-claim" ), AppointmentImportStatus.PENDING );

        assertTrue( AppointmentImportHome.claimPendingBatch( nBatchId, "instance-1" ) );
        assertFalse( AppointmentImportHome.claimPendingBatch( nBatchId, "instance-2" ) );
        assertEquals( AppointmentImportStatus.PROCESSING, AppointmentImportHome.findBatch( nBatchId ).getStatus( ) );
    }

    @Test
    public void testAbandonedBatchIsTakenOver( )
    {
        int nBatchId = createBatch( createFile( AppointmentImportStatus.PENDING, "hash-stale" ), AppointmentImportStatus.PENDING );
        assertTrue( AppointmentImportHome.claimPendingBatch( nBatchId, "instance-1" ) );
        setLastExecDate( "appointment_import_batch", "id_import_batch", nBatchId, LocalDateTime.now( ).minusHours( 1 ) );

        // Still touched recently for a threshold one hour and a half ago: not abandoned
        assertFalse( AppointmentImportHome.claimStaleBatch( nBatchId, LocalDateTime.now( ).minusMinutes( 90 ), "instance-2" ) );
        assertTrue( AppointmentImportHome.claimStaleBatch( nBatchId, LocalDateTime.now( ).minusMinutes( 30 ), "instance-2" ) );
        assertFalse( AppointmentImportHome.claimStaleBatch( nBatchId, LocalDateTime.now( ).minusMinutes( 30 ), "instance-3" ) );
    }

    @Test
    public void testRequeue( )
    {
        int nFileId = createFile( AppointmentImportStatus.COMPLETED_WITH_ERRORS, "hash-requeue" );
        int nBatchId = createBatch( nFileId, AppointmentImportStatus.PROCESSING );
        List<Integer> listRows = createRows( nBatchId, AppointmentImportStatus.CREATED, AppointmentImportStatus.ERROR, AppointmentImportStatus.ERROR );
        AppointmentImportHome.markAppointmentError( listRows.get( 1 ), "SLOT_FULL", "full" );
        AppointmentImportHome.markAppointmentError( listRows.get( 2 ), "INTERRUPTED", "interrupted" );

        // A batch being processed is left to the daemon
        assertFalse( AppointmentImportHome.requeueBatch( nBatchId ) );
        AppointmentImportHome.updateBatchStatus( nBatchId, AppointmentImportStatus.COMPLETED_WITH_ERRORS );

        AppointmentImportHome.requeueErrorRows( nBatchId, "INTERRUPTED" );
        assertTrue( AppointmentImportHome.requeueBatch( nBatchId ) );
        assertEquals( AppointmentImportStatus.CREATED, AppointmentImportHome.findAppointment( listRows.get( 0 ) ).getStatus( ) );
        AppointmentImportAppointment requeued = AppointmentImportHome.findAppointment( listRows.get( 1 ) );
        assertEquals( AppointmentImportStatus.PENDING, requeued.getStatus( ) );
        assertNull( requeued.getErrorCode( ) );
        assertEquals( AppointmentImportStatus.ERROR, AppointmentImportHome.findAppointment( listRows.get( 2 ) ).getStatus( ) );

        AppointmentImportHome.requeueRow( listRows.get( 2 ) );
        assertEquals( AppointmentImportStatus.PENDING, AppointmentImportHome.findAppointment( listRows.get( 2 ) ).getStatus( ) );

        AppointmentImportHome.updateAppointmentData( listRows.get( 2 ), "{\"lastName\":\"MARTIN\"}", "{}" );
        assertEquals( "{\"lastName\":\"MARTIN\"}", AppointmentImportHome.findAppointment( listRows.get( 2 ) ).getGenericAttributesJson( ) );
    }

    @Test
    public void testFilesToClose( )
    {
        int nDoneFileId = createFile( AppointmentImportStatus.PENDING, "hash-close-1" );
        createBatch( nDoneFileId, AppointmentImportStatus.COMPLETED );
        createBatch( nDoneFileId, AppointmentImportStatus.COMPLETED_WITH_ERRORS );
        int nRunningFileId = createFile( AppointmentImportStatus.PENDING, "hash-close-2" );
        createBatch( nRunningFileId, AppointmentImportStatus.COMPLETED );
        createBatch( nRunningFileId, AppointmentImportStatus.PROCESSING );

        List<Integer> listFileIds = AppointmentImportHome.findFileIdsToClose( );
        assertTrue( listFileIds.contains( nDoneFileId ) );
        assertFalse( listFileIds.contains( nRunningFileId ) );
    }

    @Test
    public void testPurge( )
    {
        int nFileId = createFile( AppointmentImportStatus.COMPLETED, "hash-purge" );
        int nBatchId = createBatch( nFileId, AppointmentImportStatus.COMPLETED );
        createRows( nBatchId, AppointmentImportStatus.CREATED );
        setLastExecDate( "appointment_import_batch", "id_import_batch", nBatchId, LocalDateTime.now( ).minusDays( 100 ) );

        assertTrue( AppointmentImportHome.findBatchIdsToPurge( LocalDateTime.now( ).minusDays( 90 ) ).contains( nBatchId ) );
        assertTrue( AppointmentImportHome.purgeBatch( nBatchId ) );
        assertTrue( AppointmentImportHome.findAppointmentsByBatch( nBatchId, null ).isEmpty( ) );
        assertEquals( AppointmentImportStatus.ARCHIVED, AppointmentImportHome.findBatch( nBatchId ).getStatus( ) );
        assertTrue( AppointmentImportHome.fileAllBatchesArchived( nFileId ) );
        AppointmentImportHome.archiveFile( nFileId );
        assertEquals( AppointmentImportStatus.ARCHIVED, AppointmentImportHome.findFile( nFileId ).getStatus( ) );
        // An accepted file keeps its hash: the same content is still refused
        assertTrue( AppointmentImportHome.existsDuplicateFile( "hash-purge", FORM_ID ) );

        int nRejectedId = createFile( AppointmentImportStatus.VALIDATION_FAILED, "hash-rejected" );
        assertTrue( AppointmentImportHome.findRejectedFileIdsToPurge( LocalDateTime.now( ).plusMinutes( 1 ) ).contains( nRejectedId ) );
        AppointmentImportHome.archiveFile( nRejectedId );
        AppointmentImportFile rejected = AppointmentImportHome.findFile( nRejectedId );
        assertNull( rejected.getValidationReport( ) );
        assertNull( rejected.getFileHash( ) );
    }

    @Test
    public void testStatusFilterOfTheFiles( )
    {
        List<Integer> listForms = Collections.singletonList( FORM_ID );
        int nRunningFileId = createFile( AppointmentImportStatus.PENDING, "hash-status-1" );
        createBatch( nRunningFileId, AppointmentImportStatus.PROCESSING );
        int nWaitingFileId = createFile( AppointmentImportStatus.PENDING, "hash-status-2" );
        createBatch( nWaitingFileId, AppointmentImportStatus.PENDING );
        int nRejectedFileId = createFile( AppointmentImportStatus.VALIDATION_FAILED, "hash-status-3" );

        List<Integer> listProcessing = AppointmentImportHome.findFileIds( listForms, null, null, AppointmentImportStatus.PROCESSING, null );
        assertTrue( listProcessing.contains( nRunningFileId ) );
        assertFalse( listProcessing.contains( nWaitingFileId ) );
        assertTrue( AppointmentImportHome.findFileIds( listForms, null, null, AppointmentImportStatus.VALIDATION_FAILED, null ).contains( nRejectedFileId ) );
    }

    @Test
    public void testRetriedBatchIsNotPurged( )
    {
        int nBatchId = createBatch( createFile( AppointmentImportStatus.PENDING, "hash-purge-retried" ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );
        createRows( nBatchId, AppointmentImportStatus.ERROR );
        // Selected by the purge, then retried before being purged
        assertTrue( AppointmentImportHome.requeueBatch( nBatchId ) );

        assertFalse( AppointmentImportHome.purgeBatch( nBatchId ) );
        assertEquals( AppointmentImportStatus.PENDING, AppointmentImportHome.findBatch( nBatchId ).getStatus( ) );
        assertEquals( 1, AppointmentImportHome.findAppointmentsByBatch( nBatchId, null ).size( ) );
    }

    private static int createFile( String strStatus, String strHash )
    {
        AppointmentImportFile file = new AppointmentImportFile( );
        file.setImportFileName( "file.xlsx" );
        file.setIdForm( FORM_ID );
        file.setAdminAccessCode( "admin" );
        file.setStatus( strStatus );
        file.setFileHash( strHash + "-" + System.nanoTime( ) );
        file.setValidationReport( "[]" );
        file.setCreationDate( LocalDateTime.now( ) );
        int nId = AppointmentImportHome.createFile( file );
        // Rename the file and fix its hash once its id is known, so that each test finds its own
        try ( DAOUtil daoUtil = new DAOUtil( "UPDATE appointment_import_file SET import_file_name = ?, file_hash = ? WHERE id_import_file = ?", plugin( ) ) )
        {
            daoUtil.setString( 1, "file-" + nId + ".xlsx" );
            daoUtil.setString( 2, strHash );
            daoUtil.setInt( 3, nId );
            daoUtil.executeUpdate( );
        }
        return nId;
    }

    private static int createBatch( int nFileId, String strStatus )
    {
        AppointmentImportBatch batch = new AppointmentImportBatch( );
        batch.setIdImportFile( nFileId );
        batch.setImportFileName( "file-" + nFileId + ".xlsx" );
        batch.setIdForm( FORM_ID );
        batch.setStartingDateTime( SLOT_START );
        batch.setEndingDateTime( SLOT_START.plusMinutes( 30 ) );
        batch.setStatus( strStatus );
        batch.setCreationDate( LocalDateTime.now( ) );
        return AppointmentImportHome.createBatch( batch );
    }

    private static List<Integer> createRows( int nBatchId, String... statuses )
    {
        List<AppointmentImportAppointment> listRows = new ArrayList<>( );
        int nLine = 2;
        for ( String strStatus : statuses )
        {
            AppointmentImportAppointment row = new AppointmentImportAppointment( );
            row.setIdImportBatch( nBatchId );
            row.setSourceLineNumber( nLine++ );
            row.setGenericAttributesJson( "{}" );
            row.setFormFieldsJson( "{}" );
            row.setStatus( strStatus );
            row.setCreationDate( LocalDateTime.now( ) );
            listRows.add( row );
        }
        AppointmentImportHome.createAppointments( listRows );
        List<Integer> listIds = new ArrayList<>( );
        AppointmentImportHome.findAppointmentsByBatch( nBatchId, null ).forEach( row -> listIds.add( row.getIdImportAppointment( ) ) );
        assertEquals( Arrays.asList( statuses ).size( ), listIds.size( ) );
        return listIds;
    }

    private static void setLastExecDate( String strTable, String strIdColumn, int nId, LocalDateTime dtLastExec )
    {
        try ( DAOUtil daoUtil = new DAOUtil( "UPDATE " + strTable + " SET last_exec_date = ? WHERE " + strIdColumn + " = ?", plugin( ) ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( dtLastExec ) );
            daoUtil.setInt( 2, nId );
            daoUtil.executeUpdate( );
        }
    }

    private static fr.paris.lutece.portal.service.plugin.Plugin plugin( )
    {
        return PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME );
    }
}
