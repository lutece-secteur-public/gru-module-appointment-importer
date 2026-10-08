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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.portal.service.admin.AdminUserService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * What the validation of a workbook depends on: the columns, the messages, the email check, the current date and the limits.
 * <p>
 * {@link #fromLutece(Locale)} reads them from Lutece; a test builds them with the constructor.
 * </p>
 */
public final class ImportValidationSettings
{
    private static final String PROPERTY_MAX_ROWS = "appointment-importer.maxRows";
    private static final String PROPERTY_MAX_NAME_LENGTH = "appointment-importer.maxNameLength";
    private static final String PROPERTY_PHONE_PATTERN = "appointment-importer.phonePattern";
    private static final int DEFAULT_MAX_ROWS = 20000;
    private static final int DEFAULT_MAX_NAME_LENGTH = 100;
    private static final String DEFAULT_PHONE_PATTERN = "0[0-9]{9}";
    private static final String PROPERTY_EMAIL_FIELD_PATTERN = "appointment-importer.emailFieldPattern";
    /** Found in the normalized title or code of a field: "email du CAR", "email_car", "Courriel" */
    public static final String DEFAULT_EMAIL_FIELD_PATTERN = "(^|[ -])(e-?mail|mail|courriel)($|[ -])";

    private final ImportColumns _columns;
    private final BiFunction<String, Object [ ], String> _messages;
    private final Predicate<String> _emailChecker;
    private final LocalDateTime _dtNow;
    private final int _nMaxRows;
    private final int _nMaxNameLength;
    private final Pattern _patternPhone;
    private final Pattern _patternEmailField;

    /**
     * @param columns
     *            the standard columns
     * @param messages
     *            gives the localized message of a key and its arguments
     * @param emailChecker
     *            tells whether an email is valid
     * @param dtNow
     *            the current date: an appointment cannot start before it
     * @param nMaxRows
     *            the maximum number of rows of a workbook
     * @param nMaxNameLength
     *            the maximum length of a last or first name
     * @param strPhonePattern
     *            the pattern a phone number must match once its separators are removed
     * @param strEmailFieldPattern
     *            the pattern found in the normalized title or code of the form fields whose value must be an email
     */
    public ImportValidationSettings( ImportColumns columns, BiFunction<String, Object [ ], String> messages, Predicate<String> emailChecker,
            LocalDateTime dtNow, int nMaxRows, int nMaxNameLength, String strPhonePattern, String strEmailFieldPattern )
    {
        _columns = columns;
        _messages = messages;
        _emailChecker = emailChecker;
        _dtNow = dtNow;
        _nMaxRows = nMaxRows;
        _nMaxNameLength = nMaxNameLength;
        _patternPhone = Pattern.compile( strPhonePattern );
        _patternEmailField = Pattern.compile( strEmailFieldPattern );
    }

    /**
     * @param locale
     *            the locale of the messages
     * @return the settings of the running Lutece
     */
    public static ImportValidationSettings fromLutece( Locale locale )
    {
        return new ImportValidationSettings( ImportColumns.fromProperties( ), ( strKey, args ) -> I18nService.getLocalizedString( strKey, args, locale ),
                // Emails are checked as the back office does: pattern or regular expressions, and banned domains
                AdminUserService::checkEmail, LocalDateTime.now( ), AppPropertiesService.getPropertyInt( PROPERTY_MAX_ROWS, DEFAULT_MAX_ROWS ),
                AppPropertiesService.getPropertyInt( PROPERTY_MAX_NAME_LENGTH, DEFAULT_MAX_NAME_LENGTH ),
                AppPropertiesService.getProperty( PROPERTY_PHONE_PATTERN, DEFAULT_PHONE_PATTERN ),
                AppPropertiesService.getProperty( PROPERTY_EMAIL_FIELD_PATTERN, DEFAULT_EMAIL_FIELD_PATTERN ) );
    }

    public ImportColumns getColumns( )
    {
        return _columns;
    }

    /**
     * @param strKey
     *            the i18n key
     * @param arguments
     *            the arguments of the message
     * @return the localized message
     */
    public String message( String strKey, Object... arguments )
    {
        return _messages.apply( strKey, arguments );
    }

    /**
     * @param strEmail
     *            an email
     * @return true if it is valid
     */
    public boolean isValidEmail( String strEmail )
    {
        return _emailChecker.test( strEmail );
    }

    /**
     * @param strPhoneNumber
     *            a phone number, separators removed
     * @return true if it is valid
     */
    public boolean isValidPhoneNumber( String strPhoneNumber )
    {
        return _patternPhone.matcher( strPhoneNumber ).matches( );
    }

    /**
     * @param strNormalizedName
     *            the normalized title or code of a form field
     * @return true if the value of the field must be an email
     */
    public boolean isEmailField( String strNormalizedName )
    {
        return strNormalizedName != null && _patternEmailField.matcher( strNormalizedName ).find( );
    }

    public LocalDateTime getNow( )
    {
        return _dtNow;
    }

    public int getMaxRows( )
    {
        return _nMaxRows;
    }

    public int getMaxNameLength( )
    {
        return _nMaxNameLength;
    }
}
