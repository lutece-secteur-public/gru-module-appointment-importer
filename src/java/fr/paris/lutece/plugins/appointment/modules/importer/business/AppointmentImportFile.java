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
import java.time.format.DateTimeFormatter;

/** Persistent uploaded workbook and its validation result. */
public class AppointmentImportFile
{
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern( "dd/MM/uuuu · HH:mm" );

    private int _idImportFile;
    private String _importFileName;
    private int _idForm;
    private String _formTitle;
    private ImportCounts _counts = new ImportCounts( );
    private String _adminAccessCode;
    private String _status;
    private String _fileHash;
    private String _validationReport;
    private LocalDateTime _creationDate;
    private LocalDateTime _lastExecDate;

    public int getIdImportFile( )
    {
        return _idImportFile;
    }

    public void setIdImportFile( int nValue )
    {
        _idImportFile = nValue;
    }

    public String getImportFileName( )
    {
        return _importFileName;
    }

    public void setImportFileName( String strValue )
    {
        _importFileName = strValue;
    }

    public int getIdForm( )
    {
        return _idForm;
    }

    public void setIdForm( int nValue )
    {
        _idForm = nValue;
    }

    public String getAdminAccessCode( )
    {
        return _adminAccessCode;
    }

    public void setAdminAccessCode( String strValue )
    {
        _adminAccessCode = strValue;
    }

    /**
     * @return the number of rows by outcome; empty unless filled by {@link AppointmentImportHome}
     */
    public ImportCounts getCounts( )
    {
        return _counts;
    }

    public void setCounts( ImportCounts counts )
    {
        _counts = counts;
    }

    public String getFormTitle( )
    {
        return _formTitle;
    }

    public void setFormTitle( String strValue )
    {
        _formTitle = strValue;
    }

    public String getStatus( )
    {
        return _status;
    }

    public void setStatus( String strValue )
    {
        _status = strValue;
    }

    public String getFileHash( )
    {
        return _fileHash;
    }

    public void setFileHash( String strValue )
    {
        _fileHash = strValue;
    }

    public String getValidationReport( )
    {
        return _validationReport;
    }

    public void setValidationReport( String strValue )
    {
        _validationReport = strValue;
    }

    public LocalDateTime getCreationDate( )
    {
        return _creationDate;
    }

    public void setCreationDate( LocalDateTime dtValue )
    {
        _creationDate = dtValue;
    }

    public String getCreationDateLabel( )
    {
        return _creationDate == null ? "" : _creationDate.format( DATE_TIME_FORMAT );
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
