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
 * Messages are resolved via I18nService with Locale.FRANCE (daemon context, no user locale available).
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

    private final String _strCode;

    /**
     * @param strCode   one of the constants defined in this class
     * @param strMsgKey i18n key for the human-readable message stored in the report
     * @param args      optional MessageFormat arguments for the key
     */
    AppointmentImportException( String strCode, String strMsgKey, Object... args )
    {
        super( I18nService.getLocalizedString( strMsgKey, args, Locale.FRANCE ) );
        _strCode = strCode;
    }

    /**
     * @param strCode   one of the constants defined in this class
     * @param strMsgKey i18n key for the human-readable message stored in the report
     * @param cause     the underlying exception, forwarded to the log
     * @param args      optional MessageFormat arguments for the key
     */
    AppointmentImportException( String strCode, String strMsgKey, Throwable cause, Object... args )
    {
        super( I18nService.getLocalizedString( strMsgKey, args, Locale.FRANCE ), cause );
        _strCode = strCode;
    }

    /**
     * Returns the functional error code.
     *
     * @return the error code
     */
    String getCode( )
    {
        return _strCode;
    }
}
