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
import java.time.LocalDateTime;

/**
 * The fingerprint of a line imported on a form, with the row it was imported by. It outlives the purge of its row when the row created its appointment,
 * so that the same line is still refused afterwards.
 */
public class AppointmentImportFingerprint
{
    private int _idForm;
    private String _fingerprint;
    private int _idImportFile;
    private int _idImportAppointment;
    private int _sourceLineNumber;
    private LocalDate _appointmentDate;
    private LocalDateTime _creationDate;
    private String _importFileName;
    private String _rowStatus;
    private String _rowErrorCode;

    public int getIdForm( )
    {
        return _idForm;
    }

    public void setIdForm( int nValue )
    {
        _idForm = nValue;
    }

    public String getFingerprint( )
    {
        return _fingerprint;
    }

    public void setFingerprint( String strValue )
    {
        _fingerprint = strValue;
    }

    public int getIdImportFile( )
    {
        return _idImportFile;
    }

    public void setIdImportFile( int nValue )
    {
        _idImportFile = nValue;
    }

    public int getIdImportAppointment( )
    {
        return _idImportAppointment;
    }

    public void setIdImportAppointment( int nValue )
    {
        _idImportAppointment = nValue;
    }

    public int getSourceLineNumber( )
    {
        return _sourceLineNumber;
    }

    public void setSourceLineNumber( int nValue )
    {
        _sourceLineNumber = nValue;
    }

    public LocalDate getAppointmentDate( )
    {
        return _appointmentDate;
    }

    public void setAppointmentDate( LocalDate value )
    {
        _appointmentDate = value;
    }

    public LocalDateTime getCreationDate( )
    {
        return _creationDate;
    }

    public void setCreationDate( LocalDateTime value )
    {
        _creationDate = value;
    }

    /**
     * @return the name of the file the line was imported by; read only, from the file
     */
    public String getImportFileName( )
    {
        return _importFileName;
    }

    public void setImportFileName( String strValue )
    {
        _importFileName = strValue;
    }

    /**
     * @return the status of the row the line was imported by, or null once the row is purged; read only, from the row
     */
    public String getRowStatus( )
    {
        return _rowStatus;
    }

    public void setRowStatus( String strValue )
    {
        _rowStatus = strValue;
    }

    /**
     * @return the error code of the row the line was imported by; read only, from the row
     */
    public String getRowErrorCode( )
    {
        return _rowErrorCode;
    }

    public void setRowErrorCode( String strValue )
    {
        _rowErrorCode = strValue;
    }
}
