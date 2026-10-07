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

import java.util.HashMap;
import java.util.Map;

import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;

/**
 * Standard columns of an import workbook. Only these columns are validated and used to create appointments; any other
 * column is kept as is for the failed rows export.
 */
public enum ImportColumn
{
    LAST_NAME( "Nom", AppointmentImportRow.ATTRIBUTE_LAST_NAME, true ),
    FIRST_NAME( "Prénom", AppointmentImportRow.ATTRIBUTE_FIRST_NAME, true ),
    EMAIL( "Email", AppointmentImportRow.ATTRIBUTE_EMAIL, true ),
    PHONE_NUMBER( "Téléphone", AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, false ),
    DATE( "Date", null, true ),
    STARTING_TIME( "Heure de début", null, true ),
    ENDING_TIME( "Heure de fin", null, true ),
    BIRTH_DATE( "Date de naissance", AppointmentImportRow.ATTRIBUTE_BIRTH_DATE, true );

    private static final Map<String, ImportColumn> BY_NORMALIZED_HEADER = new HashMap<>( );

    static
    {
        for ( ImportColumn column : values( ) )
        {
            BY_NORMALIZED_HEADER.put( column.getNormalizedHeader( ), column );
        }
    }

    private final String _strHeader;
    private final String _strNormalizedHeader;
    private final String _strAttributeKey;
    private final boolean _bMandatory;

    ImportColumn( String strHeader, String strAttributeKey, boolean bMandatory )
    {
        _strHeader = strHeader;
        _strNormalizedHeader = ImportTextUtils.normalize( strHeader );
        _strAttributeKey = strAttributeKey;
        _bMandatory = bMandatory;
    }

    /**
     * Finds the standard column matching a workbook header
     *
     * @param strNormalizedHeader
     *            the header, already normalized with {@link ImportTextUtils#normalize(String)}
     * @return the standard column, or null if the header is not a standard column
     */
    public static ImportColumn fromNormalizedHeader( String strNormalizedHeader )
    {
        return BY_NORMALIZED_HEADER.get( strNormalizedHeader );
    }

    /**
     * @return the header expected in the workbook, as shown to the administrator
     */
    public String getHeader( )
    {
        return _strHeader;
    }

    /**
     * @return the normalized header
     */
    public String getNormalizedHeader( )
    {
        return _strNormalizedHeader;
    }

    /**
     * @return the key of the value in the generic attributes of a row, or null for the slot columns
     */
    public String getAttributeKey( )
    {
        return _strAttributeKey;
    }

    /**
     * @return true if both the column and a value in each row are required
     */
    public boolean isMandatory( )
    {
        return _bMandatory;
    }
}
