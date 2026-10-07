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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentExcelValidationResult;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;

/**
 * Receives the cells of the first sheet row by row and validates them.
 * <p>
 * Used by {@link AppointmentExcelReader} as the SAX sheet content handler.
 * </p>
 */
final class AppointmentSheetReader implements SheetContentsHandler
{
    // Messages
    private static final String MESSAGE_FIELD_WORKBOOK = "module.appointment.importer.workbook";
    private static final String MESSAGE_FIELD_LINE = "module.appointment.importer.line";
    private static final String ERROR_WORKBOOK_UNREADABLE = "module.appointment.importer.error.workbook.unreadable";
    private static final String ERROR_WORKBOOK_NO_HEADER = "module.appointment.importer.error.workbook.noHeader";
    private static final String ERROR_WORKBOOK_NO_ROWS = "module.appointment.importer.error.workbook.noRows";
    private static final String ERROR_WORKBOOK_TOO_MANY_ROWS = "module.appointment.importer.error.workbook.tooManyRows";
    private static final String ERROR_COLUMN_MISSING = "module.appointment.importer.error.column.missing";
    private static final String ERROR_COLUMN_DUPLICATE = "module.appointment.importer.error.column.duplicate";
    private static final String ERROR_VALUE_REQUIRED = "module.appointment.importer.error.value.required";
    private static final String ERROR_VALUE_TOO_LONG = "module.appointment.importer.error.value.tooLong";
    private static final String ERROR_VALUE_EMAIL = "module.appointment.importer.error.value.email";
    private static final String ERROR_VALUE_PHONE = "module.appointment.importer.error.value.phone";
    private static final String ERROR_VALUE_DATE = "module.appointment.importer.error.value.date";
    private static final String ERROR_VALUE_BIRTH_DATE_FUTURE = "module.appointment.importer.error.value.birthDateFuture";
    private static final String ERROR_VALUE_TIME = "module.appointment.importer.error.value.time";
    private static final String ERROR_VALUE_TIME_ORDER = "module.appointment.importer.error.value.timeOrder";
    private static final String ERROR_VALUE_PAST = "module.appointment.importer.error.value.past";
    private static final String ERROR_ROW_DUPLICATE = "module.appointment.importer.error.row.duplicate";

    // Formats
    private static final DateTimeFormatter FORMAT_DATE_INPUT = DateTimeFormatter.ofPattern( "d/M/uuuu" ).withResolverStyle( ResolverStyle.STRICT );
    private static final DateTimeFormatter FORMAT_DATE_OUTPUT = DateTimeFormatter.ofPattern( "dd/MM/uuuu" );
    private static final DateTimeFormatter FORMAT_TIME_INPUT = DateTimeFormatter.ofPattern( "H:mm[:ss]" ).withResolverStyle( ResolverStyle.STRICT );
    private static final String KEY_SEPARATOR = "\u0000";

    private final ImportValidationSettings _settings;
    private final ImportColumns _columns;
    private final DateCapturingFormatter _formatter = new DateCapturingFormatter( );
    private final List<AppointmentValidationError> _workbookErrors = new ArrayList<>( );
    private final List<AppointmentValidationError> _rowErrors = new ArrayList<>( );
    private final List<AppointmentImportRow> _validRows = new ArrayList<>( );
    private final Map<ImportColumn, Integer> _standardColumns = new EnumMap<>( ImportColumn.class );
    private final Map<Integer, String> _otherColumns = new LinkedHashMap<>( );
    private final Map<String, Integer> _rowKeys = new HashMap<>( );
    private final Map<Integer, ImportCell> _currentRow = new LinkedHashMap<>( );
    private boolean _bHeaderRead;
    private int _nDataRows;

    /**
     * @param settings
     *            what the validation depends on
     */
    AppointmentSheetReader( ImportValidationSettings settings )
    {
        _settings = settings;
        _columns = settings.getColumns( );
    }

    /**
     * Returns the formatter so that AppointmentExcelReader can wire it into XSSFSheetXMLHandler.
     *
     * @return the DateCapturingFormatter owned by this reader
     */
    DataFormatter getFormatter( )
    {
        return _formatter;
    }

    /** Clears the current row buffer at the start of each row. */
    @Override
    public void startRow( int nRowNum )
    {
        _currentRow.clear( );
    }

    /**
     * Stores the formatted cell value and the date captured by the formatter, if any.
     * Called by the SAX handler for each non-empty cell in the current row.
     */
    @Override
    public void cell( String strCellReference, String strFormattedValue, XSSFComment comment )
    {
        LocalDateTime dateTime = _formatter.takeDateTime( );
        if ( strCellReference != null && strFormattedValue != null )
        {
            int nColumn = new CellReference( strCellReference ).getCol( );
            _currentRow.put( nColumn, new ImportCell( strFormattedValue.trim( ), dateTime ) );
        }
    }

    /**
     * Dispatches the completed row to header or data processing, skipping blank rows and rows after a header error.
     */
    @Override
    public void endRow( int nRowNum )
    {
        if ( !_bHeaderRead )
        {
            readHeader( );
            _bHeaderRead = true;

            return;
        }
        // Rows are only checked against a valid header
        if ( !_workbookErrors.isEmpty( ) || isBlankRow( ) )
        {
            return;
        }
        _nDataRows++;
        if ( _nDataRows > _settings.getMaxRows( ) )
        {
            // The rows beyond the limit are counted, not kept, so that a huge file does not fill the memory
            return;
        }
        readRow( nRowNum + 1 );
    }

    /**
     * Returns the validation result after the sheet has been fully parsed.
     *
     * @return valid rows and all collected errors
     */
    AppointmentExcelValidationResult getResult( )
    {
        if ( !_bHeaderRead )
        {
            _workbookErrors.add( AppointmentValidationError.workbook( _settings.message( MESSAGE_FIELD_WORKBOOK ),
                    _settings.message( ERROR_WORKBOOK_NO_HEADER ) ) );
        }
        else
            if ( _workbookErrors.isEmpty( ) && _nDataRows == 0 )
            {
                // A file without rows would be accepted with no batch, and stay pending for ever
                _workbookErrors.add( AppointmentValidationError.workbook( _settings.message( MESSAGE_FIELD_WORKBOOK ),
                        _settings.message( ERROR_WORKBOOK_NO_ROWS ) ) );
            }
        if ( _nDataRows > _settings.getMaxRows( ) )
        {
            _workbookErrors.add( AppointmentValidationError.workbook( _settings.message( MESSAGE_FIELD_WORKBOOK ),
                    _settings.message( ERROR_WORKBOOK_TOO_MANY_ROWS, Integer.toString( _nDataRows ), Integer.toString( _settings.getMaxRows( ) ) ) ) );
        }
        List<AppointmentValidationError> listErrors = new ArrayList<>( _workbookErrors );
        listErrors.addAll( _rowErrors );
        Set<String> otherColumnNames = new LinkedHashSet<>( _otherColumns.values( ) );

        return new AppointmentExcelValidationResult( _validRows, listErrors, otherColumnNames );
    }

    /**
     * Returns a validation result containing a single unreadable-workbook error, used when parsing fails entirely.
     *
     * @return a result with no valid rows and one workbook-level error
     */
    AppointmentExcelValidationResult unreadable( )
    {
        AppointmentValidationError error = AppointmentValidationError.workbook( _settings.message( MESSAGE_FIELD_WORKBOOK ),
                _settings.message( ERROR_WORKBOOK_UNREADABLE ) );

        return new AppointmentExcelValidationResult( List.of( ), List.of( error ), Set.of( ) );
    }

    /**
     * Parses the header row: maps each cell to a standard column, or keeps it as a form field.
     * Detects duplicate column names and missing mandatory columns and records them as workbook-level errors.
     */
    private void readHeader( )
    {
        Map<String, String> mapHeadersByNormalizedName = new HashMap<>( );
        for ( Map.Entry<Integer, ImportCell> cell : _currentRow.entrySet( ) )
        {
            String strHeader = cell.getValue( ).getText( );
            String strNormalizedHeader = ImportTextUtils.normalize( strHeader );
            if ( strNormalizedHeader.isEmpty( ) )
            {
                continue;
            }
            ImportColumn column = _columns.fromHeader( strHeader );
            // A column given both by its header and by its alias is a duplicate too
            String strDuplicateKey = column != null ? column.name( ) : strNormalizedHeader;
            if ( mapHeadersByNormalizedName.putIfAbsent( strDuplicateKey, strHeader ) != null )
            {
                _workbookErrors.add( AppointmentValidationError.workbook( strHeader, _settings.message( ERROR_COLUMN_DUPLICATE ) ) );
                continue;
            }
            if ( column != null )
            {
                _standardColumns.put( column, cell.getKey( ) );
            }
            else
            {
                _otherColumns.put( cell.getKey( ), strHeader );
            }
        }
        for ( ImportColumn column : ImportColumn.values( ) )
        {
            if ( _columns.isMandatory( column ) && !_standardColumns.containsKey( column ) )
            {
                _workbookErrors.add( AppointmentValidationError.workbook( _columns.getHeader( column ), _settings.message( ERROR_COLUMN_MISSING ) ) );
            }
        }
    }

    /**
     * Validates and parses a data row, then appends a valid {@link AppointmentImportRow} to {@code _validRows}
     * or row-level errors to {@code _rowErrors}.
     * Duplicate rows (identical on all standard columns) are detected via a key map.
     *
     * @param nLine 1-based row number used in error messages
     */
    private void readRow( int nLine )
    {
        int nErrorCount = _rowErrors.size( );
        String strLastName = readName( ImportColumn.LAST_NAME, nLine );
        String strFirstName = readName( ImportColumn.FIRST_NAME, nLine );
        String strEmail = readText( ImportColumn.EMAIL, nLine );
        if ( !strEmail.isEmpty( ) && !_settings.isValidEmail( strEmail ) )
        {
            addRowError( nLine, ImportColumn.EMAIL, ERROR_VALUE_EMAIL );
        }
        String strPhoneNumber = readPhoneNumber( nLine );
        LocalDate birthDate = readDate( ImportColumn.BIRTH_DATE, nLine );
        if ( birthDate != null && birthDate.isAfter( _settings.getNow( ).toLocalDate( ) ) )
        {
            addRowError( nLine, ImportColumn.BIRTH_DATE, ERROR_VALUE_BIRTH_DATE_FUTURE );
        }
        LocalDate date = readDate( ImportColumn.DATE, nLine );
        LocalTime startingTime = readTime( ImportColumn.STARTING_TIME, nLine );
        LocalTime endingTime = readTime( ImportColumn.ENDING_TIME, nLine );
        if ( startingTime != null && endingTime != null && !endingTime.isAfter( startingTime ) )
        {
            addRowError( nLine, ImportColumn.ENDING_TIME, ERROR_VALUE_TIME_ORDER );
        }
        if ( date != null && startingTime != null && !date.atTime( startingTime ).isAfter( _settings.getNow( ) ) )
        {
            addRowError( nLine, ImportColumn.DATE, ERROR_VALUE_PAST );
        }
        if ( _rowErrors.size( ) > nErrorCount )
        {
            return;
        }

        String strBirthDate = birthDate == null ? "" : birthDate.format( FORMAT_DATE_OUTPUT );
        String strRowKey = String.join( KEY_SEPARATOR, ImportTextUtils.normalize( strLastName ), ImportTextUtils.normalize( strFirstName ),
                ImportTextUtils.normalize( strEmail ), strPhoneNumber, strBirthDate, date.toString( ), startingTime.toString( ), endingTime.toString( ) );
        Integer nFirstLine = _rowKeys.putIfAbsent( strRowKey, nLine );
        if ( nFirstLine != null )
        {
            _rowErrors.add( AppointmentValidationError.row( nLine, _settings.message( MESSAGE_FIELD_LINE ),
                    _settings.message( ERROR_ROW_DUPLICATE, Integer.toString( nFirstLine ) ) ) );

            return;
        }

        Map<String, String> mapGenericAttributes = new LinkedHashMap<>( );
        mapGenericAttributes.put( AppointmentImportRow.ATTRIBUTE_LAST_NAME, strLastName );
        mapGenericAttributes.put( AppointmentImportRow.ATTRIBUTE_FIRST_NAME, strFirstName );
        mapGenericAttributes.put( AppointmentImportRow.ATTRIBUTE_EMAIL, strEmail );
        mapGenericAttributes.put( AppointmentImportRow.ATTRIBUTE_BIRTH_DATE, strBirthDate );
        mapGenericAttributes.put( AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, strPhoneNumber );
        Map<String, String> mapOtherValues = new LinkedHashMap<>( );
        for ( Map.Entry<Integer, String> column : _otherColumns.entrySet( ) )
        {
            mapOtherValues.put( column.getValue( ), getCell( column.getKey( ) ).getText( ) );
        }
        _validRows.add( new AppointmentImportRow( nLine, mapGenericAttributes, mapOtherValues, date, startingTime, endingTime ) );
    }

    /**
     * Reads a text cell value, recording an error if the column is mandatory and the cell is empty.
     *
     * @param column the column to read
     * @param nLine  1-based row number used in error messages
     * @return the trimmed cell text, or an empty string
     */
    private String readText( ImportColumn column, int nLine )
    {
        String strValue = getCell( column ).getText( );
        if ( strValue.isEmpty( ) && _columns.isMandatory( column ) )
        {
            addRowError( nLine, column, ERROR_VALUE_REQUIRED );
        }

        return strValue;
    }

    /**
     * Reads a last or first name, recording an error if it is missing or too long.
     *
     * @param column the column to read
     * @param nLine  1-based row number used in error messages
     * @return the trimmed cell text, or an empty string
     */
    private String readName( ImportColumn column, int nLine )
    {
        String strValue = readText( column, nLine );
        if ( strValue.length( ) > _settings.getMaxNameLength( ) )
        {
            _rowErrors.add( AppointmentValidationError.row( nLine, _columns.getHeader( column ),
                    _settings.message( ERROR_VALUE_TOO_LONG, Integer.toString( _settings.getMaxNameLength( ) ) ) ) );
        }

        return strValue;
    }

    /**
     * Reads the phone number without its separators, restoring the leading zero a numeric cell drops, and checks its format.
     *
     * @param nLine 1-based row number used in error messages
     * @return the phone number, or an empty string
     */
    private String readPhoneNumber( int nLine )
    {
        String strPhoneNumber = ImportTextUtils.normalizePhoneNumber( readText( ImportColumn.PHONE_NUMBER, nLine ) );
        if ( !strPhoneNumber.isEmpty( ) && !_settings.isValidPhoneNumber( strPhoneNumber ) )
        {
            addRowError( nLine, ImportColumn.PHONE_NUMBER, ERROR_VALUE_PHONE );
        }

        return strPhoneNumber;
    }

    /**
     * Reads a date cell: uses the POI-captured {@link LocalDateTime} when the cell is a native Excel date,
     * otherwise falls back to text parsing ({@code dd/MM/yyyy}).
     * Appends a row error if the cell is unparseable, or empty in a mandatory column.
     *
     * @param column the column to read
     * @param nLine  1-based row number used in error messages
     * @return the parsed date, or {@code null} if the cell is empty or invalid
     */
    private LocalDate readDate( ImportColumn column, int nLine )
    {
        ImportCell cell = getCell( column );
        if ( cell.getDateTime( ) != null )
        {
            return cell.getDateTime( ).toLocalDate( );
        }
        if ( readText( column, nLine ).isEmpty( ) )
        {
            return null;
        }
        LocalDate date = parseDate( cell.getText( ) );
        if ( date == null )
        {
            addRowError( nLine, column, ERROR_VALUE_DATE );
        }

        return date;
    }

    /**
     * Reads a time cell: uses the POI-captured {@link LocalDateTime} when the cell is a native Excel time,
     * otherwise falls back to text parsing ({@code HH:mm} or {@code HH:mm:ss}).
     * Appends a row error if the cell is empty or unparseable.
     *
     * @param column the column to read
     * @param nLine  1-based row number used in error messages
     * @return the parsed time, or {@code null} if validation failed
     */
    private LocalTime readTime( ImportColumn column, int nLine )
    {
        ImportCell cell = getCell( column );
        if ( cell.getDateTime( ) != null )
        {
            return cell.getDateTime( ).toLocalTime( );
        }
        if ( readText( column, nLine ).isEmpty( ) )
        {
            return null;
        }
        LocalTime time = parseTime( cell.getText( ) );
        if ( time == null )
        {
            addRowError( nLine, column, ERROR_VALUE_TIME );
        }

        return time;
    }

    /**
     * Returns true if every cell in the current row is empty.
     *
     * @return true if the row is blank
     */
    private boolean isBlankRow( )
    {
        for ( ImportCell cell : _currentRow.values( ) )
        {
            if ( !cell.getText( ).isEmpty( ) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the cell for the given standard column, or an empty cell if the column was not found in the header.
     *
     * @param column the column to look up
     * @return the cell, never null
     */
    private ImportCell getCell( ImportColumn column )
    {
        Integer nColumn = _standardColumns.get( column );

        return nColumn == null ? ImportCell.EMPTY : getCell( nColumn );
    }

    /**
     * Returns the cell at the given 0-based column index, or an empty cell if absent.
     *
     * @param nColumn 0-based column index
     * @return the cell, never null
     */
    private ImportCell getCell( int nColumn )
    {
        return _currentRow.getOrDefault( nColumn, ImportCell.EMPTY );
    }

    /**
     * Appends a row-level validation error for the given column.
     *
     * @param nLine         1-based row number
     * @param column        the column in error
     * @param strMessageKey i18n key for the error message
     */
    private void addRowError( int nLine, ImportColumn column, String strMessageKey )
    {
        _rowErrors.add( AppointmentValidationError.row( nLine, _columns.getHeader( column ), _settings.message( strMessageKey ) ) );
    }

    /**
     * Parses a dd/MM/yyyy text date
     *
     * @return the date, or null if the text is not a valid date
     */
    private static LocalDate parseDate( String strValue )
    {
        try
        {
            return LocalDate.parse( strValue, FORMAT_DATE_INPUT );
        }
        catch( DateTimeParseException e )
        {
            return null;
        }
    }

    /**
     * Parses a HH:mm or HH:mm:ss text time
     *
     * @return the time, or null if the text is not a valid time
     */
    private static LocalTime parseTime( String strValue )
    {
        try
        {
            return LocalTime.parse( strValue, FORMAT_TIME_INPUT );
        }
        catch( DateTimeParseException e )
        {
            return null;
        }
    }
}
