-- liquibase formatted sql
-- changeset appointment-importer:create_db_appointment-importer.sql
-- preconditions onFail:MARK_RAN onError:WARN

DROP TABLE IF EXISTS appointment_import_appointment;
DROP TABLE IF EXISTS appointment_import_batch;
DROP TABLE IF EXISTS appointment_import_file;

CREATE TABLE appointment_import_file (
    id_import_file INT NOT NULL AUTO_INCREMENT,
    import_file_name VARCHAR(255) NOT NULL,
    id_form INT NOT NULL,
    admin_access_code VARCHAR(255) NULL,
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
    processing_token VARCHAR(64) NULL,
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
