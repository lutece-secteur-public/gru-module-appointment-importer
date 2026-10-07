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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;
import fr.paris.lutece.plugins.appointment.service.EntryService;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.Field;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.AbstractEntryTypeCheckBox;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.AbstractEntryTypeChoice;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;

/**
 * The fields of an appointment form that a workbook fills: matches the columns to the fields, checks the values and builds the responses.
 * <p>
 * A column matches a field by its title or by its code. A field with choices (list, radio buttons, check boxes) only accepts the title or the value of
 * one of its choices; check boxes accept several, separated by {@code ;}.
 * </p>
 */
final class AppointmentFormEntries
{
    /** How a field takes its value */
    enum EntryKind
    {
        TEXT,
        SINGLE_CHOICE,
        MULTIPLE_CHOICE
    }

    private static final String ERROR_COLUMN_NOT_IN_FORM = "module.appointment.importer.error.column.notInForm";
    private static final String ERROR_FORM_ENTRY_MISSING = "module.appointment.importer.error.column.missingInFile";
    private static final String ERROR_VALUE_REQUIRED = "module.appointment.importer.error.value.required";
    private static final String ERROR_VALUE_CHOICE = "module.appointment.importer.error.value.choice";
    private static final Pattern PATTERN_CHOICE_SEPARATOR = Pattern.compile( "\\s*;\\s*" );

    private final List<Entry> _listEntries;
    private final Function<Entry, EntryKind> _kindResolver;
    private final ImportColumns _columns;
    private final Map<String, Entry> _mapEntriesByName = new LinkedHashMap<>( );

    /**
     * @param listEntries
     *            the fields of the form, with their choices
     * @param kindResolver
     *            tells how a field takes its value
     * @param columns
     *            the standard columns
     */
    AppointmentFormEntries( List<Entry> listEntries, Function<Entry, EntryKind> kindResolver, ImportColumns columns )
    {
        _listEntries = listEntries;
        _kindResolver = kindResolver;
        _columns = columns;
        for ( Entry entry : listEntries )
        {
            _mapEntriesByName.putIfAbsent( ImportTextUtils.normalize( title( entry ) ), entry );
            if ( entry.getCode( ) != null && !entry.getCode( ).trim( ).isEmpty( ) )
            {
                _mapEntriesByName.putIfAbsent( ImportTextUtils.normalize( entry.getCode( ) ), entry );
            }
        }
    }

    /**
     * Loads the fields of a form that a workbook can fill: neither comments nor groups, nor the fields displayed in the back office only.
     *
     * @param nFormId
     *            the form
     * @param columns
     *            the standard columns
     * @return the fields
     */
    static AppointmentFormEntries load( int nFormId, ImportColumns columns )
    {
        List<Entry> listEntries = new ArrayList<>( );
        for ( Entry entryLight : EntryService.getFilter( nFormId, true ) )
        {
            // The list does not carry the choices of the fields: load each field completely
            Entry entry = EntryHome.findByPrimaryKey( entryLight.getIdEntry( ) );
            if ( entry != null && entry.getEntryType( ) != null && !Boolean.TRUE.equals( entry.getEntryType( ).getComment( ) )
                    && !Boolean.TRUE.equals( entry.getEntryType( ).getGroup( ) ) )
            {
                listEntries.add( entry );
            }
        }
        return new AppointmentFormEntries( listEntries, AppointmentFormEntries::kindOf, columns );
    }

    private static EntryKind kindOf( Entry entry )
    {
        IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( entry );
        if ( entryTypeService instanceof AbstractEntryTypeCheckBox )
        {
            return EntryKind.MULTIPLE_CHOICE;
        }
        return entryTypeService instanceof AbstractEntryTypeChoice ? EntryKind.SINGLE_CHOICE : EntryKind.TEXT;
    }

    /**
     * Checks that the other columns of the workbook and the fields of the form match both ways. The fields named like a standard column are filled from
     * it and need no column of their own.
     *
     * @param setOtherColumns
     *            the headers of the columns that are not standard
     * @param settings
     *            what the validation depends on
     * @return one error per unknown column and per field without column
     */
    List<AppointmentValidationError> validateColumns( Set<String> setOtherColumns, ImportValidationSettings settings )
    {
        List<AppointmentValidationError> listErrors = new ArrayList<>( );
        Set<Entry> setMatchedEntries = setOtherColumns.stream( ).map( this::findEntry ).filter( e -> e != null ).collect( Collectors.toSet( ) );
        for ( String strColumn : setOtherColumns )
        {
            if ( findEntry( strColumn ) == null )
            {
                listErrors.add( AppointmentValidationError.workbook( strColumn, settings.message( ERROR_COLUMN_NOT_IN_FORM ) ) );
            }
        }
        for ( Entry entry : _listEntries )
        {
            if ( !setMatchedEntries.contains( entry ) && standardColumnOf( entry ) == null )
            {
                listErrors.add( AppointmentValidationError.workbook( title( entry ), settings.message( ERROR_FORM_ENTRY_MISSING ) ) );
            }
        }
        return listErrors;
    }

    /**
     * Checks the values a row gives to the fields of the form: mandatory fields filled, choices known.
     *
     * @param row
     *            the row
     * @param settings
     *            what the validation depends on
     * @return the errors of the row
     */
    List<AppointmentValidationError> validateRow( AppointmentImportRow row, ImportValidationSettings settings )
    {
        List<AppointmentValidationError> listErrors = new ArrayList<>( );
        for ( Map.Entry<Entry, String> value : values( row.getGenericAttributes( ), row.getFormFields( ) ).entrySet( ) )
        {
            Entry entry = value.getKey( );
            String strValue = value.getValue( );
            if ( strValue.isEmpty( ) )
            {
                if ( entry.isMandatory( ) )
                {
                    listErrors.add( AppointmentValidationError.row( row.getLineNumber( ), title( entry ), settings.message( ERROR_VALUE_REQUIRED ) ) );
                }
                continue;
            }
            EntryKind kind = _kindResolver.apply( entry );
            if ( kind == EntryKind.TEXT )
            {
                continue;
            }
            for ( String strChoice : splitChoices( strValue, kind ) )
            {
                if ( findChoice( entry, strChoice ) == null )
                {
                    listErrors.add( AppointmentValidationError.row( row.getLineNumber( ), title( entry ),
                            settings.message( ERROR_VALUE_CHOICE, strChoice, choiceTitles( entry ) ) ) );
                }
            }
        }
        return listErrors;
    }

    /**
     * Builds the responses of an appointment from the values of its row. A choice is answered with its field, as the form would do.
     *
     * @param mapGenericAttributes
     *            the values of the standard columns
     * @param mapFormFields
     *            the values of the other columns
     * @return the responses
     */
    List<Response> buildResponses( Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields )
    {
        List<Response> listResponses = new ArrayList<>( );
        for ( Map.Entry<Entry, String> value : values( mapGenericAttributes, mapFormFields ).entrySet( ) )
        {
            Entry entry = value.getKey( );
            String strValue = value.getValue( );
            if ( strValue.isEmpty( ) )
            {
                continue;
            }
            EntryKind kind = _kindResolver.apply( entry );
            if ( kind == EntryKind.TEXT )
            {
                listResponses.add( response( entry, strValue, null ) );
                continue;
            }
            for ( String strChoice : splitChoices( strValue, kind ) )
            {
                Field field = findChoice( entry, strChoice );
                if ( field != null )
                {
                    listResponses.add( response( entry, field.getValue( ), field ) );
                }
            }
        }
        return listResponses;
    }

    /**
     * Gives each field of the form the value the row has for it: from its own column, or from the standard column it is named after.
     */
    private Map<Entry, String> values( Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields )
    {
        Map<Entry, String> mapValues = new LinkedHashMap<>( );
        for ( Entry entry : _listEntries )
        {
            ImportColumn column = standardColumnOf( entry );
            if ( column != null && column.getAttributeKey( ) != null )
            {
                mapValues.put( entry, mapGenericAttributes.getOrDefault( column.getAttributeKey( ), "" ) );
            }
        }
        for ( Map.Entry<String, String> field : mapFormFields.entrySet( ) )
        {
            Entry entry = findEntry( field.getKey( ) );
            if ( entry != null )
            {
                mapValues.put( entry, field.getValue( ) == null ? "" : field.getValue( ).trim( ) );
            }
        }
        return mapValues;
    }

    private Entry findEntry( String strColumn )
    {
        return _mapEntriesByName.get( ImportTextUtils.normalize( strColumn ) );
    }

    private ImportColumn standardColumnOf( Entry entry )
    {
        ImportColumn column = _columns.fromHeader( title( entry ) );
        return column != null ? column : _columns.fromHeader( entry.getCode( ) );
    }

    private static List<String> splitChoices( String strValue, EntryKind kind )
    {
        if ( kind != EntryKind.MULTIPLE_CHOICE )
        {
            return List.of( strValue );
        }
        List<String> listChoices = new ArrayList<>( );
        for ( String strChoice : PATTERN_CHOICE_SEPARATOR.split( strValue ) )
        {
            if ( !strChoice.isEmpty( ) )
            {
                listChoices.add( strChoice );
            }
        }
        return listChoices;
    }

    private static Field findChoice( Entry entry, String strChoice )
    {
        String strNormalizedChoice = ImportTextUtils.normalize( strChoice );
        for ( Field field : choices( entry ) )
        {
            if ( strNormalizedChoice.equals( ImportTextUtils.normalize( StringEscapeUtils.unescapeHtml4( field.getTitle( ) ) ) )
                    || strNormalizedChoice.equals( ImportTextUtils.normalize( field.getValue( ) ) ) )
            {
                return field;
            }
        }
        return null;
    }

    private static List<Field> choices( Entry entry )
    {
        List<Field> listChoices = new ArrayList<>( );
        if ( entry.getFields( ) != null )
        {
            for ( Field field : entry.getFields( ) )
            {
                if ( IEntryTypeService.FIELD_ANSWER_CHOICE.equals( field.getCode( ) ) )
                {
                    listChoices.add( field );
                }
            }
        }
        return listChoices;
    }

    private static String choiceTitles( Entry entry )
    {
        return choices( entry ).stream( ).map( field -> StringEscapeUtils.unescapeHtml4( field.getTitle( ) ) ).collect( Collectors.joining( ", " ) );
    }

    /** Entry titles are stored HTML-encoded (for instance {@code CASPE d&#39;affectation}) */
    private static String title( Entry entry )
    {
        return StringEscapeUtils.unescapeHtml4( entry.getTitle( ) );
    }

    private static Response response( Entry entry, String strValue, Field field )
    {
        Response response = new Response( );
        response.setEntry( entry );
        response.setResponseValue( strValue );
        response.setField( field );
        return response;
    }
}
