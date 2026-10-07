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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One validated source row: standard values, slot, and the other columns kept as is for the failed rows export. */
public final class AppointmentImportRow
{
    public static final String ATTRIBUTE_LAST_NAME = "lastName";
    public static final String ATTRIBUTE_FIRST_NAME = "firstName";
    public static final String ATTRIBUTE_EMAIL = "email";
    public static final String ATTRIBUTE_PHONE_NUMBER = "phoneNumber";
    public static final String ATTRIBUTE_BIRTH_DATE = "birthDate";

    private final int _lineNumber;
    private final Map<String, String> _genericAttributes;
    private final Map<String, String> _formFields;
    private final LocalDate _appointmentDate;
    private final LocalTime _startingTime;
    private final LocalTime _endingTime;

    public AppointmentImportRow( int lineNumber, Map<String, String> genericAttributes, Map<String, String> formFields, LocalDate appointmentDate,
            LocalTime startingTime, LocalTime endingTime )
    {
        _lineNumber = lineNumber;
        _genericAttributes = Collections.unmodifiableMap( new LinkedHashMap<>( genericAttributes ) );
        _formFields = Collections.unmodifiableMap( new LinkedHashMap<>( formFields ) );
        _appointmentDate = appointmentDate;
        _startingTime = startingTime;
        _endingTime = endingTime;
    }

    public int getLineNumber( )
    {
        return _lineNumber;
    }

    public Map<String, String> getGenericAttributes( )
    {
        return _genericAttributes;
    }

    public Map<String, String> getFormFields( )
    {
        return _formFields;
    }

    public String getLastName( )
    {
        return _genericAttributes.get( ATTRIBUTE_LAST_NAME );
    }

    public String getFirstName( )
    {
        return _genericAttributes.get( ATTRIBUTE_FIRST_NAME );
    }

    public String getEmail( )
    {
        return _genericAttributes.get( ATTRIBUTE_EMAIL );
    }

    public String getPhoneNumber( )
    {
        return _genericAttributes.getOrDefault( ATTRIBUTE_PHONE_NUMBER, "" );
    }

    public String getBirthDate( )
    {
        return _genericAttributes.get( ATTRIBUTE_BIRTH_DATE );
    }

    public LocalDate getAppointmentDate( )
    {
        return _appointmentDate;
    }

    public LocalTime getStartingTime( )
    {
        return _startingTime;
    }

    public LocalTime getEndingTime( )
    {
        return _endingTime;
    }
}
