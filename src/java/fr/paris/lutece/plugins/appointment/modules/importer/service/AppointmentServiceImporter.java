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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;

import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.business.user.User;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.util.ImportTextUtils;
import fr.paris.lutece.plugins.appointment.service.AppointmentPlugin;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.EntryService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.ReservationRuleService;
import fr.paris.lutece.plugins.appointment.service.SlotSafeService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.WeekDefinitionService;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.util.sql.TransactionManager;

/**
 * Creates appointments through the Appointment plugin service.
 */
public final class AppointmentServiceImporter
{
    private static final String IMPORT_ACCESS_CODE = "admin";
    private static final DateTimeFormatter FORMAT_DT = DateTimeFormatter.ofPattern( "dd/MM/uuuu HH:mm" );

    /**
     * Validates the form and prepares (creates if needed) the slots for the given interval.
     * Must be called once per batch before calling {@link #importAppointment(int, Map, List)}.
     *
     * @param nFormId    the form identifier
     * @param dtStarting start of the slot interval
     * @param dtEnding   end of the slot interval
     * @return the list of persisted slots covering the interval
     * @throws IllegalArgumentException if the form is inactive or the slots are unavailable
     */
    public List<Slot> prepareSlots( int nFormId, LocalDateTime dtStarting, LocalDateTime dtEnding )
    {
        validateForm( nFormId );
        return findAndPersistSlots( nFormId, dtStarting, dtEnding );
    }

    /**
     * Creates an appointment from a map of generic attributes and a pre-prepared list of slots.
     * Every database update for this row is committed together or rolled back together.
     *
     * @param nFormId              the form identifier
     * @param mapGenericAttributes the generic attribute values
     * @param listSlots            the slots prepared by {@link #prepareSlots(int, LocalDateTime, LocalDateTime)}
     * @return created appointment identifier
     */
    public int importAppointment( int nFormId, Map<String, String> mapGenericAttributes, List<Slot> listSlots )
    {
        String strLastName = required( mapGenericAttributes, "lastName" );
        String strFirstName = required( mapGenericAttributes, "firstName" );
        String strEmail = required( mapGenericAttributes, "email" );
        String strPhoneNumber = mapGenericAttributes.getOrDefault( "phoneNumber", "" );
        TransactionManager.beginTransaction( AppointmentPlugin.getPlugin( ) );
        try
        {
            User user = new User( );
            user.setLastName( strLastName );
            user.setFirstName( strFirstName );
            user.setEmail( strEmail );
            user.setPhoneNumber( strPhoneNumber );
            AppointmentDTO appointment = new AppointmentDTO( );
            appointment.setIdForm( nFormId );
            appointment.setFirstName( strFirstName );
            appointment.setLastName( strLastName );
            appointment.setEmail( strEmail );
            appointment.setPhoneNumber( strPhoneNumber );
            appointment.setUser( user );
            appointment.setSlot( listSlots );
            appointment.setNbBookedSeats( 1 );
            appointment.setOverbookingAllowed( false );
            appointment.setAdminUserCreate( IMPORT_ACCESS_CODE );
            appointment.setListResponse( responses( nFormId, mapGenericAttributes ) );
            int nAppointmentId = AppointmentService.saveAppointment( appointment );
            TransactionManager.commitTransaction( AppointmentPlugin.getPlugin( ) );
            return nAppointmentId;
        }
        catch( RuntimeException e )
        {
            TransactionManager.rollBack( AppointmentPlugin.getPlugin( ), e );
            throw new AppointmentImportException( AppointmentImportException.SAVE_FAILED,
                    "module.appointment.importer.error.import.saveFailed", e, e.getMessage( ) );
        }
    }

    /**
     * Builds the list of generic-attribute responses from the row values, matching columns by code or title.
     *
     * @param nFormId the form identifier
     * @param mapAll  all attribute values for the row (generic + extra fields)
     * @return the responses to attach to the appointment
     */
    private List<Response> responses( int nFormId, Map<String, String> mapAll )
    {

        Map<String, Entry> mapEntries = new HashMap<>( );
        for ( Entry entry : EntryService.getFilter( nFormId, true ) )
        {
            mapEntries.put( ImportTextUtils.normalize( entry.getCode( ) ), entry );
            mapEntries.put( ImportTextUtils.normalize( StringEscapeUtils.unescapeHtml4( entry.getTitle( ) ) ), entry );
        }

        List<Response> listResult = new ArrayList<>( );
        Set<String> standardAttributeKeys = new HashSet<>( );

        for ( ImportColumn column : ImportColumn.values( ) )
        {
            if ( column.getAttributeKey( ) == null )
            {
                continue;
            }
            standardAttributeKeys.add( column.getAttributeKey( ) );
            Entry entry = mapEntries.get( column.getNormalizedHeader( ) );
            String strValue = mapAll.getOrDefault( column.getAttributeKey( ), "" );
            if ( entry != null && !strValue.isEmpty( ) )
            {
                Response response = new Response( );
                response.setEntry( entry );
                response.setResponseValue( strValue );
                listResult.add( response );
            }
        }

        // Extra form fields: keyed by their display title (as read from the Excel header)
        for ( Map.Entry<String, String> e : mapAll.entrySet( ) )
        {
            if ( standardAttributeKeys.contains( e.getKey( ) ) || e.getValue( ).isEmpty( ) )
            {
                continue;
            }
            Entry entry = mapEntries.get( ImportTextUtils.normalize( e.getKey( ) ) );
            if ( entry != null )
            {
                Response response = new Response( );
                response.setEntry( entry );
                response.setResponseValue( e.getValue( ) );
                listResult.add( response );
            }
        }

        return listResult;
    }

    /**
     * Returns the value for the given key, throwing if it is absent or blank.
     *
     * @param mapValues the attribute map
     * @param strKey    the required key
     * @return the non-blank value
     */
    private String required( Map<String, String> mapValues, String strKey )
    {
        String strValue = mapValues.get( strKey );
        if ( strValue == null || strValue.trim( ).isEmpty( ) )
        {
            throw new IllegalArgumentException( "Missing required generic attribute: " + strKey );
        }
        return strValue;
    }

    /**
     * Throws if the form does not exist or is inactive.
     *
     * @param nFormId the form identifier
     */
    private void validateForm( int nFormId )
    {
        Form form = FormService.findFormLightByPrimaryKey( nFormId );
        if ( form == null || !form.getIsActive( ) )
        {
            throw new AppointmentImportException( AppointmentImportException.FORM_INACTIVE,
                    "module.appointment.importer.error.import.formInactive" );
        }
    }

    /**
     * Finds slots covering the given interval and persists any that don't exist yet.
     *
     * @param nFormId    the form identifier
     * @param dtStarting start of the interval
     * @param dtEnding   end of the interval
     * @return the persisted slots, sorted by starting datetime
     */
    private List<Slot> findAndPersistSlots( int nFormId, LocalDateTime dtStarting, LocalDateTime dtEnding )
    {
        List<WeekDefinition> listWeekDefinitions = WeekDefinitionService.findListWeekDefinition( nFormId );
        Map<WeekDefinition, ReservationRule> mapReservationRules = ReservationRuleService.findAllReservationRule( nFormId, listWeekDefinitions );
        List<Slot> listSlots = SlotService.buildListSlot( nFormId, mapReservationRules, dtStarting.toLocalDate( ), dtStarting.toLocalDate( ) )
                .stream( )
                .filter( slot -> !slot.getStartingDateTime( ).isBefore( dtStarting )
                        && !slot.getEndingDateTime( ).isAfter( dtEnding ) )
                .sorted( Comparator.comparing( Slot::getStartingDateTime ) )
                .collect( Collectors.toList( ) );
        validateSlots( listSlots, dtStarting, dtEnding );
        return listSlots.stream( )
                .map( slot -> slot.getIdSlot( ) == 0 ? SlotSafeService.createSlot( slot ) : SlotService.findSlotById( slot.getIdSlot( ) ) )
                .collect( Collectors.toList( ) );
    }

    /**
     * Throws if the slots don't exactly cover the requested interval or contain gaps or unavailable slots.
     *
     * @param listSlots  slots to validate
     * @param dtStarting expected interval start
     * @param dtEnding   expected interval end
     */
    private void validateSlots( List<Slot> listSlots, LocalDateTime dtStarting, LocalDateTime dtEnding )
    {
        if ( listSlots.isEmpty( )
                || !listSlots.get( 0 ).getStartingDateTime( ).isEqual( dtStarting )
                || !listSlots.get( listSlots.size( ) - 1 ).getEndingDateTime( ).isEqual( dtEnding ) )
        {
            throw new AppointmentImportException( AppointmentImportException.SLOT_NOT_FOUND,
                    "module.appointment.importer.error.import.slotNotFound",
                    FORMAT_DT.format( dtStarting ), FORMAT_DT.format( dtEnding ) );
        }
        LocalDateTime dtPreviousEnd = null;
        for ( Slot slot : listSlots )
        {
            if ( dtPreviousEnd != null && !dtPreviousEnd.isEqual( slot.getStartingDateTime( ) ) )
            {
                throw new AppointmentImportException( AppointmentImportException.SLOT_NOT_FOUND,
                        "module.appointment.importer.error.import.slotGap",
                        FORMAT_DT.format( dtPreviousEnd ), FORMAT_DT.format( slot.getStartingDateTime( ) ) );
            }
            if ( !slot.getIsOpen( ) || slot.getNbRemainingPlaces( ) < 1 || slot.getNbPotentialRemainingPlaces( ) < 1 )
            {
                throw new AppointmentImportException( AppointmentImportException.SLOT_FULL,
                        "module.appointment.importer.error.import.slotFull",
                        FORMAT_DT.format( slot.getStartingDateTime( ) ), FORMAT_DT.format( slot.getEndingDateTime( ) ) );
            }
            dtPreviousEnd = slot.getEndingDateTime( );
        }
    }
}
