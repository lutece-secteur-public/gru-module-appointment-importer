<%@ page import="fr.paris.lutece.portal.service.admin.AdminAuthenticationService" %>
<%@ page import="fr.paris.lutece.portal.business.user.AdminUser" %>
<%@ page import="fr.paris.lutece.portal.service.rbac.RBACService" %>
<%@ page import="fr.paris.lutece.portal.service.workgroup.AdminWorkgroupService" %>
<%@ page import="fr.paris.lutece.plugins.appointment.business.form.Form" %>
<%@ page import="fr.paris.lutece.plugins.appointment.business.form.FormHome" %>
<%@ page import="fr.paris.lutece.plugins.appointment.service.AppointmentResourceIdService" %>
<%@ page import="fr.paris.lutece.plugins.appointment.modules.importer.business.AppointmentImportFile" %>
<%@ page import="fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportHome" %>
<%@ page import="fr.paris.lutece.plugins.appointment.modules.importer.service.AppointmentImportReportService" %>
<%
AdminUser user = AdminAuthenticationService.getInstance( ).getRegisteredUser( request );
if ( user == null || !user.checkRight( "APPOINTMENT_IMPORT" ) ) { response.sendError( 403 ); return; }
int nFileId;
try { nFileId = Integer.parseInt( request.getParameter( "id_import_file" ) ); }
catch ( NumberFormatException e ) { response.sendError( 400 ); return; }
AppointmentImportFile importFile = AppointmentImportHome.findFile( nFileId );
if ( importFile == null ) { response.sendError( 404 ); return; }
Form form = FormHome.findByPrimaryKey( importFile.getIdForm( ) );
if ( form == null || !form.getIsActive( )
        || !AdminWorkgroupService.isAuthorized( form, user )
        || !RBACService.isAuthorized( form, AppointmentResourceIdService.PERMISSION_VIEW_FORM, user ) ) {
    response.sendError( 403 ); return;
}
byte[] report;
try { report = AppointmentImportReportService.finalReport( nFileId, user.getLocale( ) ); }
catch ( java.io.IOException e ) { response.sendError( 500 ); return; }
response.setContentType( "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" );
response.setHeader( "Content-Disposition", "attachment; filename=\"rapport-import-" + nFileId + ".xlsx\"" );
response.getOutputStream( ).write( report );
%>
