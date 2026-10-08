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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentExcelValidationResult;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;

/**
 * Tests the reading and the validation of a workbook.
 */
public class AppointmentExcelReaderTest
{
    private static final LocalDate DATE = LocalDate.of( 2026, 12, 2 );
    private static final LocalTime NINE = LocalTime.of( 9, 0 );
    private static final LocalTime FIVE_PM = LocalTime.of( 17, 0 );

    private final AppointmentExcelReader _reader = new AppointmentExcelReader( );

    @Test
    public void testSampleWorkbookIsValid( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS,
                ImportTestUtils.sampleRow( "TESTNOM", "test@gmail.com", DATE, NINE, FIVE_PM ),
                ImportTestUtils.sampleRow( "TESTNAME", "test2@gmail.com", DATE, NINE, FIVE_PM ) );

        assertFalse( messages( result ).toString( ), result.hasErrors( ) );
        assertEquals( 2, result.getValidRows( ).size( ) );
        AppointmentImportRow row = result.getValidRows( ).get( 0 );
        assertEquals( 2, row.getLineNumber( ) );
        assertEquals( DATE, row.getAppointmentDate( ) );
        assertEquals( NINE, row.getStartingTime( ) );
        assertEquals( FIVE_PM, row.getEndingTime( ) );
        assertEquals( "12/12/1988", row.getBirthDate( ) );
        assertEquals( "CASPE 18", row.getFormFields( ).get( "CASPE d'affectation" ) );
        assertEquals( Arrays.asList( "CASPE d'affectation", "email du CAR" ), result.getOtherColumnNames( ).stream( ).collect( Collectors.toList( ) ) );
    }

    @Test
    public void testLeadingZeroOfNumericPhoneNumberIsRestored( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS, ImportTestUtils.sampleRow( "TESTNOM", "test@gmail.com", DATE, NINE, FIVE_PM ) );

        assertEquals( "0781995425", result.getValidRows( ).get( 0 ).getPhoneNumber( ) );
    }

    @Test
    public void testHeadersOfTheSpecificationAreRecognized( ) throws IOException
    {
        List<String> listHeaders = Arrays.asList( "nom", "prenom", "email", "telephone", "date_rdv", "heure_debut", "heure_fin", "date_naissance",
                "caspe_affectation" );
        AppointmentExcelValidationResult result = read( listHeaders, new Object [ ] {
                "DUPONT", "Marie", "marie.dupont@paris.fr", "06 12 34 56 78", "14/10/2026", "09:00", "12:00", "15/03/1980", "CASPE 18"
        } );

        assertFalse( messages( result ).toString( ), result.hasErrors( ) );
        AppointmentImportRow row = result.getValidRows( ).get( 0 );
        assertEquals( "0612345678", row.getPhoneNumber( ) );
        assertEquals( LocalDate.of( 2026, 10, 14 ), row.getAppointmentDate( ) );
        assertEquals( Arrays.asList( "caspe_affectation" ), result.getOtherColumnNames( ).stream( ).collect( Collectors.toList( ) ) );
    }

    @Test
    public void testMissingMandatoryColumn( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS.subList( 1, ImportTestUtils.HEADERS.size( ) ) );

        assertTrue( messages( result ).contains( "Nom : module.appointment.importer.error.column.missing" ) );
    }

    @Test
    public void testColumnGivenByHeaderAndAliasIsDuplicate( ) throws IOException
    {
        List<String> listHeaders = Arrays.asList( "Nom", "nom", "Prénom", "Email", "Date", "Heure de début", "Heure de fin", "Date de naissance" );
        AppointmentExcelValidationResult result = read( listHeaders );

        assertTrue( messages( result ).contains( "nom : module.appointment.importer.error.column.duplicate" ) );
    }

    @Test
    public void testAppointmentInThePastIsRejected( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS,
                ImportTestUtils.sampleRow( "TESTNOM", "test@gmail.com", ImportTestUtils.NOW.toLocalDate( ), NINE, FIVE_PM ) );

        assertTrue( messages( result ).contains( "2 Date : module.appointment.importer.error.value.past" ) );
    }

    @Test
    public void testInvalidValuesAreAllReported( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS, new Object [ ] {
                "", "Tata", "not-an-email", "12345", DATE, FIVE_PM, NINE, LocalDate.of( 2030, 1, 1 ), "CASPE 18", "cartest@gmail.com"
        } );

        List<String> listMessages = messages( result );
        assertTrue( listMessages.contains( "2 Nom : module.appointment.importer.error.value.required" ) );
        assertTrue( listMessages.contains( "2 Email : module.appointment.importer.error.value.email" ) );
        assertTrue( listMessages.contains( "2 Téléphone : module.appointment.importer.error.value.phone" ) );
        assertTrue( listMessages.contains( "2 Heure de fin : module.appointment.importer.error.value.timeOrder" ) );
        assertTrue( listMessages.contains( "2 Date de naissance : module.appointment.importer.error.value.birthDateFuture" ) );
        assertTrue( result.getValidRows( ).isEmpty( ) );
    }

    @Test
    public void testNameLongerThanTheLimitIsRejected( ) throws IOException
    {
        StringBuilder sbName = new StringBuilder( );
        for ( int i = 0; i < 101; i++ )
        {
            sbName.append( 'A' );
        }
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS,
                ImportTestUtils.sampleRow( sbName.toString( ), "test@gmail.com", DATE, NINE, FIVE_PM ) );

        assertTrue( messages( result ).contains( "2 Nom : module.appointment.importer.error.value.tooLong 100" ) );
    }

    @Test
    public void testDuplicateRowIsRejected( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS,
                ImportTestUtils.sampleRow( "TESTNOM", "test@gmail.com", DATE, NINE, FIVE_PM ),
                ImportTestUtils.sampleRow( "testnom", "TEST@gmail.com", DATE, NINE, FIVE_PM ) );

        assertTrue( messages( result ).toString( ),
                messages( result ).contains( "3 module.appointment.importer.line : module.appointment.importer.error.row.duplicate 2" ) );
        assertEquals( 1, result.getValidRows( ).size( ) );
    }

    @Test
    public void testWorkbookWithoutRowsIsRejected( ) throws IOException
    {
        AppointmentExcelValidationResult result = read( ImportTestUtils.HEADERS );

        assertTrue( messages( result ).toString( ),
                messages( result ).contains( "module.appointment.importer.workbook : module.appointment.importer.error.workbook.noRows" ) );
    }

    @Test
    public void testUnreadableWorkbookHasNoHeader( )
    {
        AppointmentExcelValidationResult result = _reader.read( "not a workbook".getBytes( java.nio.charset.StandardCharsets.UTF_8 ),
                ImportTestUtils.settings( 1000 ) );

        assertFalse( result.isHeaderRead( ) );
        assertEquals( 1, result.getErrors( ).size( ) );
    }

    @Test
    public void testHeaderIsRead( ) throws IOException
    {
        assertTrue( read( ImportTestUtils.HEADERS ).isHeaderRead( ) );
    }

    @Test
    public void testTooManyRows( ) throws IOException
    {
        AppointmentExcelValidationResult result = _reader.read( ImportTestUtils.workbook( ImportTestUtils.HEADERS,
                ImportTestUtils.sampleRow( "A", "a@gmail.com", DATE, NINE, FIVE_PM ), ImportTestUtils.sampleRow( "B", "b@gmail.com", DATE, NINE, FIVE_PM ),
                ImportTestUtils.sampleRow( "C", "c@gmail.com", DATE, NINE, FIVE_PM ) ), ImportTestUtils.settings( 2 ) );

        assertTrue( messages( result ).toString( ),
                messages( result ).contains( "module.appointment.importer.workbook : module.appointment.importer.error.workbook.tooManyRows 3 2" ) );
    }

    private AppointmentExcelValidationResult read( List<String> listHeaders, Object [ ]... rows ) throws IOException
    {
        return _reader.read( ImportTestUtils.workbook( listHeaders, rows ), ImportTestUtils.settings( 1000 ) );
    }

    /** Each error as "line field : message", without the line for a workbook error */
    private static List<String> messages( AppointmentExcelValidationResult result )
    {
        return result.getErrors( ).stream( ).map( AppointmentExcelReaderTest::message ).collect( Collectors.toList( ) );
    }

    private static String message( AppointmentValidationError error )
    {
        return ( error.getLineNumber( ) == null ? "" : error.getLineNumber( ) + " " ) + error.getField( ) + " : " + error.getMessage( );
    }
}
