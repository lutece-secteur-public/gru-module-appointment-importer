<jsp:useBean id="appointmentImport" scope="session" class="fr.paris.lutece.plugins.appointment.modules.importer.web.AppointmentImportJspBean" />
<% String strContent = appointmentImport.processController( request, response ); %>

<%@ page errorPage="../../../../ErrorPage.jsp" %>
<jsp:include page="../../../../AdminHeader.jsp" />

<%= strContent %>

<%@ include file="../../../../AdminFooter.jsp" %>
