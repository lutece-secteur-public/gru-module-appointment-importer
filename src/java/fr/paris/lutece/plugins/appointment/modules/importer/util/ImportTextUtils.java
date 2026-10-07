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
package fr.paris.lutece.plugins.appointment.modules.importer.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text helpers shared by the workbook reader and the appointment creation.
 */
public final class ImportTextUtils
{
    private static final Pattern PATTERN_DIACRITICS = Pattern.compile( "\\p{M}+" );
    private static final Pattern PATTERN_SPACES = Pattern.compile( "[\\s\\u00A0_]+" );
    private static final Pattern PATTERN_PHONE_SEPARATORS = Pattern.compile( "[\\s\\u00A0.\\-/()]" );
    // A French number typed in a numeric cell loses its leading zero: 0612345678 is read 612345678
    private static final Pattern PATTERN_PHONE_WITHOUT_LEADING_ZERO = Pattern.compile( "[1-9][0-9]{8}" );

    /**
     * Private constructor - this class need not be instantiated
     */
    private ImportTextUtils( )
    {
    }

    /**
     * Normalizes a text for comparison: accents removed, blanks (including non-breaking spaces and underscores) trimmed and collapsed, lower case.
     *
     * @param strValue
     *            the text, may be null
     * @return the normalized text, never null
     */
    public static String normalize( String strValue )
    {
        if ( strValue == null )
        {
            return "";
        }
        String strWithoutAccents = PATTERN_DIACRITICS.matcher( Normalizer.normalize( strValue, Normalizer.Form.NFD ) ).replaceAll( "" );

        return PATTERN_SPACES.matcher( strWithoutAccents ).replaceAll( " " ).trim( ).toLowerCase( Locale.ROOT );
    }

    /**
     * Removes the separators of a phone number and restores the leading zero a numeric Excel cell drops.
     *
     * @param strValue
     *            the phone number, may be null
     * @return the phone number without separators, never null
     */
    public static String normalizePhoneNumber( String strValue )
    {
        String strPhoneNumber = strValue == null ? "" : PATTERN_PHONE_SEPARATORS.matcher( strValue ).replaceAll( "" );
        return PATTERN_PHONE_WITHOUT_LEADING_ZERO.matcher( strPhoneNumber ).matches( ) ? "0" + strPhoneNumber : strPhoneNumber;
    }
}
