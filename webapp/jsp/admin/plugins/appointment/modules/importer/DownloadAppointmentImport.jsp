<jsp:useBean id="appointmentImport" scope="session" class="fr.paris.lutece.plugins.appointment.modules.importer.web.AppointmentImportJspBean" />
<%
    // The download actions write the workbook to the response and return null; any other result is a redirection
    String strContent = appointmentImport.processController( request, response );
    if ( strContent != null )
    {
        out.print( strContent );
    }
%>
