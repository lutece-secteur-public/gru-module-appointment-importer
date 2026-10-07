# gru-module-appointment-importer

Lutece module that imports appointments from an Excel file.

## How it works

1. A .xlsx file can be uploaded to import appointment.
2. The file is validated (check duplicate row, value missing, etc..). If any error is found, a report is created.
3. If the file is valid, its rows are saved in database.
4. The daemon `AppointmentImportDaemon` create the appointment using services from the plugin appointment.
5. Results (created appointments, errors) are available in the administration interface.

## Excel file format

The first sheet must contain the following columns (column order does not matter):

| Column | Required |
|---|---|
| Nom | yes |
| Prénom | yes |
| Email | yes |
| Date de naissance | yes |
| Date | yes |
| Heure de début | yes |
| Heure de fin | yes |
| Téléphone | no |

Any additional column must match a generic-attribute field of the selected form.
## Validation rules

- All mandatory columns must be present and non-empty.
- Additional columns must correspond exactly to the form's generic-attribute fields.
- Email must match the pattern configured in Lutece.
- End time must be after start time.
- Duplicate rows (identical on all standard columns) are rejected.
- Duplicate files (same SHA-256 hash on the same form) are rejected.

Import does not start if any validation error is detected. All errors are reported at once.
