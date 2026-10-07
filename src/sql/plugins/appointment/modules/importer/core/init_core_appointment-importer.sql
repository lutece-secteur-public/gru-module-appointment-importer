-- liquibase formatted sql
-- changeset appointment-importer:init_core_appointment-importer.sql
-- preconditions onFail:MARK_RAN onError:WARN

DELETE FROM core_admin_right WHERE id_right = 'APPOINTMENT_IMPORT';
INSERT INTO core_admin_right (id_right,name,level_right,admin_url,description,is_updatable,plugin_name,id_feature_group,icon_url,documentation_url,id_order) VALUES
('APPOINTMENT_IMPORT','module.appointment.importer.adminFeature.name',0,'jsp/admin/plugins/appointment/modules/importer/ManageAppointmentImport.jsp','module.appointment.importer.adminFeature.description',0,'appointment-importer','APPLICATIONS',NULL,NULL,10);

DELETE FROM core_user_right WHERE id_right = 'APPOINTMENT_IMPORT';
INSERT INTO core_user_right (id_right,id_user) VALUES ('APPOINTMENT_IMPORT',1);
