# gru-module-appointment-importer

Lutece module that imports appointments from an Excel file.

## How it works

1. A .xlsx file can be uploaded to import appointments into an appointment form.
2. The file is validated (duplicate rows, missing values, formats, form fields, etc.). If any error is found, a report is created and nothing is imported.
3. If the file is valid, its rows are saved in database, grouped by slot.
4. The daemon `AppointmentImportDaemon` creates the appointments using the services of the appointment plugin.
5. Results are available in the administration interface: the latest imports are listed with their number of rows created and in error, and
   the final report gives, for each row, its outcome, the person and the reference of the created appointment.
6. The rows in error can be retried once the cause is fixed (a slot opened, the form reactivated), for a whole file, a batch or a single row,
   and the values of a row can be corrected before it is retried.
7. The daemon `AppointmentImportPurgeDaemon` removes the personal data of the imports older than the retention period and keeps them archived.

## Rights

The feature requires the right `APPOINTMENT_IMPORT`. On each form, the RBAC permission `CREATE_APPOINTMENT` is required to import a file,
retry and correct rows, and `VIEW_APPOINTMENT` to see the results and download the reports.

A file can only be imported into an active form. The imports of a form stay visible once it is deactivated: results, reports, retries and
corrections remain available (a retry fails with `FORM_INACTIVE` until the form is reactivated).

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
  (check boxes accept several, separated by `;`); a text field whose title or code contains `email`, `mail` or `courriel` only accepts
  a valid email (`appointment-importer.emailFieldPattern`).
- Email is checked as in the back office (`AdminUserService.checkEmail`): the pattern set by hand or the selected regular expressions,
  and the banned domains.
- Phone number must have 10 digits and start with 0; the leading zero lost by a numeric Excel cell is restored.
- Last and first names must not exceed 100 characters.
- Dates are native Excel dates or `dd/mm/yyyy`, times native Excel times or `HH:mm`.
- The appointment must start in the future, the end time must be after the start time, the birth date must not be in the future.
- Duplicate rows (identical on all standard columns) are rejected.
- Duplicate files (same SHA-256 hash on the same form) are rejected.
- A file has at most 20 000 rows (configurable).

Import does not start if any validation error is detected. All errors are reported at once, with one exception: the values of the form
fields are only checked once the columns of the file match the fields of the form, since a missing column would put every row in error.
An unreadable workbook, or one without a header row, is only reported as such.

The slots are checked when the appointments are created. A row in error says why: no slot on that day (`SLOT_NOT_FOUND`), outside
the opening hours (`SLOT_NOT_FOUND`), times not on the limits of the slots (`SLOT_NOT_ALIGNED`, with the duration of the slots),
slot closed (`SLOT_CLOSED`) or full (`SLOT_FULL`).

## Processing

A batch (the rows of one slot) is taken atomically, so that several instances of the webapp never process the same batch.
A batch left in `PROCESSING` for more than `appointment-importer.processing.timeoutMinutes` is taken over; the row that was being saved
at that moment is put in error (code `INTERRUPTED`) and must be checked by hand, since its appointment may have been created.
A batch left with pending rows (a row retried while the daemon was processing it) is put back in the queue. A file still pending whose batches
are all done (two instances closed its last batches at the same time) is closed by the next run of the daemon.

A row put in error as `INTERRUPTED` is never retried with the others: it can only be retried on its own, once checked by hand.

The appointment plugin notifies its listeners (indexing, notifications...) after it has committed the appointment. If a listener fails, the
appointment exists: the row is counted as created, with its reference, and the failure is logged, so that a retry never creates it twice.

## Tests

The unit tests run with `mvn test`. The integration tests (DAO, daemon on a real appointment form) need Lutece started on a database:

```bash
mvn clean lutece:exploded antrun:run -Dlutece-test-hsql test
```

They are JUnit 4 tests extending `AbstractLuteceIntegrationTest`, which starts Lutece through `LuteceTestCase` of
`library-lutece-unit-testing`.
