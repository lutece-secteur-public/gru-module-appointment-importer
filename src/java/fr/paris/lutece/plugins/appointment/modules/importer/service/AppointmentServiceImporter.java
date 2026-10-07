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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.business.user.User;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportRow;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.ReservationRuleService;
import fr.paris.lutece.plugins.appointment.service.SlotSafeService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.WeekDefinitionService;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;

/**
 * Creates appointments through the Appointment plugin service.
 */
public final class AppointmentServiceImporter
{
    private static final DateTimeFormatter FORMAT_DT = DateTimeFormatter.ofPattern( "dd/MM/uuuu HH:mm" );

    /**
     * Validates the form and prepares (creates if needed) the slots for the given interval.
     * Must be called once per batch before calling {@link #importAppointment(int, String, Map, Map, AppointmentFormEntries, List)}.
     *
     * @param nFormId    the form identifier
     * @param dtStarting start of the slot interval
     * @param dtEnding   end of the slot interval
     * @return the list of persisted slots covering the interval
     * @throws AppointmentImportException if the form is inactive or the slots are unavailable
     */
    public List<Slot> prepareSlots( int nFormId, LocalDateTime dtStarting, LocalDateTime dtEnding )
    {
        validateForm( nFormId );
        return findSlots( nFormId, dtStarting, dtEnding ).stream( )
                .map( slot -> slot.getIdSlot( ) == 0 ? SlotSafeService.createSlot( slot ) : SlotService.findSlotById( slot.getIdSlot( ) ) )
                .collect( Collectors.toList( ) );
    }

    /**
     * Creates an appointment from the values of a row and a pre-prepared list of slots.
     * The appointment plugin saves it in its own transaction, under a lock on each slot: this method must not open another one around it, otherwise the
     * lock would be released before the data is committed.
     *
     * @param nFormId              the form identifier
     * @param strAdminAccessCode   the access code of the administrator who uploaded the file
     * @param mapGenericAttributes the values of the standard columns
     * @param mapFormFields        the values of the other columns
     * @param formEntries          the fields of the form
     * @param listSlots            the slots prepared by {@link #prepareSlots(int, LocalDateTime, LocalDateTime)}
     * @return created appointment identifier
     */
    public int importAppointment( int nFormId, String strAdminAccessCode, Map<String, String> mapGenericAttributes, Map<String, String> mapFormFields,
            AppointmentFormEntries formEntries, List<Slot> listSlots )
    {
        String strLastName = required( mapGenericAttributes, AppointmentImportRow.ATTRIBUTE_LAST_NAME );
        String strFirstName = required( mapGenericAttributes, AppointmentImportRow.ATTRIBUTE_FIRST_NAME );
        String strEmail = required( mapGenericAttributes, AppointmentImportRow.ATTRIBUTE_EMAIL );
        String strPhoneNumber = mapGenericAttributes.getOrDefault( AppointmentImportRow.ATTRIBUTE_PHONE_NUMBER, "" );
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
            appointment.setAdminUserCreate( strAdminAccessCode );
            appointment.setListResponse( formEntries.buildResponses( mapGenericAttributes, mapFormFields ) );
            return AppointmentService.saveAppointment( appointment );
        }
        catch( RuntimeException e )
        {
            throw new AppointmentImportException( AppointmentImportException.SAVE_FAILED, "module.appointment.importer.error.import.saveFailed", e,
                    e.getMessage( ) );
        }
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
     * Finds the slots covering the given interval, persisted or not, and checks them.
     *
     * @param nFormId    the form identifier
     * @param dtStarting start of the interval
     * @param dtEnding   end of the interval
     * @return the slots, sorted by starting datetime
     */
    private List<Slot> findSlots( int nFormId, LocalDateTime dtStarting, LocalDateTime dtEnding )
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
        return listSlots;
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
