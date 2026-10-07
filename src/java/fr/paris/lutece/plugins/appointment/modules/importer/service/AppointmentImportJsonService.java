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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.portal.service.i18n.I18nService;

/** JSON encoding shared by the persistent importer records and reports. */
public final class AppointmentImportJsonService
{
    private static final ObjectMapper MAPPER = new ObjectMapper( );
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>( ) { };

    private static final String KEY_LINE = "module.appointment.importer.line";
    private static final String KEY_FIELD = "module.appointment.importer.field";
    private static final String KEY_MESSAGE = "module.appointment.importer.message";

    private AppointmentImportJsonService( )
    {
    }

    /**
     * Serializes a map to a JSON string.
     * Used to persist generic-attribute and form-field values in the database.
     *
     * @param mapValues the map to serialize; must not be {@code null}
     * @return the JSON representation
     */
    public static String writeMap( Map<String, String> mapValues )
    {
        return write( mapValues );
    }

    /**
     * Deserializes a JSON string.
     * The map preserves insertion order ({@link java.util.LinkedHashMap} under the hood).
     *
     * @param strJson the JSON string produced by {@link #writeMap}
     * @return the deserialized map
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Map<String, String> readMap( String strJson )
    {
        try
        {
            return MAPPER.readValue( strJson, MAP_TYPE );
        }
        catch( IOException e )
        {
            throw new IllegalArgumentException( "Cannot read import JSON map", e );
        }
    }

    /**
     * Deserializes a JSON validation-report string back to a list of {@link AppointmentValidationError}.
     * Row-level errors have a non-null {@code line} field; workbook-level errors have {@code null}.
     *
     * @param strJson the JSON string produced by {@link #writeErrors}
     * @return the list of errors in original order
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static List<AppointmentValidationError> readErrors( String strJson )
    {
        try
        {
            List<AppointmentValidationError> listErrors = new ArrayList<>( );
            for ( JsonNode item : MAPPER.readTree( strJson ) )
            {
                JsonNode nodeLine = item.get( "line" );
                if ( nodeLine == null || nodeLine.isNull( ) )
                {
                    listErrors.add( AppointmentValidationError.workbook( item.path( "field" ).asText( ), item.path( "message" ).asText( ) ) );
                }
                else
                {
                    listErrors.add( AppointmentValidationError.row( nodeLine.asInt( ), item.path( "field" ).asText( ), item.path( "message" ).asText( ) ) );
                }
            }
            return listErrors;
        }
        catch( IOException e )
        {
            throw new IllegalArgumentException( "Cannot read validation report JSON", e );
        }
    }

    /**
     * Formats the validation errors stored in {@code strJson} as a semicolon-separated CSV.
     *
     * @param strJson the serialised validation report
     * @param locale  the locale used to localise column headers
     * @return the CSV string
     */
    public static String errorsAsCsv( String strJson, Locale locale )
    {
        String strColLine = I18nService.getLocalizedString( KEY_LINE, locale );
        String strColField = I18nService.getLocalizedString( KEY_FIELD, locale );
        String strColMessage = I18nService.getLocalizedString( KEY_MESSAGE, locale );
        StringBuilder sbCsv = new StringBuilder( strColLine ).append( ';' ).append( strColField ).append( ';' ).append( strColMessage ).append( '\n' );
        for ( AppointmentValidationError error : readErrors( strJson ) )
        {
            sbCsv.append( error.getLineNumber( ) == null ? "" : error.getLineNumber( ) )
                    .append( ';' ).append( csvCell( error.getField( ) ) )
                    .append( ';' ).append( csvCell( error.getMessage( ) ) )
                    .append( '\n' );
        }
        return sbCsv.toString( );
    }

    /**
     * Serializes a list of {@link AppointmentValidationError} to a JSON string for storage in {@code validation_report}.
     * Each error is represented as {@code {"line": <int|null>, "field": "<string>", "message": "<string>"}}.
     *
     * @param listErrors the errors to serialize; must not be {@code null}
     * @return the JSON array string
     */
    public static String writeErrors( List<AppointmentValidationError> listErrors )
    {
        List<Map<String, Object>> listReport = listErrors.stream( ).map( error -> {
            Map<String, Object> mapItem = new LinkedHashMap<>( );
            mapItem.put( "line", error.getLineNumber( ) );
            mapItem.put( "field", error.getField( ) );
            mapItem.put( "message", error.getMessage( ) );
            return mapItem;
        } ).collect( Collectors.toList( ) );
        return write( listReport );
    }

    private static String csvCell( String strValue )
    {
        return '"' + strValue.replace( "\"", "\"\"" ) + '"';
    }

    private static String write( Object value )
    {
        try
        {
            return MAPPER.writeValueAsString( value );
        }
        catch( IOException e )
        {
            throw new IllegalStateException( "Cannot serialize to JSON", e );
        }
    }
}
