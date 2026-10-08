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
package fr.paris.lutece.plugins.appointment.modules.importer.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportAppointment;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportBatch;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportHome;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportStatus;
import fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentValidationError;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumn;
import fr.paris.lutece.plugins.appointment.modules.importer.business.ImportColumns;
import fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportJsonService;
import fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportReportService;
import fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportRetryService;
import fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportService;
import fr.paris.lutece.plugins.appointment.service.AppointmentResourceIdService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.rbac.RBACService;
import fr.paris.lutece.portal.service.security.SecurityTokenService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.service.workgroup.AdminWorkgroupService;
import fr.paris.lutece.portal.util.mvc.admin.MVCAdminJspBean;
import fr.paris.lutece.portal.util.mvc.admin.annotations.Controller;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.portal.web.upload.MultipartHttpServletRequest;
import fr.paris.lutece.portal.web.util.LocalizedPaginator;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.html.AbstractPaginator;
import fr.paris.lutece.util.url.UrlItem;

/** Administration views for persistent appointment imports. */
@Controller( controllerJsp = "ManageAppointmentImport.jsp", controllerPath = "jsp/admin/plugins/appointment/modules/importer", right = "APPOINTMENT_IMPORT" )
public class AppointmentImportJspBean extends MVCAdminJspBean
{
    private static final long serialVersionUID = 1L;

    // Views
    private static final String VIEW_MANAGE_IMPORT = "manageAppointmentImport";
    private static final String VIEW_IMPORT_BATCH = "viewImportBatch";
    private static final String VIEW_MODIFY_ROW = "modifyImportRow";

    // Actions
    private static final String ACTION_IMPORT_WORKBOOK = "doImportWorkbook";
    private static final String ACTION_DOWNLOAD_VALIDATION_REPORT = "downloadValidationReport";
    private static final String ACTION_DOWNLOAD_REPORT = "downloadReport";
    private static final String ACTION_DOWNLOAD_FAILED_ROWS = "downloadFailedRows";
    private static final String ACTION_RETRY_BATCH = "doRetryImportBatch";
    private static final String ACTION_RETRY_FILE = "doRetryImportFile";
    private static final String ACTION_RETRY_ROW = "doRetryImportRow";
    private static final String ACTION_MODIFY_ROW = "doModifyImportRow";

    // Templates
    private static final String TEMPLATE_MANAGE_IMPORT = "admin/plugins/appointment/modules/importer/manage_appointment_import.html";
    private static final String TEMPLATE_IMPORT_BATCH = "admin/plugins/appointment/modules/importer/manage_appointment_import_batch.html";
    private static final String TEMPLATE_MODIFY_ROW = "admin/plugins/appointment/modules/importer/modify_appointment_import_row.html";

    // Properties
    private static final String PROPERTY_PAGE_TITLE = "module.appointment.importer.pageTitle";
    private static final String PROPERTY_ITEMS_PER_PAGE = "appointment-importer.itemsPerPage";

    // JSP
    private static final String JSP_MANAGE_IMPORT = "jsp/admin/plugins/appointment/modules/importer/ManageAppointmentImport.jsp";

    // Request parameters
    private static final String PARAMETER_FORM_ID = "id_form";
    private static final String PARAMETER_WORKBOOK = "import_file";
    private static final String PARAMETER_BATCH_ID = "id_import_batch";
    private static final String PARAMETER_FILE_ID = "id_import_file";
    private static final String PARAMETER_ROW_ID = "id_import_appointment";
    private static final String PARAMETER_PREFIX_GENERIC = "generic_";
    private static final String PARAMETER_PREFIX_FIELD = "field_";
    private static final String PARAMETER_STATUS = "status";
    private static final String PARAMETER_TAB = "tab";
    private static final String PARAMETER_FILTER_FILE = "filter_file";
    private static final String PARAMETER_FILTER_FORM = "filter_form";
    private static final String PARAMETER_FILTER_DATE = "filter_date";
    private static final String PARAMETER_FILE_PAGE_INDEX = "page_index_files";
    private static final String PARAMETER_RETURN_FILTER_FILE = "return_filter_file";
    private static final String PARAMETER_RETURN_FILTER_FORM = "return_filter_form";
    private static final String PARAMETER_RETURN_FILTER_STATUS = "return_filter_status";
    private static final String PARAMETER_RETURN_FILTER_DATE = "return_filter_date";
    private static final String PARAMETER_RETURN_PAGE_INDEX = "return_page_index";
    private static final String PARAMETER_RETURN_ITEMS_PER_PAGE = "return_items_per_page";
    private static final String PARAMETER_RESET_FILTERS = "reset";

    // Model marks
    private static final String MARK_FORMS = "forms";
    private static final String MARK_RESULT_FORMS = "result_forms";
    private static final String MARK_SELECTED_FORM_ID = "selected_form_id";
    private static final String MARK_VALIDATION_ERRORS = "validation_errors";
    private static final String MARK_VALIDATION_REPORT_AVAILABLE = "validation_report_available";
    private static final String MARK_VALIDATION_REPORT_FILE_ID = "validation_report_file_id";
    private static final String MARK_FILES = "import_files";
    private static final String MARK_FILTER_FILES = "filter_files";
    private static final String MARK_BATCHES = "import_batches";
    private static final String MARK_BATCH = "import_batch";
    private static final String MARK_ROWS = "import_rows";
    private static final String MARK_ROW = "import_row";
    private static final String MARK_GENERIC_VALUES = "generic_values";
    private static final String MARK_FIELD_VALUES = "field_values";
    private static final String MARK_ROW_ERRORS = "row_errors";
    private static final String MARK_CAN_RETRY = "can_retry";
    private static final String MARK_RETRY_FORM_IDS = "retry_form_ids";
    private static final String MARK_TOKEN_RETRY_BATCH = "token_retry_batch";
    private static final String MARK_TOKEN_RETRY_FILE = "token_retry_file";
    private static final String MARK_TOKEN_RETRY_ROW = "token_retry_row";
    private static final String MARK_INTERRUPTED_CODE = "interrupted_code";
    private static final String MARK_PROCESSED_ROWS_COUNT = "processed_rows_count";
    private static final String MARK_TOTAL_ROWS_COUNT = "total_rows_count";
    private static final String MARK_FILTER_STATUS = "filter_status";
    private static final String MARK_STATUS_OPTIONS = "status_options";
    private static final String MARK_ACTIVE_TAB = "active_tab";
    private static final String MARK_PAGINATOR = "paginator";
    private static final String MARK_FILE_PAGINATOR = "file_paginator";
    private static final String MARK_NB_ITEMS_PER_PAGE = "nb_items_per_page";
    private static final String MARK_RESULT_FILE = "result_file";
    private static final String MARK_RESULT_FORM_ID = "result_form_id";
    private static final String MARK_RESULT_DATE = "result_date";
    private static final String MARK_CURRENT_PAGE_INDEX = "current_page_index";
    private static final String MARK_BACK_TO_RESULTS_URL = "back_to_results_url";
    private static final String MARK_BATCH_URL = "batch_url";

    // i18n keys
    private static final String MESSAGE_INVALID_TOKEN = "portal.security.message.invalidToken";
    private static final String KEY_INFO_IMPORT_QUEUED = "module.appointment.importer.info.importQueued";
    private static final String KEY_INFO_RETRY_QUEUED = "module.appointment.importer.info.retryQueued";
    private static final String KEY_INFO_ROW_CORRECTED = "module.appointment.importer.info.rowCorrected";
    private static final String KEY_ERROR_RETRY_REFUSED = "module.appointment.importer.error.retryRefused";
    private static final String KEY_FORM = "module.appointment.importer.form";
    private static final String KEY_WORKBOOK = "module.appointment.importer.workbook";
    private static final String KEY_ERROR_FORM_UNAUTHORIZED = "module.appointment.importer.error.form.unauthorized";
    private static final String KEY_ERROR_FILE_MISSING = "module.appointment.importer.error.file.missing";
    private static final String KEY_ERROR_FILE_REQUIRED = "module.appointment.importer.error.file.required";
    private static final String KEY_ERROR_FILE_EXTENSION = "module.appointment.importer.error.file.extension";
    private static final String KEY_ERROR_FILE_UNEXPECTED = "module.appointment.importer.error.file.unexpected";
    private static final String KEY_ERROR_FILE_DUPLICATE = "module.appointment.importer.error.file.duplicate";
    private static final String KEY_STATUS_PREFIX = "module.appointment.importer.status.";
    private static final String KEY_STATUS_ALL = "module.appointment.importer.statusAll";

    // Downloads
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String FILE_NAME_VALIDATION_REPORT = "rapport-validation-";
    private static final String FILE_NAME_REPORT = "rapport-import-";
    private static final String FILE_NAME_FAILED_ROWS = "rendez-vous-non-importes-";
    private static final String EXTENSION_XLSX = ".xlsx";

    private final AppointmentImportService _importService = new AppointmentImportService( );
    private List<AppointmentValidationError> _validationErrors = new ArrayList<>( );
    private String _selectedFormId = StringUtils.EMPTY;
    private Integer _validationReportFileId;

    // Persisted filter state (survives tab switches within the same session)
    private String _strSavedFilterFile = StringUtils.EMPTY;
    private String _strSavedFilterFormId = StringUtils.EMPTY;
    private String _strSavedFilterStatus = StringUtils.EMPTY;
    private String _strSavedFilterDate = StringUtils.EMPTY;
    private int _nItemsPerPage = 0; // 0 = not yet set; resolved to property default on first use

    /**
     * Default view: renders the import form (tab "import") and the results table (tab "results").
     * Filters applied on the results tab are persisted in the session so they survive tab switches and page navigation.
     * On a plain GET the transient validation error state is cleared; on a POST (called by an action) it is kept.
     */
    @View( value = VIEW_MANAGE_IMPORT, defaultView = true )
    public String getManageAppointmentImport( HttpServletRequest request )
    {
        // Clear transient error state on plain GET navigation (POST = called directly from the action, errors are fresh)
        if ( "GET".equalsIgnoreCase( request.getMethod( ) ) )
        {
            _validationErrors = new ArrayList<>( );
            _validationReportFileId = null;
        }
        // Importing creates appointments, only in an active form; the results show the people imported, even once the form is deactivated
        ReferenceList forms = getAuthorizedForms( AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT, true );
        ReferenceList resultForms = getAuthorizedForms( AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT, false );
        String strTab = "results".equals( request.getParameter( PARAMETER_TAB ) ) ? "results" : "import";
        Map<Integer, String> mapFormTitles = new HashMap<>( );
        List<Integer> listAuthorizedFormIds = resultForms.stream( )
                .filter( item -> StringUtils.isNotBlank( item.getCode( ) ) )
                .map( item -> {
                    int nId = Integer.parseInt( item.getCode( ) );
                    mapFormTitles.put( nId, item.getName( ) );
                    return nId;
                } )
                .collect( Collectors.toList( ) );
        // Determine filter values: form submission > reset > tab navigation (restore session state)
        boolean bFilterSubmitted = request.getParameter( PARAMETER_FILTER_FILE ) != null;
        boolean bReset = "1".equals( request.getParameter( PARAMETER_RESET_FILTERS ) );
        String strFilterFile;
        String strFilterFormId;
        String strFilterStatus;
        String strFilterDate;
        if ( bReset )
        {
            strFilterFile = strFilterFormId = strFilterStatus = strFilterDate = StringUtils.EMPTY;
            _strSavedFilterFile = _strSavedFilterFormId = _strSavedFilterStatus = _strSavedFilterDate = StringUtils.EMPTY;
        }
        else if ( bFilterSubmitted )
        {
            strFilterFile   = StringUtils.defaultString( request.getParameter( PARAMETER_FILTER_FILE ) );
            strFilterFormId = StringUtils.defaultString( request.getParameter( PARAMETER_FILTER_FORM ) );
            strFilterStatus = StringUtils.defaultString( request.getParameter( PARAMETER_STATUS ) );
            strFilterDate   = StringUtils.defaultString( request.getParameter( PARAMETER_FILTER_DATE ) );
            _strSavedFilterFile   = strFilterFile;
            _strSavedFilterFormId = strFilterFormId;
            _strSavedFilterStatus = strFilterStatus;
            _strSavedFilterDate   = strFilterDate;
        }
        else
        {
            // Tab navigation: restore previously applied filters
            strFilterFile   = _strSavedFilterFile;
            strFilterFormId = _strSavedFilterFormId;
            strFilterStatus = _strSavedFilterStatus;
            strFilterDate   = _strSavedFilterDate;
        }
        strFilterDate = validDate( strFilterDate );
        // A form id the user may not see, or not a number, is dropped instead of failing every page of the session
        if ( !listAuthorizedFormIds.stream( ).map( String::valueOf ).collect( Collectors.toList( ) ).contains( strFilterFormId ) )
        {
            strFilterFormId = StringUtils.EMPTY;
            _strSavedFilterFormId = StringUtils.EMPTY;
        }
        List<String> listFilterFiles = AppointmentImportHome.findFileNames( listAuthorizedFormIds );
        List<AppointmentImportBatch> listBatches;
        List<AppointmentImportFile> listFiles;
        LocalizedPaginator<Integer> paginator;
        LocalizedPaginator<Integer> filePaginator;
        int nItemsPerPage;
        String strPageIndex;
        String strFilePageIndex;
        // Without filter, the latest imports are listed
        {
            // Only the ids are paginated: the batches and files of the current page alone are loaded
            List<Integer> listBatchIds = AppointmentImportHome.findBatchIds( listAuthorizedFormIds, strFilterFormId, strFilterStatus, strFilterFile,
                    strFilterDate );
            strPageIndex = AbstractPaginator.getPageIndex( request, AbstractPaginator.PARAMETER_PAGE_INDEX, "1" );
            strFilePageIndex = AbstractPaginator.getPageIndex( request, PARAMETER_FILE_PAGE_INDEX, "1" );
            // items_per_page is persisted in session (_nItemsPerPage) so it survives page navigation
            // without being duplicated in the paginator URL (which would cause the first value to win).
            // Argument order: getItemsPerPage(request, param, nCurrent, nDefault)
            //   nCurrent = _nItemsPerPage (session value, used when param absent from request)
            //   nDefault = property default (used only on the very first request when _nItemsPerPage == 0)
            _nItemsPerPage = AbstractPaginator.getItemsPerPage( request, AbstractPaginator.PARAMETER_ITEMS_PER_PAGE, _nItemsPerPage,
                    AppPropertiesService.getPropertyInt( PROPERTY_ITEMS_PER_PAGE, 10 ) );
            nItemsPerPage = _nItemsPerPage;
            UrlItem url = new UrlItem( JSP_MANAGE_IMPORT );
            url.addParameter( PARAMETER_TAB, "results" );
            url.addParameter( PARAMETER_FILTER_FILE, strFilterFile );
            url.addParameter( PARAMETER_FILTER_FORM, strFilterFormId );
            url.addParameter( PARAMETER_STATUS, strFilterStatus );
            url.addParameter( PARAMETER_FILTER_DATE, strFilterDate );
            url.addParameter( PARAMETER_FILE_PAGE_INDEX, strFilePageIndex );
            paginator = new LocalizedPaginator<>( listBatchIds, nItemsPerPage, url.getUrl( ), AbstractPaginator.PARAMETER_PAGE_INDEX, strPageIndex, getLocale( ) );
            listBatches = AppointmentImportHome.findBatchesByIds( paginator.getPageItems( ) );
            listBatches.forEach( batch -> batch.setFormTitle( mapFormTitles.getOrDefault( batch.getIdForm( ), Integer.toString( batch.getIdForm( ) ) ) ) );
            List<Integer> listFileIds = AppointmentImportHome.findFileIds( listAuthorizedFormIds, strFilterFormId, strFilterFile, strFilterStatus,
                    strFilterDate );
            UrlItem fileUrl = new UrlItem( JSP_MANAGE_IMPORT );
            fileUrl.addParameter( PARAMETER_TAB, "results" );
            fileUrl.addParameter( PARAMETER_FILTER_FILE, strFilterFile );
            fileUrl.addParameter( PARAMETER_FILTER_FORM, strFilterFormId );
            fileUrl.addParameter( PARAMETER_STATUS, strFilterStatus );
            fileUrl.addParameter( PARAMETER_FILTER_DATE, strFilterDate );
            fileUrl.addParameter( AbstractPaginator.PARAMETER_PAGE_INDEX, strPageIndex );
            filePaginator = new LocalizedPaginator<>( listFileIds, nItemsPerPage, fileUrl.getUrl( ), PARAMETER_FILE_PAGE_INDEX, strFilePageIndex, getLocale( ) );
            listFiles = AppointmentImportHome.findFilesByIds( filePaginator.getPageItems( ) );
            listFiles.forEach( file -> file.setFormTitle( mapFormTitles.getOrDefault( file.getIdForm( ), Integer.toString( file.getIdForm( ) ) ) ) );
            AppointmentImportHome.fillBatchCounts( listBatches );
            AppointmentImportHome.fillCounts( listFiles );
        }
        Map<String, Object> model = getModel( );
        model.put( MARK_FORMS, forms );
        model.put( MARK_RESULT_FORMS, resultForms );
        model.put( MARK_SELECTED_FORM_ID, _selectedFormId );
        model.put( MARK_VALIDATION_ERRORS, _validationErrors );
        model.put( MARK_VALIDATION_REPORT_AVAILABLE, _validationReportFileId != null );
        model.put( MARK_VALIDATION_REPORT_FILE_ID, _validationReportFileId );
        model.put( MARK_FILES, listFiles );
        model.put( MARK_FILTER_FILES, listFilterFiles );
        model.put( MARK_BATCHES, listBatches );
        model.put( MARK_ACTIVE_TAB, strTab );
        model.put( MARK_PAGINATOR, paginator );
        model.put( MARK_FILE_PAGINATOR, filePaginator );
        model.put( MARK_NB_ITEMS_PER_PAGE, Integer.toString( nItemsPerPage ) );
        model.put( MARK_FILTER_STATUS, strFilterStatus );
        model.put( MARK_RESULT_FILE, strFilterFile );
        model.put( MARK_RESULT_FORM_ID, strFilterFormId );
        model.put( MARK_RESULT_DATE, strFilterDate );
        model.put( MARK_CURRENT_PAGE_INDEX, strPageIndex );
        model.put( MARK_STATUS_OPTIONS, getStatusOptions( ) );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_IMPORT_WORKBOOK ) );
        model.put( MARK_TOKEN_RETRY_FILE, SecurityTokenService.getInstance( ).getToken( request, ACTION_RETRY_FILE ) );
        model.put( MARK_RETRY_FORM_IDS, formIds( forms ) );
        return getPage( PROPERTY_PAGE_TITLE, TEMPLATE_MANAGE_IMPORT, model );
    }

    /**
     * Handles the workbook upload form submission.
     * Validates the CSRF token, the selected form, the uploaded file (presence, extension, duplicate hash),
     * then delegates to {@link AppointmentImportService#register} which parses, validates and persists the rows.
     * On success the user is redirected to the main view with a success info message.
     * On validation failure the view is re-rendered with {@code _validationErrors} populated.
     */
    @Action( ACTION_IMPORT_WORKBOOK )
    public String doImportWorkbook( HttpServletRequest request )
    {
        _validationErrors = new ArrayList<>( );
        _validationReportFileId = null;
        _selectedFormId = request.getParameter( PARAMETER_FORM_ID );
        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_IMPORT_WORKBOOK ) )
        {
            addError( MESSAGE_INVALID_TOKEN, getLocale( ) );
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        if ( !isAuthorizedForm( _selectedFormId, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT, true ) )
        {
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_FORM ), message( KEY_ERROR_FORM_UNAUTHORIZED ) ) );
            return getManageAppointmentImport( request );
        }
        if ( !( request instanceof MultipartHttpServletRequest ) )
        {
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_WORKBOOK ), message( KEY_ERROR_FILE_MISSING ) ) );
            return getManageAppointmentImport( request );
        }
        FileItem file = ( (MultipartHttpServletRequest) request ).getFile( PARAMETER_WORKBOOK );
        if ( file == null || StringUtils.isBlank( file.getName( ) ) || file.getSize( ) == 0 )
        {
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_WORKBOOK ), message( KEY_ERROR_FILE_REQUIRED ) ) );
            return getManageAppointmentImport( request );
        }
        if ( !file.getName( ).toLowerCase( ).endsWith( ".xlsx" ) )
        {
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_WORKBOOK ), message( KEY_ERROR_FILE_EXTENSION ) ) );
            return getManageAppointmentImport( request );
        }
        byte [ ] fileBytes = file.get( );
        int nFormId = Integer.parseInt( _selectedFormId );
        String strFileHash = AppointmentImportService.computeFileHash( fileBytes );
        if ( AppointmentImportHome.existsDuplicateFile( strFileHash, nFormId ) )
        {
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_WORKBOOK ), message( KEY_ERROR_FILE_DUPLICATE ) ) );
            return getManageAppointmentImport( request );
        }
        try
        {
            AppointmentImportFile importFile = _importService.register( nFormId, file.getName( ), fileBytes, strFileHash, getUser( ).getAccessCode( ),
                    getLocale( ) );
            if ( AppointmentImportStatus.VALIDATION_FAILED.equals( importFile.getStatus( ) ) )
            {
                _validationReportFileId = importFile.getIdImportFile( );
                return getManageAppointmentImport( request );
            }
        }
        catch( RuntimeException e )
        {
            AppLogService.error( "Appointment import: unable to register workbook", e );
            String strDetail = StringUtils.defaultIfBlank( e.getMessage( ), e.getClass( ).getSimpleName( ) );
            _validationErrors.add( AppointmentValidationError.workbook( message( KEY_WORKBOOK ),
                    I18nService.getLocalizedString( KEY_ERROR_FILE_UNEXPECTED, new Object [ ] { strDetail }, getLocale( ) ) ) );
            return getManageAppointmentImport( request );
        }
        addInfo( KEY_INFO_IMPORT_QUEUED, getLocale( ) );
        return redirectView( request, VIEW_MANAGE_IMPORT );
    }

    /**
     * Renders the detail view of a single import batch, showing each appointment row and its status.
     * An optional {@code status} request parameter filters the rows displayed (e.g. only errors).
     * Return-navigation parameters are threaded through so the back button restores the previous page and filters.
     * Redirects to the main view if the batch id is invalid or the user is not authorized for its form.
     */
    @View( VIEW_IMPORT_BATCH )
    public String getImportBatch( HttpServletRequest request )
    {
        int nBatchId;
        try
        {
            nBatchId = Integer.parseInt( request.getParameter( PARAMETER_BATCH_ID ) );
        }
        catch( RuntimeException e )
        {
            return getManageAppointmentImport( request );
        }
        AppointmentImportBatch batch = AppointmentImportHome.findBatch( nBatchId );
        if ( batch == null || !isAuthorizedForm( Integer.toString( batch.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT ) )
        {
            return getManageAppointmentImport( request );
        }
        getAuthorizedForms( AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT, false ).stream( )
                .filter( item -> Integer.toString( batch.getIdForm( ) ).equals( item.getCode( ) ) )
                .findFirst( )
                .ifPresent( item -> batch.setFormTitle( item.getName( ) ) );
        String strStatus = StringUtils.defaultIfBlank( request.getParameter( PARAMETER_STATUS ), null );
        UrlItem backToResultsUrl = new UrlItem( JSP_MANAGE_IMPORT );
        backToResultsUrl.addParameter( PARAMETER_TAB, "results" );
        addBackToResultsParameters( backToResultsUrl, request );
        UrlItem batchUrl = new UrlItem( JSP_MANAGE_IMPORT );
        batchUrl.addParameter( "view", VIEW_IMPORT_BATCH );
        batchUrl.addParameter( PARAMETER_BATCH_ID, nBatchId );
        addReturnParameters( batchUrl, request );
        AppointmentImportHome.fillBatchCounts( java.util.Collections.singletonList( batch ) );
        Map<String, Object> model = getModel( );
        model.put( MARK_BATCH, batch );
        model.put( MARK_CAN_RETRY, isAuthorizedForm( Integer.toString( batch.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT ) );
        model.put( MARK_TOKEN_RETRY_BATCH, SecurityTokenService.getInstance( ).getToken( request, ACTION_RETRY_BATCH ) );
        model.put( MARK_TOKEN_RETRY_ROW, SecurityTokenService.getInstance( ).getToken( request, ACTION_RETRY_ROW ) );
        model.put( MARK_INTERRUPTED_CODE, AppointmentImportRetryService.INTERRUPTED );
        model.put( MARK_ROWS, AppointmentImportHome.findAppointmentsByBatch( nBatchId, strStatus ) );
        model.put( MARK_PROCESSED_ROWS_COUNT, AppointmentImportHome.countProcessedAppointmentsByBatch( nBatchId ) );
        model.put( MARK_TOTAL_ROWS_COUNT, AppointmentImportHome.countAppointmentsByBatch( nBatchId ) );
        model.put( MARK_FILTER_STATUS, strStatus );
        model.put( MARK_BACK_TO_RESULTS_URL, backToResultsUrl.getUrl( ) );
        model.put( MARK_BATCH_URL, batchUrl.getUrl( ) );
        return getPage( PROPERTY_PAGE_TITLE, TEMPLATE_IMPORT_BATCH, model );
    }

    /**
     * Puts the rows in error of a completed batch back in the queue of the daemon, except the interrupted ones.
     *
     * @param request the request
     * @return the redirection to the batch
     */
    @Action( ACTION_RETRY_BATCH )
    public String doRetryImportBatch( HttpServletRequest request )
    {
        AppointmentImportBatch batch = findBatch( request );
        if ( batch == null || !SecurityTokenService.getInstance( ).validate( request, ACTION_RETRY_BATCH )
                || !isAuthorizedForm( Integer.toString( batch.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT ) )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        addRetryMessage( AppointmentImportRetryService.retryBatch( batch.getIdImportBatch( ) ) );
        return redirect( request, VIEW_IMPORT_BATCH, PARAMETER_BATCH_ID, batch.getIdImportBatch( ) );
    }

    /**
     * Puts the rows in error of every completed batch of a file back in the queue of the daemon.
     *
     * @param request the request
     * @return the redirection to the results
     */
    @Action( ACTION_RETRY_FILE )
    public String doRetryImportFile( HttpServletRequest request )
    {
        AppointmentImportFile importFile = getAuthorizedFile( request, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT );
        if ( importFile != null && SecurityTokenService.getInstance( ).validate( request, ACTION_RETRY_FILE ) )
        {
            addRetryMessage( AppointmentImportRetryService.retryFile( importFile.getIdImportFile( ) ) > 0 );
        }
        Map<String, String> mapParameters = new LinkedHashMap<>( );
        mapParameters.put( PARAMETER_TAB, "results" );
        return redirect( request, VIEW_MANAGE_IMPORT, mapParameters );
    }

    /**
     * Puts one row in error back in the queue of the daemon, interrupted rows included once checked by hand.
     *
     * @param request the request
     * @return the redirection to its batch
     */
    @Action( ACTION_RETRY_ROW )
    public String doRetryImportRow( HttpServletRequest request )
    {
        AppointmentImportAppointment row = getAuthorizedRow( request );
        if ( row == null || !SecurityTokenService.getInstance( ).validate( request, ACTION_RETRY_ROW ) )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        addRetryMessage( AppointmentImportRetryService.retryRow( row.getIdImportAppointment( ) ) );
        return redirect( request, VIEW_IMPORT_BATCH, PARAMETER_BATCH_ID, row.getIdImportBatch( ) );
    }

    /**
     * Shows the values of a row in error, to correct them before it is retried. The slot cannot be changed: it is the one of the batch.
     *
     * @param request the request
     * @return the page
     */
    @View( VIEW_MODIFY_ROW )
    public String getModifyImportRow( HttpServletRequest request )
    {
        AppointmentImportAppointment row = getAuthorizedRow( request );
        if ( row == null )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        return modifyRowPage( request, row, AppointmentImportJsonService.readMap( row.getGenericAttributesJson( ) ),
                AppointmentImportJsonService.readMap( row.getFormFieldsJson( ) ), new ArrayList<>( ) );
    }

    /**
     * Saves the corrected values of a row in error and retries it, or shows the errors of the new values.
     *
     * @param request the request
     * @return the redirection to the batch, or the page with the errors
     */
    @Action( ACTION_MODIFY_ROW )
    public String doModifyImportRow( HttpServletRequest request )
    {
        AppointmentImportAppointment row = getAuthorizedRow( request );
        if ( row == null || !SecurityTokenService.getInstance( ).validate( request, ACTION_MODIFY_ROW ) )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        Map<String, String> mapGeneric = new LinkedHashMap<>( );
        for ( String strKey : AppointmentImportJsonService.readMap( row.getGenericAttributesJson( ) ).keySet( ) )
        {
            mapGeneric.put( strKey, StringUtils.defaultString( request.getParameter( PARAMETER_PREFIX_GENERIC + strKey ) ) );
        }
        // The form fields are posted by position: their names are headers of the workbook, not parameter names
        Map<String, String> mapFields = new LinkedHashMap<>( );
        int nIndex = 0;
        for ( String strName : AppointmentImportJsonService.readMap( row.getFormFieldsJson( ) ).keySet( ) )
        {
            mapFields.put( strName, StringUtils.defaultString( request.getParameter( PARAMETER_PREFIX_FIELD + nIndex++ ) ) );
        }
        List<AppointmentValidationError> listErrors = AppointmentImportRetryService.correctRow( row.getIdImportAppointment( ), mapGeneric, mapFields,
                getLocale( ) );
        if ( !listErrors.isEmpty( ) )
        {
            return modifyRowPage( request, row, mapGeneric, mapFields, listErrors );
        }
        addInfo( KEY_INFO_ROW_CORRECTED, getLocale( ) );
        return redirect( request, VIEW_IMPORT_BATCH, PARAMETER_BATCH_ID, row.getIdImportBatch( ) );
    }

    private String modifyRowPage( HttpServletRequest request, AppointmentImportAppointment row, Map<String, String> mapGeneric,
            Map<String, String> mapFields, List<AppointmentValidationError> listErrors )
    {
        ImportColumns columns = ImportColumns.fromProperties( );
        List<Map<String, String>> listGeneric = new ArrayList<>( );
        for ( ImportColumn column : ImportColumn.values( ) )
        {
            if ( column.getAttributeKey( ) != null && mapGeneric.containsKey( column.getAttributeKey( ) ) )
            {
                Map<String, String> mapValue = new HashMap<>( );
                mapValue.put( "name", PARAMETER_PREFIX_GENERIC + column.getAttributeKey( ) );
                mapValue.put( "label", columns.getHeader( column ) );
                mapValue.put( "value", mapGeneric.get( column.getAttributeKey( ) ) );
                mapValue.put( "mandatory", Boolean.toString( columns.isMandatory( column ) ) );
                listGeneric.add( mapValue );
            }
        }
        List<Map<String, String>> listFields = new ArrayList<>( );
        int nIndex = 0;
        for ( Map.Entry<String, String> field : mapFields.entrySet( ) )
        {
            Map<String, String> mapValue = new HashMap<>( );
            mapValue.put( "name", PARAMETER_PREFIX_FIELD + nIndex++ );
            mapValue.put( "label", field.getKey( ) );
            mapValue.put( "value", field.getValue( ) );
            listFields.add( mapValue );
        }
        AppointmentImportBatch batch = AppointmentImportHome.findBatch( row.getIdImportBatch( ) );
        Map<String, Object> model = getModel( );
        model.put( MARK_ROW, row );
        model.put( MARK_BATCH, batch );
        model.put( MARK_GENERIC_VALUES, listGeneric );
        model.put( MARK_FIELD_VALUES, listFields );
        model.put( MARK_ROW_ERRORS, listErrors );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_MODIFY_ROW ) );
        return getPage( PROPERTY_PAGE_TITLE, TEMPLATE_MODIFY_ROW, model );
    }

    /**
     * Returns the row in error named by the request, if the user may create appointments on its form.
     *
     * @param request the request
     * @return the row, or null
     */
    private AppointmentImportAppointment getAuthorizedRow( HttpServletRequest request )
    {
        try
        {
            AppointmentImportAppointment row = AppointmentImportHome.findAppointment( Integer.parseInt( request.getParameter( PARAMETER_ROW_ID ) ) );
            if ( row == null || !AppointmentImportStatus.ERROR.equals( row.getStatus( ) ) )
            {
                return null;
            }
            AppointmentImportBatch batch = AppointmentImportHome.findBatch( row.getIdImportBatch( ) );
            return batch != null && isAuthorizedForm( Integer.toString( batch.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT )
                    ? row
                    : null;
        }
        catch( NumberFormatException e )
        {
            return null;
        }
    }

    private AppointmentImportBatch findBatch( HttpServletRequest request )
    {
        try
        {
            return AppointmentImportHome.findBatch( Integer.parseInt( request.getParameter( PARAMETER_BATCH_ID ) ) );
        }
        catch( NumberFormatException e )
        {
            return null;
        }
    }

    private void addRetryMessage( boolean bRequeued )
    {
        if ( bRequeued )
        {
            addInfo( KEY_INFO_RETRY_QUEUED, getLocale( ) );
        }
        else
        {
            addError( KEY_ERROR_RETRY_REFUSED, getLocale( ) );
        }
    }

    /**
     * @param strDate a date filter, as sent by the date input (yyyy-MM-dd)
     * @return the date, or an empty string if it is not a valid date
     */
    private static String validDate( String strDate )
    {
        try
        {
            return StringUtils.isBlank( strDate ) ? StringUtils.EMPTY : java.time.LocalDate.parse( strDate ).toString( );
        }
        catch( java.time.format.DateTimeParseException e )
        {
            return StringUtils.EMPTY;
        }
    }

    /**
     * @param forms the forms
     * @return their ids, as in the reference list
     */
    private static List<String> formIds( ReferenceList forms )
    {
        return forms.stream( ).map( item -> item.getCode( ) ).filter( StringUtils::isNotBlank ).collect( Collectors.toList( ) );
    }

    /**
     * Downloads the validation report of a file rejected at validation.
     *
     * @param request the request
     * @return null: the report is written to the response
     * @throws IOException if the report cannot be built
     */
    @Action( ACTION_DOWNLOAD_VALIDATION_REPORT )
    public String doDownloadValidationReport( HttpServletRequest request ) throws IOException
    {
        // The validation report answers the upload: whoever may import on the form may read it
        AppointmentImportFile importFile = getAuthorizedFile( request, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT,
                AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT );
        if ( importFile == null || importFile.getValidationReport( ) == null )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        download( AppointmentImportReportService.validationReport( importFile.getValidationReport( ), getLocale( ) ),
                FILE_NAME_VALIDATION_REPORT + importFile.getIdImportFile( ) + EXTENSION_XLSX, CONTENT_TYPE_XLSX );
        return null;
    }

    /**
     * Downloads the report of an import: one row per appointment, with its outcome.
     *
     * @param request the request
     * @return null: the report is written to the response
     * @throws IOException if the report cannot be built
     */
    @Action( ACTION_DOWNLOAD_REPORT )
    public String doDownloadReport( HttpServletRequest request ) throws IOException
    {
        AppointmentImportFile importFile = getAuthorizedFile( request, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT );
        if ( importFile == null )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        download( AppointmentImportReportService.finalReport( importFile.getIdImportFile( ), getLocale( ) ),
                FILE_NAME_REPORT + importFile.getIdImportFile( ) + EXTENSION_XLSX, CONTENT_TYPE_XLSX );
        return null;
    }

    /**
     * Downloads the rows of an import whose appointment could not be created, in the layout of the source workbook.
     *
     * @param request the request
     * @return null: the workbook is written to the response
     * @throws IOException if the workbook cannot be built
     */
    @Action( ACTION_DOWNLOAD_FAILED_ROWS )
    public String doDownloadFailedRows( HttpServletRequest request ) throws IOException
    {
        AppointmentImportFile importFile = getAuthorizedFile( request, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT );
        if ( importFile == null )
        {
            return redirectView( request, VIEW_MANAGE_IMPORT );
        }
        download( AppointmentImportReportService.failedRowsWorkbook( importFile.getIdImportFile( ), getLocale( ) ),
                FILE_NAME_FAILED_ROWS + importFile.getIdImportFile( ) + EXTENSION_XLSX, CONTENT_TYPE_XLSX );
        return null;
    }

    /**
     * Returns the file named by the request, if the user has one of the given permissions on its form.
     *
     * @param request        the request
     * @param strPermissions the RBAC permissions, any of which is enough
     * @return the file, or null if it does not exist or is not authorized
     */
    private AppointmentImportFile getAuthorizedFile( HttpServletRequest request, String... strPermissions )
    {
        try
        {
            AppointmentImportFile importFile = AppointmentImportHome.findFile( Integer.parseInt( request.getParameter( PARAMETER_FILE_ID ) ) );
            if ( importFile == null )
            {
                return null;
            }
            for ( String strPermission : strPermissions )
            {
                if ( isAuthorizedForm( Integer.toString( importFile.getIdForm( ) ), strPermission ) )
                {
                    return importFile;
                }
            }
        }
        catch( NumberFormatException e )
        {
            AppLogService.debug( "Appointment import: invalid file id " + request.getParameter( PARAMETER_FILE_ID ) );
        }
        return null;
    }

    /**
     * Builds the status filter drop-down options (all statuses + empty "all" entry).
     *
     * @return localized reference list
     */
    private ReferenceList getStatusOptions( )
    {
        ReferenceList options = new ReferenceList( );
        options.addItem( StringUtils.EMPTY, message( KEY_STATUS_ALL ) );
        for ( String strStatus : new String [ ] {
                AppointmentImportStatus.PENDING,
                AppointmentImportStatus.PROCESSING,
                AppointmentImportStatus.COMPLETED,
                AppointmentImportStatus.COMPLETED_WITH_ERRORS,
                AppointmentImportStatus.VALIDATION_FAILED
        } )
        {
            options.addItem( strStatus, message( KEY_STATUS_PREFIX + strStatus ) );
        }
        return options;
    }

    /**
     * Appends all return-navigation parameters from the request to {@code url} (same name → same name).
     *
     * @param url     the URL being built
     * @param request the current request
     */
    private void addReturnParameters( UrlItem url, HttpServletRequest request )
    {
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_FILE );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_FORM );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_STATUS );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_DATE );
        addRequestParameter( url, request, PARAMETER_RETURN_PAGE_INDEX );
        addRequestParameter( url, request, PARAMETER_RETURN_ITEMS_PER_PAGE );
    }

    /**
     * Appends filter and pagination parameters from the request to {@code url},
     * mapping return-prefixed parameter names to their original filter names.
     *
     * @param url     the URL being built
     * @param request the current request
     */
    private void addBackToResultsParameters( UrlItem url, HttpServletRequest request )
    {
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_FILE, PARAMETER_FILTER_FILE );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_FORM, PARAMETER_FILTER_FORM );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_STATUS, PARAMETER_STATUS );
        addRequestParameter( url, request, PARAMETER_RETURN_FILTER_DATE, PARAMETER_FILTER_DATE );
        addRequestParameter( url, request, PARAMETER_RETURN_PAGE_INDEX, AbstractPaginator.PARAMETER_PAGE_INDEX );
        addRequestParameter( url, request, PARAMETER_RETURN_ITEMS_PER_PAGE, AbstractPaginator.PARAMETER_ITEMS_PER_PAGE );
    }

    /**
     * Appends a request parameter to {@code url} using the same name for source and target.
     *
     * @param url              the URL being built
     * @param request          the current request
     * @param strParameterName the parameter name
     */
    private void addRequestParameter( UrlItem url, HttpServletRequest request, String strParameterName )
    {
        addRequestParameter( url, request, strParameterName, strParameterName );
    }

    /**
     * Appends a request parameter to {@code url}, renaming it if source and target names differ.
     * Does nothing if the parameter value is blank.
     *
     * @param url                    the URL being built
     * @param request                the current request
     * @param strSourceParameterName the name of the parameter in the request
     * @param strTargetParameterName the name to use in the output URL
     */
    private void addRequestParameter( UrlItem url, HttpServletRequest request, String strSourceParameterName, String strTargetParameterName )
    {
        String strParameterValue = request.getParameter( strSourceParameterName );
        if ( StringUtils.isNotBlank( strParameterValue ) )
        {
            url.addParameter( strTargetParameterName, strParameterValue );
        }
    }

    /**
     * Returns true if the given form id is a valid integer and belongs to the current user's authorized forms, active or not.
     *
     * @param strFormId     the form id as a string
     * @param strPermission the RBAC permission required on the form
     * @return true if authorized, false otherwise
     */
    private boolean isAuthorizedForm( String strFormId, String strPermission )
    {
        return isAuthorizedForm( strFormId, strPermission, false );
    }

    /**
     * Returns true if the given form id is a valid integer and belongs to the current user's authorized forms.
     *
     * @param strFormId     the form id as a string
     * @param strPermission the RBAC permission required on the form
     * @param bActiveOnly   true if the form must be active
     * @return true if authorized, false otherwise
     */
    private boolean isAuthorizedForm( String strFormId, String strPermission, boolean bActiveOnly )
    {
        try
        {
            int nId = Integer.parseInt( strFormId );
            return getAuthorizedForms( strPermission, bActiveOnly ).stream( ).anyMatch( item -> Integer.toString( nId ).equals( item.getCode( ) ) );
        }
        catch( NumberFormatException e )
        {
            return false;
        }
    }

    /**
     * Returns the appointment forms accessible to the current admin user,
     * filtered by workgroup and by the given RBAC permission.
     * The first item is always an empty placeholder used to display "no form selected".
     *
     * @param strPermission the RBAC permission required on the forms
     * @param bActiveOnly   true to keep only the active forms
     * @return the forms
     */
    private ReferenceList getAuthorizedForms( String strPermission, boolean bActiveOnly )
    {
        List<Form> listForms = new ArrayList<>( AdminWorkgroupService.getAuthorizedCollection( FormService.findAllForms( ), getUser( ) ) );
        listForms = new ArrayList<>( RBACService.getAuthorizedCollection( listForms, strPermission, getUser( ) ) );
        ReferenceList listResult = new ReferenceList( );
        listResult.addItem( StringUtils.EMPTY, StringUtils.EMPTY );
        for ( Form form : listForms )
        {
            if ( !bActiveOnly || form.getIsActive( ) )
            {
                listResult.addItem( form.getIdForm( ), form.getTitle( ) );
            }
        }
        return listResult;
    }

    /**
     * Returns the localized message for the given i18n key.
     *
     * @param strKey the i18n key
     * @return the localized string
     */
    private String message( String strKey )
    {
        return I18nService.getLocalizedString( strKey, getLocale( ) );
    }
}
