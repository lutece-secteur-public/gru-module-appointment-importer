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

import java.io.Serializable;

/**
 * Describes one user-facing workbook validation error.
 */
public final class AppointmentValidationError implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final Integer _lineNumber;
    private final String _field;
    private final String _message;

    private AppointmentValidationError( Integer lineNumber, String field, String message )
    {
        _lineNumber = lineNumber;
        _field = field;
        _message = message;
    }

    /**
     * Creates a row-level error (a specific data cell is invalid).
     *
     * @param lineNumber 1-based row number in the source Excel file
     * @param field      column header identifying the invalid cell
     * @param message    localized description of the problem
     */
    public static AppointmentValidationError row( int lineNumber, String field, String message )
    {
        return new AppointmentValidationError( lineNumber, field, message );
    }

    /**
     * Creates a workbook-level error (structure problem not tied to a specific row,
     * e.g. a missing mandatory column or an unreadable file).
     *
     * @param field   column header or workbook-level label identifying the problem area
     * @param message localized description of the problem
     */
    public static AppointmentValidationError workbook( String field, String message )
    {
        return new AppointmentValidationError( null, field, message );
    }

    public Integer getLineNumber( )
    {
        return _lineNumber;
    }

    public String getField( )
    {
        return _field;
    }

    public String getMessage( )
    {
        return _message;
    }
}
