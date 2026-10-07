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

import java.util.Locale;

import fr.paris.lutece.portal.service.i18n.I18nService;

/**
 * Thrown by AppointmentServiceImporter when an appointment cannot be created.
 * Carries a functional error code readable by the daemon and stored in the report.
 * The message is resolved in the default locale of Lutece (the daemon has no user locale); {@link #getMessage(Locale)} resolves it in another one.
 */
final class AppointmentImportException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** The slot does not exist in the form calendar for the requested interval. */
    static final String SLOT_NOT_FOUND = "SLOT_NOT_FOUND";

    /** The slot exists but is full or closed. */
    static final String SLOT_FULL = "SLOT_FULL";

    /** The form has been deactivated since the file was uploaded. */
    static final String FORM_INACTIVE = "FORM_INACTIVE";

    /** Appointment creation failed for an unexpected reason. */
    static final String SAVE_FAILED = "SAVE_FAILED";

    /** The processing of the row was interrupted: the appointment may or may not have been created. */
    static final String INTERRUPTED = "INTERRUPTED";

    private final String _strCode;
    private final String _strMessageKey;
    private final transient Object [ ] _arguments;

    /**
     * @param strCode
     *            the functional error code
     * @param strMessageKey
     *            the i18n key of the message
     * @param arguments
     *            the arguments of the message
     */
    AppointmentImportException( String strCode, String strMessageKey, Object... arguments )
    {
        this( strCode, strMessageKey, null, arguments );
    }

    /**
     * @param strCode
     *            the functional error code
     * @param strMessageKey
     *            the i18n key of the message
     * @param cause
     *            the cause
     * @param arguments
     *            the arguments of the message
     */
    AppointmentImportException( String strCode, String strMessageKey, Throwable cause, Object... arguments )
    {
        super( I18nService.getLocalizedString( strMessageKey, arguments, I18nService.getDefaultLocale( ) ), cause );
        _strCode = strCode;
        _strMessageKey = strMessageKey;
        _arguments = arguments;
    }

    /**
     * @return the functional error code
     */
    String getCode( )
    {
        return _strCode;
    }

    /**
     * @param locale
     *            the locale
     * @return the message in this locale
     */
    String getMessage( Locale locale )
    {
        return I18nService.getLocalizedString( _strMessageKey, _arguments, locale );
    }
}
