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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;

/**
 * Builds the workbooks and the validation settings of the tests, without Lutece.
 */
final class ImportTestUtils
{
    /** The current date of the tests: the appointments of the workbooks are after it */
    static final LocalDateTime NOW = LocalDateTime.of( 2026, 10, 1, 12, 0 );

    /** The headers of the workbook supplied by the business, with the trailing space of its email column */
    static final List<String> HEADERS = Arrays.asList( "Nom", "Prénom", "email ", "Téléphone", "Date", "Heure de début", "Heure de fin",
            "Date de naissance", "CASPE d'affectation", "email du CAR" );

    private ImportTestUtils( )
    {
    }

    /**
     * @param nMaxRows
     *            the maximum number of rows
     * @return settings whose messages are the key followed by the arguments, so that a test can check the key
     */
    static ImportValidationSettings settings( int nMaxRows )
    {
        return new ImportValidationSettings( ImportColumns.defaults( ), ( strKey, args ) -> args.length == 0 ? strKey
                : strKey + " " + Arrays.stream( args ).map( String::valueOf ).collect( Collectors.joining( " " ) ), strEmail -> strEmail.contains( "@" ),
                NOW, nMaxRows, 100, "0[0-9]{9}", ImportValidationSettings.DEFAULT_EMAIL_FIELD_PATTERN );
    }

    /**
     * Writes a workbook: a header row, then one row per array. A {@link LocalDate} or a {@link LocalTime} becomes a native Excel date or time, a
     * {@link Double} a numeric cell, anything else a text cell.
     *
     * @param listHeaders
     *            the headers
     * @param rows
     *            the rows
     * @return the content of the workbook
     * @throws IOException
     *             if the workbook cannot be written
     */
    static byte [ ] workbook( List<String> listHeaders, Object [ ]... rows ) throws IOException
    {
        try ( XSSFWorkbook workbook = new XSSFWorkbook( ); ByteArrayOutputStream output = new ByteArrayOutputStream( ) )
        {
            CellStyle styleDate = workbook.createCellStyle( );
            styleDate.setDataFormat( workbook.createDataFormat( ).getFormat( "dd/mm/yyyy" ) );
            CellStyle styleTime = workbook.createCellStyle( );
            styleTime.setDataFormat( workbook.createDataFormat( ).getFormat( "hh:mm" ) );
            Sheet sheet = workbook.createSheet( "Feuil1" );
            Row header = sheet.createRow( 0 );
            for ( int nCol = 0; nCol < listHeaders.size( ); nCol++ )
            {
                header.createCell( nCol ).setCellValue( listHeaders.get( nCol ) );
            }
            for ( int nRow = 0; nRow < rows.length; nRow++ )
            {
                Row row = sheet.createRow( nRow + 1 );
                for ( int nCol = 0; nCol < rows [nRow].length; nCol++ )
                {
                    Object value = rows [nRow] [nCol];
                    if ( value == null )
                    {
                        continue;
                    }
                    Cell cell = row.createCell( nCol );
                    if ( value instanceof LocalDate )
                    {
                        cell.setCellValue( ( (LocalDate) value ).atStartOfDay( ) );
                        cell.setCellStyle( styleDate );
                    }
                    else if ( value instanceof LocalTime )
                    {
                        // An Excel time is a fraction of a day
                        cell.setCellValue( ( (LocalTime) value ).toSecondOfDay( ) / 86400d );
                        cell.setCellStyle( styleTime );
                    }
                    else if ( value instanceof Double )
                    {
                        cell.setCellValue( (Double) value );
                    }
                    else
                    {
                        cell.setCellValue( value.toString( ) );
                    }
                }
            }
            workbook.write( output );
            return output.toByteArray( );
        }
    }

    /**
     * @return a row of the workbook supplied by the business: the phone number is a numeric cell, dates and times are native
     */
    static Object [ ] sampleRow( String strLastName, String strEmail, LocalDate date, LocalTime start, LocalTime end )
    {
        return new Object [ ] {
                strLastName, "Tata", strEmail, 781995425d, date, start, end, LocalDate.of( 1988, 12, 12 ), "CASPE 18", "cartest@gmail.com"
        };
    }
}
