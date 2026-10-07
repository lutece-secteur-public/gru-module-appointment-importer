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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds valid workbook rows, every validation error found while reading a workbook,
 * and the set of extra (non-standard) column names detected in the header row.
 */
public final class AppointmentExcelValidationResult
{
    private final List<AppointmentImportRow> _validRows;
    private final List<AppointmentValidationError> _errors;
    private final Set<String> _otherColumnNames;

    public AppointmentExcelValidationResult( List<AppointmentImportRow> validRows, List<AppointmentValidationError> errors, Set<String> otherColumnNames )
    {
        _validRows = List.copyOf( validRows );
        _errors = List.copyOf( errors );
        // Keeps the order of the workbook, so that the errors on the columns follow it
        _otherColumnNames = Collections.unmodifiableSet( new LinkedHashSet<>( otherColumnNames ) );
    }

    public List<AppointmentImportRow> getValidRows( )
    {
        return _validRows;
    }

    public List<AppointmentValidationError> getErrors( )
    {
        return _errors;
    }

    public boolean hasErrors( )
    {
        return !_errors.isEmpty( );
    }

    /**
     * Get collection of non-generic columns
     * @return
     */
    public Set<String> getOtherColumnNames( )
    {
        return _otherColumnNames;
    }
}
