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

/**
 * Number of rows of a file or a batch, by outcome.
 */
public final class ImportCounts
{
    private int _nCreated;
    private int _nErrors;
    private int _nTotal;

    /**
     * Adds rows with the given status.
     *
     * @param strStatus
     *            the status of the rows
     * @param nCount
     *            their number
     */
    public void add( String strStatus, int nCount )
    {
        if ( AppointmentImportStatus.CREATED.equals( strStatus ) )
        {
            _nCreated += nCount;
        }
        else
            if ( AppointmentImportStatus.ERROR.equals( strStatus ) )
            {
                _nErrors += nCount;
            }
        _nTotal += nCount;
    }

    /**
     * @return the number of appointments created
     */
    public int getCreated( )
    {
        return _nCreated;
    }

    /**
     * @return the number of rows in error
     */
    public int getErrors( )
    {
        return _nErrors;
    }

    /**
     * @return the number of rows not processed yet
     */
    public int getPending( )
    {
        return _nTotal - _nCreated - _nErrors;
    }

    /**
     * @return the number of rows
     */
    public int getTotal( )
    {
        return _nTotal;
    }
}
