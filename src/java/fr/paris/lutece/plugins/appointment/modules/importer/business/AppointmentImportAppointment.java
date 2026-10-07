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

import java.time.LocalDateTime;

/** One source row awaiting, or resulting from, appointment creation. */
public class AppointmentImportAppointment
{
    private int _idImportAppointment;
    private int _idImportBatch;
    private int _sourceLineNumber;
    private String _genericAttributesJson;
    private String _formFieldsJson;
    private String _status;
    private String _errorCode;
    private String _errorMessage;
    private Integer _idAppointment;
    private LocalDateTime _creationDate;
    private LocalDateTime _lastExecDate;

    public int getIdImportAppointment( )
    {
        return _idImportAppointment;
    }

    public void setIdImportAppointment( int nValue )
    {
        _idImportAppointment = nValue;
    }

    public int getIdImportBatch( )
    {
        return _idImportBatch;
    }

    public void setIdImportBatch( int nValue )
    {
        _idImportBatch = nValue;
    }

    public int getSourceLineNumber( )
    {
        return _sourceLineNumber;
    }

    public void setSourceLineNumber( int nValue )
    {
        _sourceLineNumber = nValue;
    }

    public String getGenericAttributesJson( )
    {
        return _genericAttributesJson;
    }

    public void setGenericAttributesJson( String strValue )
    {
        _genericAttributesJson = strValue;
    }

    public String getFormFieldsJson( )
    {
        return _formFieldsJson;
    }

    public void setFormFieldsJson( String strValue )
    {
        _formFieldsJson = strValue;
    }

    public String getStatus( )
    {
        return _status;
    }

    public void setStatus( String strValue )
    {
        _status = strValue;
    }

    public String getErrorCode( )
    {
        return _errorCode;
    }

    public void setErrorCode( String strValue )
    {
        _errorCode = strValue;
    }

    public String getErrorMessage( )
    {
        return _errorMessage;
    }

    public void setErrorMessage( String strValue )
    {
        _errorMessage = strValue;
    }

    public Integer getIdAppointment( )
    {
        return _idAppointment;
    }

    public void setIdAppointment( Integer nValue )
    {
        _idAppointment = nValue;
    }

    public LocalDateTime getCreationDate( )
    {
        return _creationDate;
    }

    public void setCreationDate( LocalDateTime dtValue )
    {
        _creationDate = dtValue;
    }

    public LocalDateTime getLastExecDate( )
    {
        return _lastExecDate;
    }

    public void setLastExecDate( LocalDateTime dtValue )
    {
        _lastExecDate = dtValue;
    }
}
