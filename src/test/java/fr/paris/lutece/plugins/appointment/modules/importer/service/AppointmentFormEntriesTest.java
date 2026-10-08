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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentFormEntries.EntryKind;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Field;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;

/**
 * Tests the matching of the columns with the fields of the form, the checks of their values and the responses built from them.
 */
public class AppointmentFormEntriesTest
{
    private final Entry _entryCaspe = entry( 1, "CASPE d&#39;affectation", "caspe_affectation", true );
    private final Entry _entryCar = entry( 2, "email du CAR", null, false );
    private final Entry _entryBirthDate = entry( 3, "Date de naissance", null, true );
    private final Entry _entryLevel = entry( 4, "Niveau", "niveau", false, choice( "Débutant", "1" ), choice( "Confirmé", "2" ) );
    private final Entry _entryDays = entry( 5, "Jours", "jours", false, choice( "Lundi", "lun" ), choice( "Mardi", "mar" ) );
    private final Map<Entry, EntryKind> _mapKinds = new HashMap<>( );

    private final AppointmentFormEntries _formEntries;

    public AppointmentFormEntriesTest( )
    {
        _mapKinds.put( _entryLevel, EntryKind.SINGLE_CHOICE );
        _mapKinds.put( _entryDays, EntryKind.MULTIPLE_CHOICE );
        _formEntries = new AppointmentFormEntries( Arrays.asList( _entryCaspe, _entryCar, _entryBirthDate, _entryLevel, _entryDays ),
                entry -> _mapKinds.getOrDefault( entry, EntryKind.TEXT ), ImportColumns.defaults( ) );
    }

    @Test
    public void testColumnsMatchFieldsByTitleOrCode( )
    {
        List<AppointmentValidationError> listErrors = _formEntries.validateColumns(
                new LinkedHashSet<>( Arrays.asList( "caspe_affectation", "Email du CAR", "niveau", "Jours" ) ), ImportTestUtils.settings( 10 ) );

        // The birth date field is filled from the standard column of the same name
        assertTrue( listErrors.isEmpty( ), listErrors.toString( ) );
    }

    @Test
    public void testUnknownColumnAndMissingFieldAreReported( )
    {
        List<String> listErrors = _formEntries.validateColumns( new LinkedHashSet<>( Arrays.asList( "caspe_affectation", "Inconnue", "niveau", "jours" ) ),
                ImportTestUtils.settings( 10 ) ).stream( ).map( e -> e.getField( ) + " : " + e.getMessage( ) ).collect( Collectors.toList( ) );

        assertEquals( Arrays.asList( "Inconnue : module.appointment.importer.error.column.notInForm",
                "email du CAR : module.appointment.importer.error.column.missingInFile" ), listErrors );
    }

    @Test
    public void testMandatoryFieldAndUnknownChoiceAreReported( )
    {
        List<String> listErrors = _formEntries.validateRow( row( "", "Expert", "Lundi ; Dimanche" ), ImportTestUtils.settings( 10 ) ).stream( )
                .map( e -> e.getField( ) + " : " + e.getMessage( ) ).collect( Collectors.toList( ) );

        assertEquals( Arrays.asList( "CASPE d'affectation : module.appointment.importer.error.value.required",
                "Niveau : module.appointment.importer.error.value.choice Expert Débutant, Confirmé",
                "Jours : module.appointment.importer.error.value.choice Dimanche Lundi, Mardi" ), listErrors );
    }

    @Test
    public void testFieldNamedLikeAnEmailOnlyAcceptsAnEmail( )
    {
        AppointmentImportRow row = row( "CASPE 18", "1", "lun" );
        Map<String, String> mapFields = new LinkedHashMap<>( row.getFormFields( ) );
        mapFields.put( "email du CAR", "pas un email" );
        List<String> listErrors = _formEntries.validateRow( new AppointmentImportRow( 2, row.getGenericAttributes( ), mapFields, row.getAppointmentDate( ),
                row.getStartingTime( ), row.getEndingTime( ) ), ImportTestUtils.settings( 10 ) ).stream( ).map( e -> e.getField( ) + " : " + e.getMessage( ) )
                .collect( Collectors.toList( ) );

        assertEquals( Arrays.asList( "email du CAR : module.appointment.importer.error.value.email" ), listErrors );
        assertTrue( ImportTestUtils.settings( 10 ).isEmailField( "email car" ) );
        assertTrue( ImportTestUtils.settings( 10 ).isEmailField( "courriel" ) );
        assertFalse( ImportTestUtils.settings( 10 ).isEmailField( "caspe d'affectation" ) );
    }

    @Test
    public void testResponsesCarryTheirChoice( )
    {
        AppointmentImportRow row = row( "CASPE 18", "confirme", "lun;Mardi" );
        assertTrue( _formEntries.validateRow( row, ImportTestUtils.settings( 10 ) ).isEmpty( ) );

        List<Response> listResponses = _formEntries.buildResponses( row.getGenericAttributes( ), row.getFormFields( ) );

        Map<Entry, List<Response>> mapResponses = listResponses.stream( ).collect( Collectors.groupingBy( Response::getEntry ) );
        assertEquals( "12/12/1988", mapResponses.get( _entryBirthDate ).get( 0 ).getResponseValue( ) );
        assertEquals( "CASPE 18", mapResponses.get( _entryCaspe ).get( 0 ).getResponseValue( ) );
        assertNull( mapResponses.get( _entryCaspe ).get( 0 ).getField( ) );
        assertSame( _entryLevel.getFields( ).get( 1 ), mapResponses.get( _entryLevel ).get( 0 ).getField( ) );
        assertEquals( "2", mapResponses.get( _entryLevel ).get( 0 ).getResponseValue( ) );
        assertEquals( Arrays.asList( "lun", "mar" ),
                mapResponses.get( _entryDays ).stream( ).map( Response::getResponseValue ).collect( Collectors.toList( ) ) );
        // An empty optional field gives no response
        assertNull( mapResponses.get( _entryCar ) );
    }

    private static AppointmentImportRow row( String strCaspe, String strLevel, String strDays )
    {
        Map<String, String> mapGeneric = new LinkedHashMap<>( );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_LAST_NAME, "DUPONT" );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_FIRST_NAME, "Marie" );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_EMAIL, "marie.dupont@paris.fr" );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_BIRTH_DATE, "12/12/1988" );
        Map<String, String> mapFields = new LinkedHashMap<>( );
        mapFields.put( "caspe_affectation", strCaspe );
        mapFields.put( "email du CAR", "" );
        mapFields.put( "niveau", strLevel );
        mapFields.put( "jours", strDays );
        return new AppointmentImportRow( 2, mapGeneric, mapFields, LocalDate.of( 2026, 12, 2 ), LocalTime.of( 9, 0 ), LocalTime.of( 17, 0 ) );
    }

    private static Entry entry( int nId, String strTitle, String strCode, boolean bMandatory, Field... choices )
    {
        Entry entry = new Entry( );
        entry.setIdEntry( nId );
        entry.setTitle( strTitle );
        entry.setCode( strCode );
        entry.setMandatory( bMandatory );
        entry.setFields( Arrays.asList( choices ) );
        return entry;
    }

    private static Field choice( String strTitle, String strValue )
    {
        Field field = new Field( );
        field.setCode( IEntryTypeService.FIELD_ANSWER_CHOICE );
        field.setTitle( strTitle );
        field.setValue( strValue );
        return field;
    }
}
