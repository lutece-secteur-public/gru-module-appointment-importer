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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;

/**
 * Computes the fingerprint of a line: what tells that two lines are the same, in one file or in two files of the same form.
 * <p>
 * Two lines are the same when they have the same standard columns and the same form fields, written with or without capitals, accents or extra blanks.
 * The fingerprint is a SHA-256 digest: it can be kept after the purge of the personal data of the rows.
 * </p>
 */
final class ImportRowFingerprint
{
    private static final List<String> STANDARD_ATTRIBUTES = List.of( AppointmentImportRow.ATTRIBUTE_LAST_NAME, AppointmentImportRow.ATTRIBUTE_FIRST_NAME,
            AppointmentImportRow.ATTRIBUTE_EMAIL, AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, AppointmentImportRow.ATTRIBUTE_BIRTH_DATE );
    private static final Pattern PATTERN_CHOICE_SEPARATOR = Pattern.compile( "\\s*;\\s*" );
    private static final String SEPARATOR = "\u0000";

    private ImportRowFingerprint( )
    {
    }

    /**
     * @param row
     *            a line of a workbook
     * @return the fingerprint of the line
     */
    static String of( AppointmentImportRow row )
    {
        return of( row.getGenericAttributes( ), row.getFormFields( ), row.getAppointmentDate( ), row.getStartingTime( ), row.getEndingTime( ) );
    }

    /**
     * @param mapGenericAttributes
     *            the values of the standard columns, by attribute key, normalized as on upload (phone number without separators, birth date dd/MM/yyyy)
     * @param mapFormFields
     *            the values of the form fields, by column
     * @param date
     *            the date of the appointment
     * @param startingTime
     *            the start of the appointment
     * @param endingTime
     *            the end of the appointment
     * @return the fingerprint of the line
     */
    static String of( Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields, LocalDate date, LocalTime startingTime,
            LocalTime endingTime )
    {
        List<String> listParts = new ArrayList<>( );
        for ( String strAttribute : STANDARD_ATTRIBUTES )
        {
            listParts.add( ImportTextUtils.normalize( mapGenericAttributes.get( strAttribute ) ) );
        }
        listParts.add( date.toString( ) );
        listParts.add( startingTime.toString( ) );
        listParts.add( endingTime.toString( ) );
        // By column name, whatever the order of the columns of the file; an empty field is a field the file may not have
        Map<String, String> mapFields = new TreeMap<>( );
        for ( Map.Entry<String, String> field : mapFormFields.entrySet( ) )
        {
            String strValue = PATTERN_CHOICE_SEPARATOR.matcher( ImportTextUtils.normalize( field.getValue( ) ) ).replaceAll( ";" );
            if ( !strValue.isEmpty( ) )
            {
                mapFields.put( ImportTextUtils.normalize( field.getKey( ) ), strValue );
            }
        }
        mapFields.forEach( ( strColumn, strValue ) -> listParts.add( strColumn + "=" + strValue ) );
        return AppointmentImportService.computeFileHash( String.join( SEPARATOR, listParts ).getBytes( StandardCharsets.UTF_8 ) );
    }
}
