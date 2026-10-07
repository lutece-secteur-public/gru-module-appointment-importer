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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.portal.service.i18n.I18nService;

/** Produces administrator-downloadable reports from persistent importer data. */
public final class AppointmentImportReportService
{
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern( "dd/MM/uuuu" );
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern( "HH:mm" );
    private static final int ROWS_FOR_WIDTH = 50;
    private static final int MIN_COLUMN_WIDTH = 8;
    private static final int MAX_COLUMN_WIDTH = 80;

    // Light blue-gray header background (RGB 221 235 247)
    private static final byte [ ] HEADER_COLOR = { (byte) 221, (byte) 235, (byte) 247 };

    private AppointmentImportReportService( )
    {
    }

    /**
     * Produces an XLSX workbook with one row per imported appointment.
     *
     * @param nFileId the {@code id_import_file}
     * @param locale  the admin user's locale, used to localise report headers and statuses
     * @return the XLSX bytes
     */
    public static byte [ ] finalReport( int nFileId, Locale locale ) throws IOException
    {
        // Read every row first: the columns of the form fields are the union of their keys
        List<ReportRow> listRows = new ArrayList<>( );
        LinkedHashSet<String> setFieldNames = new LinkedHashSet<>( );
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByFile( nFileId ) )
        {
            for ( AppointmentImportAppointment appointment : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), null ) )
            {
                Map<String, String> mapFields = AppointmentImportJsonService.readMap( appointment.getFormFieldsJson( ) );
                setFieldNames.addAll( mapFields.keySet( ) );
                listRows.add( new ReportRow( batch, appointment, AppointmentImportJsonService.readMap( appointment.getGenericAttributesJson( ) ), mapFields ) );
            }
        }
        ImportColumns columns = ImportColumns.fromProperties( );
        Map<Integer, String> mapReferences = appointmentReferences( nFileId );
        try ( XSSFWorkbook workbook = new XSSFWorkbook( ); ByteArrayOutputStream output = new ByteArrayOutputStream( ) )
        {
            XSSFCellStyle headerStyle = buildHeaderStyle( workbook );

            Sheet sheet = workbook.createSheet( I18nService.getLocalizedString( "module.appointment.importer.report.sheetName", locale ) );
            sheet.createFreezePane( 0, 1 );

            List<String> listHeaders = new ArrayList<>( );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.report.slot", locale ) );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.columnLine", locale ) );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.filterStatus", locale ) );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.report.reference", locale ) );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.report.rdvId", locale ) );
            // The person, as in the workbook, so that a created appointment can be told from another
            for ( ImportColumn column : ImportColumn.values( ) )
            {
                if ( column.getAttributeKey( ) != null )
                {
                    listHeaders.add( columns.getHeader( column ) );
                }
            }
            listHeaders.addAll( setFieldNames );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.report.errorCode", locale ) );
            listHeaders.add( I18nService.getLocalizedString( "module.appointment.importer.report.errorMsg", locale ) );
            Row headerRow = sheet.createRow( 0 );
            for ( int nCol = 0; nCol < listHeaders.size( ); nCol++ )
            {
                Cell cell = headerRow.createCell( nCol );
                cell.setCellValue( listHeaders.get( nCol ) );
                cell.setCellStyle( headerStyle );
            }

            int nRowIndex = 1;
            for ( ReportRow reportRow : listRows )
            {
                AppointmentImportAppointment appt = reportRow._appointment;
                Row row = sheet.createRow( nRowIndex++ );
                int nCol = 0;
                row.createCell( nCol++ ).setCellValue( slotLabel( reportRow._batch ) );
                row.createCell( nCol++ ).setCellValue( appt.getSourceLineNumber( ) );
                row.createCell( nCol++ ).setCellValue( localizeStatus( appt.getStatus( ), locale ) );
                row.createCell( nCol++ ).setCellValue( appt.getIdAppointment( ) == null ? "" : mapReferences.getOrDefault( appt.getIdAppointment( ), "" ) );
                row.createCell( nCol++ ).setCellValue( appt.getIdAppointment( ) == null ? "" : String.valueOf( appt.getIdAppointment( ) ) );
                for ( ImportColumn column : ImportColumn.values( ) )
                {
                    if ( column.getAttributeKey( ) != null )
                    {
                        row.createCell( nCol++ ).setCellValue( reportRow._generic.getOrDefault( column.getAttributeKey( ), "" ) );
                    }
                }
                for ( String strFieldName : setFieldNames )
                {
                    row.createCell( nCol++ ).setCellValue( reportRow._fields.getOrDefault( strFieldName, "" ) );
                }
                row.createCell( nCol++ ).setCellValue( appt.getErrorCode( ) == null ? "" : appt.getErrorCode( ) );
                row.createCell( nCol ).setCellValue( appt.getErrorMessage( ) == null ? "" : appt.getErrorMessage( ) );
            }

            setColumnWidths( sheet, nRowIndex, listHeaders.size( ) );
            workbook.write( output );
            return output.toByteArray( );
        }
    }

    /**
     * @param batch the batch
     * @return its slot, as shown in the reports
     */
    private static String slotLabel( AppointmentImportBatch batch )
    {
        return batch.getStartingDateTime( ).toLocalDate( ).format( FORMAT_DATE ) + "  ·  " + batch.getStartingDateTime( ).toLocalTime( ).format( FORMAT_TIME )
                + " – " + batch.getEndingDateTime( ).toLocalTime( ).format( FORMAT_TIME );
    }

    /**
     * Produces an XLSX workbook listing all validation errors from the stored JSON report.
     *
     * @param strJson the serialised validation report (stored in {@code AppointmentImportFile})
     * @param locale  the admin user's locale, used to localise report headers
     * @return the XLSX bytes
     */
    public static byte [ ] validationReport( String strJson, Locale locale ) throws IOException
    {
        List<AppointmentValidationError> listErrors = AppointmentImportJsonService.readErrors( strJson );
        try ( XSSFWorkbook workbook = new XSSFWorkbook( ); ByteArrayOutputStream output = new ByteArrayOutputStream( ) )
        {
            XSSFCellStyle headerStyle = buildHeaderStyle( workbook );
            Sheet sheet = workbook.createSheet( I18nService.getLocalizedString( "module.appointment.importer.report.sheetNameValidation", locale ) );
            sheet.createFreezePane( 0, 1 );
            String [ ] listHeaders = {
                    I18nService.getLocalizedString( "module.appointment.importer.line",    locale ),
                    I18nService.getLocalizedString( "module.appointment.importer.field",   locale ),
                    I18nService.getLocalizedString( "module.appointment.importer.message", locale ),
            };
            Row headerRow = sheet.createRow( 0 );
            for ( int nCol = 0; nCol < listHeaders.length; nCol++ )
            {
                Cell cell = headerRow.createCell( nCol );
                cell.setCellValue( listHeaders [ nCol ] );
                cell.setCellStyle( headerStyle );
            }
            int nRowIndex = 1;
            for ( AppointmentValidationError error : listErrors )
            {
                Row row = sheet.createRow( nRowIndex++ );
                row.createCell( 0 ).setCellValue( error.getLineNumber( ) == null ? "" : String.valueOf( error.getLineNumber( ) ) );
                row.createCell( 1 ).setCellValue( error.getField( ) == null ? "" : error.getField( ) );
                row.createCell( 2 ).setCellValue( error.getMessage( ) == null ? "" : error.getMessage( ) );
            }
            setColumnWidths( sheet, nRowIndex, listHeaders.length );
            workbook.write( output );
            return output.toByteArray( );
        }
    }

    /**
     * Produces an XLSX workbook containing the rows that could not be imported, with all original columns preserved.
     *
     * @param nFileId the {@code id_import_file}
     * @param locale  the admin user's locale, used to localise report headers
     * @return the XLSX bytes
     */
    public static byte [ ] failedRowsWorkbook( int nFileId, Locale locale ) throws IOException
    {
        List<FailedRow> listFailedRows = new ArrayList<>( );
        LinkedHashSet<String> setExtraFieldNames = new LinkedHashSet<>( );
        for ( AppointmentImportBatch batch : AppointmentImportHome.findBatchesByFile( nFileId ) )
        {
            for ( AppointmentImportAppointment appointment : AppointmentImportHome.findAppointmentsByBatch( batch.getIdImportBatch( ), AppointmentImportStatus.ERROR ) )
            {
                Map<String, String> mapGeneric = AppointmentImportJsonService.readMap( appointment.getGenericAttributesJson( ) );
                Map<String, String> mapFields = AppointmentImportJsonService.readMap( appointment.getFormFieldsJson( ) );
                setExtraFieldNames.addAll( mapFields.keySet( ) );
                listFailedRows.add( new FailedRow( batch, appointment, mapGeneric, mapFields ) );
            }
        }
        try ( XSSFWorkbook workbook = new XSSFWorkbook( ); ByteArrayOutputStream output = new ByteArrayOutputStream( ) )
        {
            XSSFCellStyle headerStyle = buildHeaderStyle( workbook );

            Sheet sheet = workbook.createSheet( I18nService.getLocalizedString( "module.appointment.importer.report.sheetNameFailed", locale ) );
            sheet.createFreezePane( 0, 1 );

            ImportColumns columns = ImportColumns.fromProperties( );
            List<String> listLabels = new ArrayList<>( );
            for ( ImportColumn column : ImportColumn.values( ) )
            {
                listLabels.add( columns.getHeader( column ) );
            }
            listLabels.addAll( setExtraFieldNames );
            listLabels.add( I18nService.getLocalizedString( "module.appointment.importer.report.errorCode", locale ) );
            listLabels.add( I18nService.getLocalizedString( "module.appointment.importer.report.errorMsg",  locale ) );

            Row headerRow = sheet.createRow( 0 );
            for ( int nCol = 0; nCol < listLabels.size( ); nCol++ )
            {
                Cell cell = headerRow.createCell( nCol );
                cell.setCellValue( listLabels.get( nCol ) );
                cell.setCellStyle( headerStyle );
            }

            int nRowIndex = 1;
            for ( FailedRow failed : listFailedRows )
            {
                Row row = sheet.createRow( nRowIndex++ );
                int nCol = 0;
                // Same order as the headers: the order of ImportColumn
                for ( ImportColumn column : ImportColumn.values( ) )
                {
                    row.createCell( nCol++ ).setCellValue( standardValue( column, failed ) );
                }
                for ( String strFieldName : setExtraFieldNames )
                {
                    row.createCell( nCol++ ).setCellValue( failed._fields.getOrDefault( strFieldName, "" ) );
                }
                row.createCell( nCol++ ).setCellValue( failed._appointment.getErrorCode( ) == null ? "" : failed._appointment.getErrorCode( ) );
                row.createCell( nCol ).setCellValue(   failed._appointment.getErrorMessage( ) == null ? "" : failed._appointment.getErrorMessage( ) );
            }
            setColumnWidths( sheet, nRowIndex, listLabels.size( ) );
            workbook.write( output );
            return output.toByteArray( );
        }
    }

    /**
     * Gives the value of a standard column for a failed row, as it was in the workbook.
     *
     * @param column the column
     * @param failed the row
     * @return the value
     */
    private static String standardValue( ImportColumn column, FailedRow failed )
    {
        switch( column )
        {
            case DATE:
                return failed._batch.getStartingDateTime( ).toLocalDate( ).format( FORMAT_DATE );
            case STARTING_TIME:
                return failed._batch.getStartingDateTime( ).toLocalTime( ).format( FORMAT_TIME );
            case ENDING_TIME:
                return failed._batch.getEndingDateTime( ).toLocalTime( ).format( FORMAT_TIME );
            default:
                return failed._generic.getOrDefault( column.getAttributeKey( ), "" );
        }
    }

    /**
     * Reads the references of the appointments of the form of a file through the appointment plugin, in one call rather than one per row.
     *
     * @param nFileId the {@code id_import_file}
     * @return the reference of each appointment of the form
     */
    private static Map<Integer, String> appointmentReferences( int nFileId )
    {
        AppointmentImportFile file = AppointmentImportHome.findFile( nFileId );
        Map<Integer, String> mapReferences = new HashMap<>( );
        if ( file != null )
        {
            for ( Appointment appointment : AppointmentService.findListAppointmentByIdForm( file.getIdForm( ) ) )
            {
                mapReferences.put( appointment.getIdAppointment( ), Objects.toString( appointment.getReference( ), "" ) );
            }
        }
        return mapReferences;
    }

    /**
     * Sizes the columns from the first rows only: autoSizeColumn reads every cell of the sheet, which takes seconds on thousands of rows.
     *
     * @param sheet the sheet
     * @param nRows the number of rows written, header included
     * @param nColumns the number of columns
     */
    private static void setColumnWidths( Sheet sheet, int nRows, int nColumns )
    {
        for ( int nCol = 0; nCol < nColumns; nCol++ )
        {
            int nWidth = MIN_COLUMN_WIDTH;
            for ( int nRow = 0; nRow < Math.min( nRows, ROWS_FOR_WIDTH ); nRow++ )
            {
                Row row = sheet.getRow( nRow );
                Cell cell = row == null ? null : row.getCell( nCol );
                if ( cell != null )
                {
                    nWidth = Math.max( nWidth, cell.toString( ).length( ) + 2 );
                }
            }
            sheet.setColumnWidth( nCol, Math.min( nWidth, MAX_COLUMN_WIDTH ) * 256 );
        }
    }

    private static XSSFCellStyle buildHeaderStyle( XSSFWorkbook workbook )
    {
        Font font = workbook.createFont( );
        font.setBold( true );
        XSSFCellStyle style = workbook.createCellStyle( );
        style.setFont( font );
        style.setFillForegroundColor( new XSSFColor( HEADER_COLOR, null ) );
        style.setFillPattern( FillPatternType.SOLID_FOREGROUND );
        style.setBorderBottom( BorderStyle.THIN );
        return style;
    }

    private static String localizeStatus( String strStatus, Locale locale )
    {
        return I18nService.getLocalizedString( "module.appointment.importer.status." + strStatus, locale );
    }

    private static final class ReportRow
    {
        private final AppointmentImportBatch _batch;
        private final AppointmentImportAppointment _appointment;
        private final Map<String, String> _generic;
        private final Map<String, String> _fields;

        private ReportRow( AppointmentImportBatch batch, AppointmentImportAppointment appointment, Map<String, String> generic, Map<String, String> fields )
        {
            _batch = batch;
            _appointment = appointment;
            _generic = generic;
            _fields = fields;
        }
    }

    private static final class FailedRow
    {
        private final AppointmentImportBatch _batch;
        private final AppointmentImportAppointment _appointment;
        private final Map<String, String> _generic;
        private final Map<String, String> _fields;

        private FailedRow( AppointmentImportBatch batch, AppointmentImportAppointment appointment,
                Map<String, String> generic, Map<String, String> fields )
        {
            _batch = batch;
            _appointment = appointment;
            _generic = generic;
            _fields = fields;
        }
    }
}
