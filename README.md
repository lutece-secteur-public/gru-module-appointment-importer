# gru-module-appointment-importer

Lutece module that imports appointments from an Excel file.

## How it works

1. A .xlsx file can be uploaded to import appointments into an appointment form.
2. The file is validated (duplicate rows, missing values, formats, form fields, etc.). If any error is found, a report is created and nothing is imported.
3. If the file is valid, its rows are saved in database, grouped by slot.
4. The daemon `AppointmentImportDaemon` creates the appointments using the services of the appointment plugin.
5. Results (created appointments, errors) are available in the administration interface, with downloadable reports.
6. The daemon `AppointmentImportPurgeDaemon` removes the personal data of the imports older than the retention period and keeps them archived.

## Rights

The feature requires the right `APPOINTMENT_IMPORT`. On each form, the RBAC permission `CREATE_APPOINTMENT` is required to import a file,
and `VIEW_APPOINTMENT` to see the results and download the reports.

## Excel file format

The first sheet must contain the following columns (column order does not matter, case and accents are ignored):

| Column | Also accepted | Required |
|---|---|---|
| Nom | nom | yes |
| Prénom | prenom | yes |
| Email | email | yes |
| Date de naissance | date_naissance | yes (configurable) |
| Date | date_rdv | yes |
| Heure de début | heure_debut | yes |
| Heure de fin | heure_fin | yes |
| Téléphone | telephone | no (configurable) |

Headers, accepted headers and the mandatory flag of `Téléphone` and `Date de naissance` can be changed in `appointment-importer.properties`.

Any additional column must match a generic-attribute field of the selected form, by its title or its code.

## Validation rules

- All mandatory columns must be present and non-empty.
- Additional columns and the fields of the form must match exactly, both ways.
- Mandatory fields of the form must be filled; a field with choices only accepts the title or the value of one of its choices
  (check boxes accept several, separated by `;`).
- Email must match the pattern configured in Lutece.
- Phone number must have 10 digits and start with 0; the leading zero lost by a numeric Excel cell is restored.
- Last and first names must not exceed 100 characters.
- Dates are native Excel dates or `dd/mm/yyyy`, times native Excel times or `HH:mm`.
- The appointment must start in the future, the end time must be after the start time, the birth date must not be in the future.
- Duplicate rows (identical on all standard columns) are rejected.
- Duplicate files (same SHA-256 hash on the same form) are rejected.
- A file has at most 20 000 rows (configurable).

Import does not start if any validation error is detected. All errors are reported at once.

The slots are checked when the appointments are created: a slot that does not exist, is closed or is full puts its rows in error.

## Processing

A batch (the rows of one slot) is taken atomically, so that several instances of the webapp never process the same batch.
A batch left in `PROCESSING` for more than `appointment-importer.processing.timeoutMinutes` is taken over; the row that was being saved
at that moment is put in error (code `INTERRUPTED`) and must be checked by hand, since its appointment may have been created.
