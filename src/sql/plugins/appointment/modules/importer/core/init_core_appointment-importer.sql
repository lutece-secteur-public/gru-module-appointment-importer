-- liquibase formatted sql
-- changeset appointment-importer:init_core_appointment-importer.sql
-- preconditions onFail:MARK_RAN onError:WARN

DELETE FROM core_admin_right WHERE id_right = 'la ';
INSERT INTO core_admin_right (id_right,name,level_right,admin_url,description,is_updatable,plugin_name,id_feature_group,icon_url,documentation_url,id_order) VALUES
('APPOINTMENT_IMPORT','module.appointment.importer.adminFeature.name',0,'jsp/admin/plugins/appointment/modules/importer/ManageAppointmentImport.jsp','module.appointment.importer.adminFeature.description',0,'appointment-importer',NULL,NULL,NULL,10);

DELETE FROM core_user_right WHERE id_right = 'APPOINTMENT_IMPORT';
INSERT INTO core_user_right (id_right,id_user) VALUES ('APPOINTMENT_IMPORT',1);

DROP TABLE IF EXISTS appointment_import_appointment;
DROP TABLE IF EXISTS appointment_import_batch;
DROP TABLE IF EXISTS appointment_import_file;

CREATE TABLE appointment_import_file (
    id_import_file INT NOT NULL AUTO_INCREMENT,
    import_file_name VARCHAR(255) NOT NULL,
    id_form INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_hash VARCHAR(64) NULL,
    validation_report LONGTEXT NULL,
    creation_date DATETIME NOT NULL,
    last_exec_date DATETIME NULL,
    PRIMARY KEY (id_import_file),
    KEY idx_appointment_import_file_status (status),
    KEY idx_appointment_import_file_hash (file_hash, id_form)
);

CREATE TABLE appointment_import_batch (
    id_import_batch INT NOT NULL AUTO_INCREMENT,
    id_import_file INT NOT NULL,
    import_file_name VARCHAR(255) NOT NULL,
    id_form INT NOT NULL,
    starting_datetime DATETIME NOT NULL,
    ending_datetime DATETIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    creation_date DATETIME NOT NULL,
    last_exec_date DATETIME NULL,
    PRIMARY KEY (id_import_batch),
    KEY idx_appointment_import_batch_file (id_import_file),
    KEY idx_appointment_import_batch_status (status),
    CONSTRAINT fk_appointment_import_batch_file FOREIGN KEY (id_import_file)
        REFERENCES appointment_import_file (id_import_file)
);

CREATE TABLE appointment_import_appointment (
    id_import_appointment INT NOT NULL AUTO_INCREMENT,
    id_import_batch INT NOT NULL,
    source_line_number INT NOT NULL,
    generic_attributes_json LONGTEXT NOT NULL,
    form_fields_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(100) NULL,
    error_message MEDIUMTEXT NULL,
    id_appointment INT NULL,
    creation_date DATETIME NOT NULL,
    last_exec_date DATETIME NULL,
    PRIMARY KEY (id_import_appointment),
    KEY idx_appointment_import_appointment_batch (id_import_batch),
    KEY idx_appointment_import_appointment_status (status),
    CONSTRAINT fk_appointment_import_appointment_batch FOREIGN KEY (id_import_batch)
        REFERENCES appointment_import_batch (id_import_batch)
);
