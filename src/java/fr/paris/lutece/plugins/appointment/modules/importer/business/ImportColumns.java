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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * Headers and mandatory flags of the standard columns, as configured.
 * <p>
 * {@code appointment-importer.column.<key>.header} renames a column, {@code appointment-importer.column.<key>.alias} changes the other header it is
 * recognized by, {@code appointment-importer.column.<key>.mandatory} makes an optional column
 * mandatory or not. The columns an appointment cannot do without stay mandatory.
 * </p>
 */
public final class ImportColumns
{
    private static final String PROPERTY_PREFIX = "appointment-importer.column.";
    private static final String PROPERTY_SUFFIX_HEADER = ".header";
    private static final String PROPERTY_SUFFIX_ALIAS = ".alias";
    private static final String PROPERTY_SUFFIX_MANDATORY = ".mandatory";

    private final Map<ImportColumn, String> _mapHeaders = new EnumMap<>( ImportColumn.class );
    private final Map<ImportColumn, Boolean> _mapMandatory = new EnumMap<>( ImportColumn.class );
    private final Map<String, ImportColumn> _mapByNormalizedHeader = new HashMap<>( );

    private ImportColumns( )
    {
    }

    /**
     * @return the columns as configured in the properties
     */
    public static ImportColumns fromProperties( )
    {
        ImportColumns columns = new ImportColumns( );
        for ( ImportColumn column : ImportColumn.values( ) )
        {
            String strHeader = AppPropertiesService.getProperty( PROPERTY_PREFIX + column.getKey( ) + PROPERTY_SUFFIX_HEADER, column.getDefaultHeader( ) );
            String strAlias = AppPropertiesService.getProperty( PROPERTY_PREFIX + column.getKey( ) + PROPERTY_SUFFIX_ALIAS, column.getDefaultAlias( ) );
            boolean bMandatory = AppPropertiesService.getPropertyBoolean( PROPERTY_PREFIX + column.getKey( ) + PROPERTY_SUFFIX_MANDATORY,
                    column.isMandatoryByDefault( ) );
            columns.put( column, strHeader, strAlias, bMandatory );
        }
        return columns;
    }

    /**
     * @return the columns with their default headers and flags
     */
    public static ImportColumns defaults( )
    {
        ImportColumns columns = new ImportColumns( );
        for ( ImportColumn column : ImportColumn.values( ) )
        {
            columns.put( column, column.getDefaultHeader( ), column.getDefaultAlias( ), column.isMandatoryByDefault( ) );
        }
        return columns;
    }

    private void put( ImportColumn column, String strHeader, String strAlias, boolean bMandatory )
    {
        _mapHeaders.put( column, strHeader );
        _mapMandatory.put( column, column.isRequired( ) || bMandatory );
        _mapByNormalizedHeader.put( ImportTextUtils.normalize( strHeader ), column );
        if ( strAlias != null && !strAlias.trim( ).isEmpty( ) )
        {
            _mapByNormalizedHeader.put( ImportTextUtils.normalize( strAlias ), column );
        }
    }

    /**
     * @param column
     *            the column
     * @return its header
     */
    public String getHeader( ImportColumn column )
    {
        return _mapHeaders.get( column );
    }

    /**
     * @param strHeader
     *            a header
     * @return true if the header names one of the standard columns
     */
    public boolean isStandardHeader( String strHeader )
    {
        return _mapByNormalizedHeader.containsKey( ImportTextUtils.normalize( strHeader ) );
    }

    /**
     * @param column
     *            the column
     * @return true if the column and its values are mandatory
     */
    public boolean isMandatory( ImportColumn column )
    {
        return _mapMandatory.get( column );
    }

    /**
     * @param strHeader
     *            a header, as written in the workbook
     * @return the standard column with this header or alias, or null
     */
    public ImportColumn fromHeader( String strHeader )
    {
        return _mapByNormalizedHeader.get( ImportTextUtils.normalize( strHeader ) );
    }
}
