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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.modules.importer.AbstractLuteceIntegrationTest;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.UserService;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.portal.service.image.ImageResource;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.util.sql.DAOUtil;

/**
 * Imports workbooks into a real appointment form and runs the daemon, on the database of the tests.
 */
public class AppointmentImportDaemonTest extends AbstractLuteceIntegrationTest
{
    private static final List<String> HEADERS = ImportTestUtils.HEADERS.subList( 0, 8 );
    private static final LocalTime TEN = LocalTime.of( 10, 0 );
    private static final LocalTime ELEVEN = LocalTime.of( 11, 0 );

    private static int _nFormId;
    private static LocalDate _monday;

    private final AppointmentImportService _importService = new AppointmentImportService( );

    @BeforeClass
    public static void createForm( )
    {
        AppointmentFormDTO form = new AppointmentFormDTO( );
        form.setName( "import" );
        form.setTitle( "Import test " + System.nanoTime( ) );
        form.setColor( "gray" );
        form.setDescription( "Import test" );
        form.setDescriptionRule( "Import test" );
        form.setReference( "IMPORT" );
        form.setTimeStart( "09:00" );
        form.setTimeEnd( "18:00" );
        form.setDurationAppointments( 30 );
        form.setMinTimeBeforeAppointment( 0 );
        form.setMaxCapacityPerSlot( 3 );
        form.setMaxPeoplePerAppointment( 1 );
        form.setDateStartValidity( Date.valueOf( LocalDate.now( ) ) );
        form.setDateEndValidity( Date.valueOf( LocalDate.now( ).plusDays( 120 ) ) );
        form.setIsOpenMonday( true );
        form.setIsOpenTuesday( true );
        form.setIsOpenWednesday( true );
        form.setIsOpenThursday( true );
        form.setIsOpenFriday( true );
        form.setIsOpenSaturday( false );
        form.setIsOpenSunday( false );
        form.setDisplayTitleFo( true );
        form.setNbWeeksToDisplay( 3 );
        ImageResource icon = new ImageResource( );
        icon.setImage( new byte [ 0] );
        icon.setMimeType( "image/png" );
        form.setIcon( icon );
        // The calendar template created by the init script of the appointment plugin
        form.setCalendarTemplateId( 1 );
        form.setIsActive( true );
        _nFormId = FormService.createAppointmentForm( form );
        _monday = LocalDate.now( ).plusDays( 14 ).with( TemporalAdjusters.next( DayOfWeek.MONDAY ) );
    }

    @Test
    public void testImportCreatesAppointmentsAndReportsEachSlotError( ) throws IOException
    {
        AppointmentImportFile file = register( "slots.xlsx", row( "DUPONT", "marie.dupont@paris.fr", _monday, TEN, ELEVEN ),
                row( "DURAND", "paul.durand@paris.fr", _monday, TEN, ELEVEN ),
                row( "PETIT", "jean.petit@paris.fr", _monday.plusDays( 5 ), TEN, LocalTime.of( 10, 30 ) ),
                row( "ROUX", "anne.roux@paris.fr", _monday, LocalTime.of( 10, 15 ), LocalTime.of( 10, 45 ) ),
                row( "LEROY", "marc.leroy@paris.fr", _monday, LocalTime.of( 18, 0 ), LocalTime.of( 18, 30 ) ) );
        assertEquals( AppointmentImportStatus.PENDING, file.getStatus( ) );

        new AppointmentImportDaemon( ).run( );

        assertEquals( AppointmentImportStatus.COMPLETED_WITH_ERRORS, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
        Map<Integer, AppointmentImportAppointment> mapRows = rowsByLine( file );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 2 ).getStatus( ) );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 3 ).getStatus( ) );
        // A Saturday, closed in the form
        assertEquals( "SLOT_CLOSED", mapRows.get( 4 ).getErrorCode( ) );
        // 10:15 is inside the slot 10:00 - 10:30
        assertEquals( "SLOT_NOT_ALIGNED", mapRows.get( 5 ).getErrorCode( ) );
        assertTrue( mapRows.get( 5 ).getErrorMessage( ), mapRows.get( 5 ).getErrorMessage( ).contains( "10:00 – 10:30" ) );
        // After the closing time of 18:00
        assertEquals( "SLOT_NOT_FOUND", mapRows.get( 6 ).getErrorCode( ) );

        Appointment appointment = AppointmentService.findAppointmentById( mapRows.get( 2 ).getIdAppointment( ) );
        assertEquals( "admin", appointment.getAdminUserCreate( ) );
        assertEquals( "marie.dupont@paris.fr", UserService.findUserById( appointment.getIdUser( ) ).getEmail( ) );
        // Two appointments of one hour on slots of 30 minutes taking 3 people: 1 place left on each slot
        List<Slot> listSlots = SlotService.findSlotsByIdFormAndDateRange( _nFormId, _monday.atTime( TEN ), _monday.atTime( ELEVEN ) );
        assertEquals( 2, listSlots.size( ) );
        listSlots.forEach( slot -> assertEquals( 1, slot.getNbRemainingPlaces( ) ) );

        List<String> listReport = reportCells( AppointmentImportReportService.finalReport( file.getIdImportFile( ), Locale.FRANCE ) );
        assertTrue( listReport.contains( "DUPONT" ) );
        assertTrue( listReport.contains( "marie.dupont@paris.fr" ) );
        assertTrue( listReport.contains( appointment.getReference( ) ) );
        List<String> listFailed = reportCells( AppointmentImportReportService.failedRowsWorkbook( file.getIdImportFile( ), Locale.FRANCE ) );
        assertTrue( listFailed.contains( "PETIT" ) );
        assertFalse( listFailed.contains( "DUPONT" ) );
    }

    @Test
    public void testRowsInErrorAreCorrectedAndRetried( ) throws IOException
    {
        LocalDate tuesday = _monday.plusDays( 1 );
        AppointmentImportFile file = register( "retry.xlsx", row( "MOREAU", "julie.moreau@paris.fr", tuesday, TEN, ELEVEN ),
                row( "SIMON", "luc.simon@paris.fr", tuesday, TEN, ELEVEN ) );
        setFormActive( false );
        new AppointmentImportDaemon( ).run( );
        Map<Integer, AppointmentImportAppointment> mapRows = rowsByLine( file );
        assertEquals( "FORM_INACTIVE", mapRows.get( 2 ).getErrorCode( ) );
        assertEquals( "FORM_INACTIVE", mapRows.get( 3 ).getErrorCode( ) );
        setFormActive( true );

        // A wrong correction is refused and leaves the row as it is
        Map<String, String> mapGeneric = AppointmentImportJsonService.readMap( mapRows.get( 2 ).getGenericAttributesJson( ) );
        mapGeneric.put( "email", "pas-un-email" );
        List<AppointmentValidationError> listErrors = AppointmentImportRetryService.correctRow( mapRows.get( 2 ).getIdImportAppointment( ),
                new LinkedHashMap<>( mapGeneric ), new LinkedHashMap<>( ), Locale.FRANCE );
        assertEquals( 1, listErrors.size( ) );
        assertEquals( AppointmentImportStatus.ERROR, AppointmentImportHome.findAppointment( mapRows.get( 2 ).getIdImportAppointment( ) ).getStatus( ) );

        mapGeneric.put( "email", "julie.moreau@corrige.fr" );
        assertTrue( AppointmentImportRetryService.correctRow( mapRows.get( 2 ).getIdImportAppointment( ), new LinkedHashMap<>( mapGeneric ),
                new LinkedHashMap<>( ), Locale.FRANCE ).isEmpty( ) );
        // The batch is pending again: the other row can still be retried before the daemon runs
        assertTrue( AppointmentImportRetryService.retryRow( mapRows.get( 3 ).getIdImportAppointment( ) ) );

        new AppointmentImportDaemon( ).run( );

        assertEquals( AppointmentImportStatus.COMPLETED, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
        mapRows = rowsByLine( file );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 2 ).getStatus( ) );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 3 ).getStatus( ) );
        Appointment appointment = AppointmentService.findAppointmentById( mapRows.get( 2 ).getIdAppointment( ) );
        assertEquals( "julie.moreau@corrige.fr", UserService.findUserById( appointment.getIdUser( ) ).getEmail( ) );
    }

    @Test
    public void testRetryFile( ) throws IOException
    {
        LocalDate wednesday = _monday.plusDays( 2 );
        AppointmentImportFile file = register( "retry-file.xlsx", row( "GARCIA", "ana.garcia@paris.fr", wednesday, TEN, ELEVEN ),
                row( "FAURE", "leo.faure@paris.fr", wednesday, LocalTime.of( 14, 0 ), LocalTime.of( 14, 30 ) ) );
        setFormActive( false );
        new AppointmentImportDaemon( ).run( );
        setFormActive( true );
        assertEquals( AppointmentImportStatus.COMPLETED_WITH_ERRORS, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );

        assertEquals( 2, AppointmentImportRetryService.retryFile( file.getIdImportFile( ) ) );
        assertEquals( AppointmentImportStatus.PENDING, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
        new AppointmentImportDaemon( ).run( );

        assertEquals( AppointmentImportStatus.COMPLETED, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
    }

    @Test
    public void testAbandonedBatchIsTakenOverWithoutCreatingTwice( ) throws IOException
    {
        LocalDate thursday = _monday.plusDays( 3 );
        AppointmentImportFile file = register( "abandoned.xlsx", row( "BLANC", "eva.blanc@paris.fr", thursday, TEN, ELEVEN ),
                row( "NOEL", "tom.noel@paris.fr", thursday, TEN, ELEVEN ) );
        AppointmentImportBatch batch = AppointmentImportHome.findBatchesByFile( file.getIdImportFile( ) ).get( 0 );
        // An instance stopped while saving the first row, an hour ago
        assertTrue( AppointmentImportHome.claimPendingBatch( batch.getIdImportBatch( ), "stopped-instance" ) );
        AppointmentImportAppointment first = rowsByLine( file ).get( 2 );
        AppointmentImportHome.markAppointmentProcessing( first.getIdImportAppointment( ) );
        execute( "UPDATE appointment_import_batch SET last_exec_date = ? WHERE id_import_batch = ?", LocalDateTime.now( ).minusHours( 1 ),
                batch.getIdImportBatch( ) );

        new AppointmentImportDaemon( ).run( );

        Map<Integer, AppointmentImportAppointment> mapRows = rowsByLine( file );
        assertEquals( AppointmentImportStatus.ERROR, mapRows.get( 2 ).getStatus( ) );
        assertEquals( AppointmentImportRetryService.INTERRUPTED, mapRows.get( 2 ).getErrorCode( ) );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 3 ).getStatus( ) );
        assertEquals( AppointmentImportStatus.COMPLETED_WITH_ERRORS, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
    }

    @Test
    public void testFileLeftPendingIsClosed( ) throws IOException
    {
        AppointmentImportFile file = register( "left-pending.xlsx", row( "LEFEVRE", "zoe.lefevre@paris.fr", _monday.plusDays( 4 ), TEN, ELEVEN ) );
        new AppointmentImportDaemon( ).run( );
        assertEquals( AppointmentImportStatus.COMPLETED, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
        // Two instances closed the last batches of the file at the same time and both missed it
        AppointmentImportHome.updateFileStatus( file.getIdImportFile( ), AppointmentImportStatus.PENDING );

        new AppointmentImportDaemon( ).run( );

        assertEquals( AppointmentImportStatus.COMPLETED, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );
    }

    @Test
    public void testFullSlotIsReportedAsFull( ) throws IOException
    {
        // The slots take 3 people: the fourth appointment of the same slot is refused by the appointment plugin
        LocalDate friday = _monday.plusDays( 4 );
        LocalTime start = LocalTime.of( 15, 0 );
        LocalTime end = LocalTime.of( 15, 30 );
        AppointmentImportFile file = register( "full.xlsx", row( "UN", "un@paris.fr", friday, start, end ), row( "DEUX", "deux@paris.fr", friday, start, end ),
                row( "TROIS", "trois@paris.fr", friday, start, end ), row( "QUATRE", "quatre@paris.fr", friday, start, end ) );

        new AppointmentImportDaemon( ).run( );

        Map<Integer, AppointmentImportAppointment> mapRows = rowsByLine( file );
        assertEquals( AppointmentImportStatus.CREATED, mapRows.get( 4 ).getStatus( ) );
        assertEquals( "SLOT_FULL", mapRows.get( 5 ).getErrorCode( ) );
    }

    @Test
    public void testRetryAndCorrectionAreRefusedWhileTheBatchIsProcessed( ) throws IOException
    {
        LocalDate thursday = _monday.plusDays( 10 );
        AppointmentImportFile file = register( "refused.xlsx", row( "VIDAL", "vidal@paris.fr", thursday, TEN, ELEVEN ),
                row( "WEBER", "weber@paris.fr", thursday, TEN, ELEVEN ) );
        setFormActive( false );
        new AppointmentImportDaemon( ).run( );
        setFormActive( true );
        Map<Integer, AppointmentImportAppointment> mapRows = rowsByLine( file );
        AppointmentImportBatch batch = AppointmentImportHome.findBatchesByFile( file.getIdImportFile( ) ).get( 0 );
        // Another instance takes the batch again
        assertTrue( AppointmentImportHome.requeueBatch( batch.getIdImportBatch( ) ) );
        assertTrue( AppointmentImportHome.claimPendingBatch( batch.getIdImportBatch( ), "other-instance" ) );

        assertFalse( AppointmentImportRetryService.retryRow( mapRows.get( 2 ).getIdImportAppointment( ) ) );
        Map<String, String> mapGeneric = AppointmentImportJsonService.readMap( mapRows.get( 3 ).getGenericAttributesJson( ) );
        mapGeneric.put( "email", "weber.corrige@paris.fr" );
        List<AppointmentValidationError> listErrors = AppointmentImportRetryService.correctRow( mapRows.get( 3 ).getIdImportAppointment( ),
                new LinkedHashMap<>( mapGeneric ), new LinkedHashMap<>( ), Locale.FRANCE );

        assertEquals( 1, listErrors.size( ) );
        // Nothing was changed: the rows are still in error, the correction was not kept
        Map<Integer, AppointmentImportAppointment> mapAfter = rowsByLine( file );
        assertEquals( AppointmentImportStatus.ERROR, mapAfter.get( 2 ).getStatus( ) );
        assertEquals( AppointmentImportStatus.ERROR, mapAfter.get( 3 ).getStatus( ) );
        assertFalse( mapAfter.get( 3 ).getGenericAttributesJson( ).contains( "weber.corrige" ) );
        AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );
    }

    @Test
    public void testRetryOfOnlyInterruptedRowsIsRefused( ) throws IOException
    {
        AppointmentImportFile file = register( "interrupted-only.xlsx", row( "XAVIER", "xavier@paris.fr", _monday.plusDays( 11 ), TEN, ELEVEN ) );
        AppointmentImportBatch batch = AppointmentImportHome.findBatchesByFile( file.getIdImportFile( ) ).get( 0 );
        AppointmentImportAppointment row = rowsByLine( file ).get( 2 );
        AppointmentImportHome.markAppointmentError( row.getIdImportAppointment( ), AppointmentImportRetryService.INTERRUPTED, "interrupted" );
        AppointmentImportHome.updateBatchStatus( batch.getIdImportBatch( ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );
        AppointmentImportHome.updateFileStatus( file.getIdImportFile( ), AppointmentImportStatus.COMPLETED_WITH_ERRORS );

        // Nothing to retry with the others: the interrupted row is retried on its own, once checked
        assertFalse( AppointmentImportRetryService.retryBatch( batch.getIdImportBatch( ) ) );
        assertEquals( 0, AppointmentImportRetryService.retryFile( file.getIdImportFile( ) ) );
        assertEquals( AppointmentImportStatus.COMPLETED_WITH_ERRORS, AppointmentImportHome.findBatch( batch.getIdImportBatch( ) ).getStatus( ) );
        assertEquals( AppointmentImportStatus.COMPLETED_WITH_ERRORS, AppointmentImportHome.findFile( file.getIdImportFile( ) ).getStatus( ) );

        assertTrue( AppointmentImportRetryService.retryRow( row.getIdImportAppointment( ) ) );
    }

    private AppointmentImportFile register( String strName, Object [ ]... rows ) throws IOException
    {
        byte [ ] content = ImportTestUtils.workbook( HEADERS, rows );
        // The name makes the content of each run unique: the same file is refused the second time
        String strFileName = System.nanoTime( ) + "-" + strName;
        AppointmentImportFile file = _importService.register( _nFormId, strFileName, content,
                AppointmentImportService.computeFileHash( ( strFileName ).getBytes( ) ), "admin", Locale.FRANCE );
        assertEquals( file.getValidationReport( ), AppointmentImportStatus.PENDING, file.getStatus( ) );
        return file;
    }

    private static Object [ ] row( String strLastName, String strEmail, LocalDate date, LocalTime start, LocalTime end )
    {
        return new Object [ ] {
                strLastName, "Prénom", strEmail, "06 12 34 56 78", date, start, end, LocalDate.of( 1985, 4, 2 )
        };
    }

    private static Map<Integer, AppointmentImportAppointment> rowsByLine( AppointmentImportFile file )
    {
        Map<Integer, AppointmentImportAppointment> mapRows = new HashMap<>( );
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByFile( file.getIdImportFile( ) ) )
        {
            AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), null ).forEach( row -> mapRows.put( row.getSourceLineNumber( ), row ) );
        }
        return mapRows;
    }

    private static void setFormActive( boolean bActive )
    {
        Form form = FormService.findFormLightByPrimaryKey( _nFormId );
        assertNotNull( form );
        form.setIsActive( bActive );
        FormService.updateForm( form );
    }

    private static void execute( String strSql, LocalDateTime dtValue, int nId )
    {
        try ( DAOUtil daoUtil = new DAOUtil( strSql, PluginService.getPlugin( AppointmentImportHome.PLUGIN_NAME ) ) )
        {
            daoUtil.setTimestamp( 1, Timestamp.valueOf( dtValue ) );
            daoUtil.setInt( 2, nId );
            daoUtil.executeUpdate( );
        }
    }

    private static List<String> reportCells( byte [ ] report ) throws IOException
    {
        List<String> listCells = new ArrayList<>( );
        try ( XSSFWorkbook workbook = new XSSFWorkbook( new ByteArrayInputStream( report ) ) )
        {
            for ( Row row : workbook.getSheetAt( 0 ) )
            {
                for ( Cell cell : row )
                {
                    listCells.add( cell.toString( ) );
                }
            }
        }
        return listCells;
    }
}
