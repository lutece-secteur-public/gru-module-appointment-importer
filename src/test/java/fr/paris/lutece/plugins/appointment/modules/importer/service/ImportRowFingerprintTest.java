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
import static org.junit.Assert.assertNotEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;

public class ImportRowFingerprintTest
{
    private static final LocalDate DATE = LocalDate.of( 2026, 11, 2 );
    private static final LocalTime TEN = LocalTime.of( 10, 0 );
    private static final LocalTime HALF_PAST_TEN = LocalTime.of( 10, 30 );

    @Test
    public void testSameLineWrittenDifferentlyHasTheSameFingerprint( )
    {
        String strFingerprint = fingerprint( generic( "DUPONT", "Élodie", "elodie.dupont@paris.fr" ), fields( "Niveau", "CM2", "Jours", "lundi;mardi" ) );

        assertEquals( strFingerprint, fingerprint( generic( "dupont", "  elodie ", "ELODIE.DUPONT@paris.fr" ), fields( "niveau", "cm2", "Jours", "Lundi ; Mardi" ) ) );
    }

    @Test
    public void testOrderOfTheFormFieldsDoesNotMatter( )
    {
        Map<String, String> mapReversed = new LinkedHashMap<>( );
        mapReversed.put( "Jours", "lundi" );
        mapReversed.put( "Niveau", "CM2" );

        assertEquals( fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2", "Jours", "lundi" ) ),
                fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), mapReversed ) );
    }

    @Test
    public void testEmptyFormFieldIsLikeNoField( )
    {
        assertEquals( fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2", "Commentaire", " " ) ),
                fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2" ) ) );
    }

    @Test
    public void testFormFieldMakesTheDifference( )
    {
        assertNotEquals( fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2" ) ),
                fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM1" ) ) );
    }

    @Test
    public void testStandardColumnsAndSlotMakeTheDifference( )
    {
        String strFingerprint = fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( ) );

        assertNotEquals( strFingerprint, fingerprint( generic( "DUPONT", "Marie", "marie.dupont@paris.fr" ), fields( ) ) );
        assertNotEquals( strFingerprint, ImportRowFingerprint.of( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( ), DATE, TEN, LocalTime.of( 11, 0 ) ) );
        assertNotEquals( strFingerprint,
                ImportRowFingerprint.of( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( ), DATE.plusDays( 1 ), TEN, HALF_PAST_TEN ) );
    }

    @Test
    public void testFingerprintOfARowIsTheFingerprintOfItsValues( )
    {
        AppointmentImportRow row = new AppointmentImportRow( 2, generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2" ), DATE, TEN,
                HALF_PAST_TEN );

        assertEquals( fingerprint( generic( "DUPONT", "Marie", "marie@paris.fr" ), fields( "Niveau", "CM2" ) ), ImportRowFingerprint.of( row ) );
        assertEquals( 64, ImportRowFingerprint.of( row ).length( ) );
    }

    private static String fingerprint( Map<String, String> mapGeneric, Map<String, String> mapFields )
    {
        return ImportRowFingerprint.of( mapGeneric, mapFields, DATE, TEN, HALF_PAST_TEN );
    }

    private static Map<String, String> generic( String strLastName, String strFirstName, String strEmail )
    {
        Map<String, String> mapGeneric = new LinkedHashMap<>( );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_LAST_NAME, strLastName );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_FIRST_NAME, strFirstName );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_EMAIL, strEmail );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, "0612345678" );
        mapGeneric.put( AppointmentImportRow.ATTRIBUTE_BIRTH_DATE, "02/04/1985" );
        return mapGeneric;
    }

    private static Map<String, String> fields( String... keyValues )
    {
        Map<String, String> mapFields = new LinkedHashMap<>( );
        for ( int i = 0; i < keyValues.length; i += 2 )
        {
            mapFields.put( keyValues [i], keyValues [i + 1] );
        }
        return mapFields;
    }
}
