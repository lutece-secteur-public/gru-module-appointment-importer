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

/**
 * Standard columns of an import workbook.
 * <p>
 * A column is recognized by its header or by its alias. The header, the alias and, for the optional columns, the mandatory flag can be changed in {@code appointment-importer.properties}: see {@link ImportColumns}.
 * </p>
 */
public enum ImportColumn
{
    LAST_NAME( "lastName", "Nom", "nom", AppointmentImportRow.ATTRIBUTE_LAST_NAME, true, true ),
    FIRST_NAME( "firstName", "Prénom", "prenom", AppointmentImportRow.ATTRIBUTE_FIRST_NAME, true, true ),
    EMAIL( "email", "Email", "email", AppointmentImportRow.ATTRIBUTE_EMAIL, true, true ),
    PHONE_NUMBER( "phoneNumber", "Téléphone", "telephone", AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, false, false ),
    DATE( "date", "Date", "date_rdv", null, true, true ),
    STARTING_TIME( "startingTime", "Heure de début", "heure_debut", null, true, true ),
    ENDING_TIME( "endingTime", "Heure de fin", "heure_fin", null, true, true ),
    BIRTH_DATE( "birthDate", "Date de naissance", "date_naissance", AppointmentImportRow.ATTRIBUTE_BIRTH_DATE, false, true );

    private final String _strKey;
    private final String _strDefaultHeader;
    private final String _strDefaultAlias;
    private final String _strAttributeKey;
    private final boolean _bRequired;
    private final boolean _bMandatoryByDefault;

    ImportColumn( String strKey, String strDefaultHeader, String strDefaultAlias, String strAttributeKey, boolean bRequired, boolean bMandatoryByDefault )
    {
        _strKey = strKey;
        _strDefaultHeader = strDefaultHeader;
        _strDefaultAlias = strDefaultAlias;
        _strAttributeKey = strAttributeKey;
        _bRequired = bRequired;
        _bMandatoryByDefault = bMandatoryByDefault;
    }

    /**
     * @return the key of the column in the configuration
     */
    public String getKey( )
    {
        return _strKey;
    }

    /**
     * @return the header used when none is configured
     */
    public String getDefaultHeader( )
    {
        return _strDefaultHeader;
    }

    /**
     * @return the other header accepted when none is configured (the technical name of the import specification)
     */
    public String getDefaultAlias( )
    {
        return _strDefaultAlias;
    }

    /**
     * @return the key of the value in the stored row, or null for the columns that define the slot
     */
    public String getAttributeKey( )
    {
        return _strAttributeKey;
    }

    /**
     * @return true if an appointment cannot be created without this column, whatever the configuration
     */
    public boolean isRequired( )
    {
        return _bRequired;
    }

    /**
     * @return true if the column is mandatory when the configuration says nothing
     */
    public boolean isMandatoryByDefault( )
    {
        return _bMandatoryByDefault;
    }
}
