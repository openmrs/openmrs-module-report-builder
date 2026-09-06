# OpenMRS Report Builder Module

Backend module for the  Report Builder project.  
This module provides the server-side APIs, domain models, persistence, preview execution, and report-building services used to define, manage, and run reporting components in Report builder app.

## Overview

The reporting backend is responsible for:

- managing reporting metadata and configuration
- exposing REST resources for reporting entities
- storing and retrieving report definitions
- previewing indicator and section SQL
- executing parameterized SQL safely through service-layer methods
- supporting frontend consumers with structured JSON responses

This module is designed to work within the OpenMRS module ecosystem and follows common OpenMRS backend patterns such as:

- service-layer business logic
- Hibernate-backed domain models
- REST resources based on `DelegatingCrudResource`
- action-oriented endpoints for non-CRUD operations like preview

## Main Responsibilities

The backend module currently supports work around:

- **Report Libraries**
- **Report Builder Sections**
- **Indicators**
- **ETL Sources**
- **SQL Preview**
- **Section Preview**
- metadata persistence and retrieval
- backend validation and resource lifecycle operations

## Module Structure

A typical package layout looks like this:

```text
org.openmrs.module.reportbuilder
├── api
│   ├── ReportBuilderService.java
│   └── impl
├── dao
├── dto
├── model
├── web
│   ├── controller
│   ├── resource
│   └── ...
```

### Key package roles

#### `api`
Contains service interfaces and implementations for business logic.

Examples:
- saving and retrieving reporting entities
- retiring and purging resources
- previewing SQL
- searching and paging reporting data

#### `dao`
Contains persistence logic for fetching and storing reporting entities.

#### `model`
Contains domain entities such as:

- `ReportBuilderSection`
- `ETLSource`
- report library entities
- indicator-related entities

#### `dto`
Contains transport and helper objects used for request/response operations.

Examples:
- `SqlPreviewResult`
- request payload DTOs for preview endpoints

#### `web.resource`
Contains REST resources for CRUD-style endpoints.

Examples:
- `ReportBuilderSectionResource`
- `ETLSourceResource`
- `ReportLibraryResource`

#### `web.controller`
Contains action-based endpoints that do not fit normal CRUD semantics.

Examples:
- preview endpoints
- compile/validate endpoints
- execution actions

## Backend Design Principles

### 1. CRUD resources for persisted entities
Resources extending `DelegatingCrudResource<T>` should be used for entities that are actually stored and managed.

Examples:
- sections
- ETL sources
- report libraries

### 2. Controllers for actions
Endpoints such as preview, validate, execute, or compile are better modeled as standalone action endpoints instead of fake CRUD subresources.

Examples:
- previewing SQL for an indicator
- previewing all indicators in a section

### 3. Service-layer ownership
Business logic should remain in the service layer as much as possible.

Resources and controllers should mainly:
- validate request input
- load domain objects
- call the service
- shape the response

### 4. Consistent lifecycle handling
Entities should consistently use either:
- `retired` / `retireReason` for `Retireable`
- `voided` for `Voidable`

Avoid mixing both semantics in the same resource unless the model truly supports that.

## Key REST Endpoints

### Report Builder Section CRUD

Base path:

```text
/ws/rest/v1/reportbuilder/section
```

Typical operations:
- create section
- get section by UUID
- list sections
- search sections
- update section
- retire section
- purge section

### Section Preview

Standalone preview endpoint:

```text
POST /ws/rest/v1/reportbuilder/sectionpreview
```

This endpoint previews SQL for:
- all indicators inside a section, or
- one indicator inside a section if `indicatorUuid` is provided

#### Sample payload

```json
{
  "sectionUuid": "4278ebf9-47ab-40b8-aeca-df3e4a050c79",
  "startDate": "2026-01-01",
  "endDate": "2026-01-31",
  "maxRows": 100,
  "params": {}
}
```

#### Preview a single indicator in a section

```json
{
  "sectionUuid": "4278ebf9-47ab-40b8-aeca-df3e4a050c79",
  "indicatorUuid": "2b9c6c8e-1234-4d9f-8abc-1234567890ab",
  "startDate": "2026-01-01",
  "endDate": "2026-01-31",
  "maxRows": 100,
  "params": {}
}
```

### ETL Source CRUD

Base path:

```text
/ws/rest/v1/reportbuilder/etlsource
```

Typical operations:
- create ETL source
- get ETL source by UUID
- list ETL sources
- search ETL sources
- retire ETL source
- purge ETL source

## Request and Response Patterns

### Common request fields for preview endpoints

- `startDate` — required
- `endDate` — required
- `maxRows` — optional
- `params` — optional map of extra SQL parameters
- `indicatorUuid` — optional, when previewing one indicator in a section
- `sectionUuid` — required for standalone section preview resource

### Example response shape

```json
{
  "sectionUuid": "4278ebf9-47ab-40b8-aeca-df3e4a050c79",
  "results": [
    {
      "indicatorUuid": "2b9c6c8e-1234-4d9f-8abc-1234567890ab",
      "kind": "indicator",
      "name": "Total Clients",
      "code": "TC001",
      "columns": ["total"],
      "rows": [
        [245]
      ],
      "rowCount": 1,
      "truncated": false,
      "error": null
    }
  ]
}
```

## Development Notes

### Standalone preview resource vs subresource
For preview operations, prefer a standalone resource or controller endpoint instead of using `DelegatingSubResource` when there is no persisted child object.

Why:
- preview is an action, not a child entity
- it avoids routing ambiguity
- it keeps endpoint intent clearer

### Paging
Where listing is supported, resources should implement:

- `doGetAll(RequestContext context)`
- `doSearch(RequestContext context)`

and return:
- `NeedsPaging<T>` for paged service responses
- `AlreadyPaged<T>` where appropriate

### Resource representations
Resources should define:
- `DefaultRepresentation`
- `FullRepresentation`
- `getCreatableProperties()`
- `getUpdatableProperties()` where needed

### Error handling
Use:
- `ObjectNotFoundException` when a referenced entity does not exist
- `ResourceDoesNotSupportOperationException` for unsupported CRUD operations
- validation errors for missing required request fields

## Example Backend Workflow

### Previewing a section
1. client sends POST request to `/ws/rest/v1/reportbuilder/sectionpreview`
2. backend validates `sectionUuid`, `startDate`, and `endDate`
3. backend loads `ReportBuilderSection`
4. backend reads `configJson`
5. backend extracts compiled SQL for one or more indicators
6. backend executes preview through `ReportBuilderService`
7. backend returns preview result as JSON

## Build and Run

This module is intended to run inside an OpenMRS-based environment.

Typical development flow:

```bash
mvn clean install
```

Then deploy the built module artifact into your OpenMRS modules directory or run it through your standard local development setup.

for module integration tests in OpenMRS.

## Security: Privileges & Roles

This module ships its own security model: **46 privileges** in the OpenMRS
`Task: reportbuilder.<domain>.<action>` convention, bundled into **5 ready-made roles**,
enforced on every API path (REST resources, controllers and scheduled tasks).

This page tells implementers and site administrators everything needed to consume them
in their own content packs.

### How it works

1. **Definition** — privileges and roles are declared in the module's initializer
   configuration: `omod/src/main/resources/configuration/privileges/reportbuilder-privileges.csv`
   and `configuration/roles/reportbuilder-roles.csv` (shipped inside the OMOD).
   The Java counterparts live in
   `api/src/main/java/org/openmrs/module/reportbuilder/security/ReportBuilderPrivileges.java`.
2. **Provisioning** — at startup the [initializer](https://github.com/openmrs/openmrs-module-initializer)
   module (2.9.0+) loads both CSVs and creates the privileges and roles.
3. **Enforcement** — every `ReportBuilderService` method is annotated `@Authorized(...)`;
   core's authorization interceptor rejects any caller whose roles don't carry the required
   privilege. Controllers that bypass the service (report download, data export, patient
   search, package validation) call `Context.requirePrivilege(...)` directly.

### Privilege catalog

| Privilege | Grants |
|---|---|
| `Task: reportbuilder.report.view` | View report definitions (Report Builder) |
| `Task: reportbuilder.report.add` | Create report definitions (Report Builder) |
| `Task: reportbuilder.report.edit` | Edit report definitions (Report Builder) |
| `Task: reportbuilder.report.purge` | Delete report definitions (Report Builder) |
| `Task: reportbuilder.indicator.view` | View SQL indicators (Report Builder) |
| `Task: reportbuilder.indicator.add` | Create SQL indicators (Report Builder) |
| `Task: reportbuilder.indicator.edit` | Edit SQL indicators (Report Builder) |
| `Task: reportbuilder.indicator.purge` | Delete SQL indicators (Report Builder) |
| `Task: reportbuilder.section.view` | View report sections (Report Builder) |
| `Task: reportbuilder.section.add` | Create report sections (Report Builder) |
| `Task: reportbuilder.section.edit` | Edit report sections (Report Builder) |
| `Task: reportbuilder.section.purge` | Delete report sections (Report Builder) |
| `Task: reportbuilder.theme.view` | View data themes (Report Builder) |
| `Task: reportbuilder.theme.add` | Create data themes (Report Builder) |
| `Task: reportbuilder.theme.edit` | Edit data themes (Report Builder) |
| `Task: reportbuilder.theme.purge` | Delete data themes (Report Builder) |
| `Task: reportbuilder.dashboard.view` | View dashboards (Report Builder) |
| `Task: reportbuilder.dashboard.add` | Create dashboards (Report Builder) |
| `Task: reportbuilder.dashboard.edit` | Edit dashboards (Report Builder) |
| `Task: reportbuilder.dashboard.purge` | Delete dashboards (Report Builder) |
| `Task: reportbuilder.category.view` | View report categories (Report Builder) |
| `Task: reportbuilder.category.add` | Create report categories (Report Builder) |
| `Task: reportbuilder.category.edit` | Edit report categories (Report Builder) |
| `Task: reportbuilder.category.purge` | Delete report categories (Report Builder) |
| `Task: reportbuilder.agegroup.view` | View age categories and age groups (Report Builder) |
| `Task: reportbuilder.agegroup.add` | Create age categories and age groups (Report Builder) |
| `Task: reportbuilder.agegroup.edit` | Edit age categories and age groups (Report Builder) |
| `Task: reportbuilder.agegroup.purge` | Delete age categories and age groups (Report Builder) |
| `Task: reportbuilder.library.view` | View report library entries (Report Builder) |
| `Task: reportbuilder.library.add` | Create report library entries (Report Builder) |
| `Task: reportbuilder.library.edit` | Edit report library entries (Report Builder) |
| `Task: reportbuilder.library.purge` | Delete report library entries (Report Builder) |
| `Task: reportbuilder.etlsource.view` | View ETL source connections (Report Builder) |
| `Task: reportbuilder.etlsource.add` | Create ETL source connections (Report Builder) |
| `Task: reportbuilder.etlsource.edit` | Edit ETL source connections (Report Builder) |
| `Task: reportbuilder.etlsource.purge` | Delete ETL source connections (Report Builder) |
| `Task: reportbuilder.etlmonitor.view` | View ETL health monitors (Report Builder) |
| `Task: reportbuilder.etlmonitor.add` | Create ETL health monitors (Report Builder) |
| `Task: reportbuilder.etlmonitor.edit` | Edit ETL health monitors (Report Builder) |
| `Task: reportbuilder.etlmonitor.purge` | Delete ETL health monitors (Report Builder) |
| `Task: reportbuilder.report.compile` | Compile report definitions (Report Builder) |
| `Task: reportbuilder.report.run` | Run reports and download outputs (Report Builder) |
| `Task: reportbuilder.schema.view` | Browse the database schema (Report Builder) |
| `Task: reportbuilder.sql.execute` | Execute SQL previews (Report Builder) |
| `Task: reportbuilder.package.import` | Import distribution packages (Report Builder) |
| `Task: reportbuilder.package.export` | Export distribution packages (Report Builder) |

`*.purge` privileges are intentionally not part of any module role — they are held by
System Developer (core auto-grants new privileges to that role) and can be delegated ad hoc.

### Shipped roles

| Role | Inherits | Privileges |
|---|---|---|
| **Report Viewer** | — | 11 privileges (see `roles/reportbuilder-roles.csv`) |
| **Report Runner** | Report Viewer | 1 privileges (see `roles/reportbuilder-roles.csv`) |
| **Report Author** | Report Runner | 18 privileges (see `roles/reportbuilder-roles.csv`) |
| **Content Publisher** | Report Viewer | 2 privileges (see `roles/reportbuilder-roles.csv`) |
| **ETL Administrator** | Report Viewer | 4 privileges (see `roles/reportbuilder-roles.csv`) |

Role chain: `Report Viewer` → `Report Runner` → `Report Author`; `Report Viewer` →
`Content Publisher`; `Report Viewer` → `ETL Administrator`.

### Using these privileges in your content pack

Site content packs assign privileges through the same initializer mechanism. Create (or
extend) `configuration/roles/` in your pack and reference the privilege **names exactly as
listed above** — initializer matches roles and privileges by name.

#### Example — extend an existing site role

`configuration/roles/site-roles.csv`:
```csv
Role name,Description,Inherited roles,Privileges
Organizational: Clinician,District clinician,,Task: reportbuilder.report.view; Task: reportbuilder.dashboard.view; Task: reportbuilder.report.run
Organizational: District Biostatistician,Runs and schedules HMIS reports,Organizational: Clinician,Task: reportbuilder.indicator.view; Task: reportbuilder.section.view
```

#### Example — a custom role reusing the module's bundles

Because the shipped roles are plain OpenMRS roles, site roles can simply inherit them:

```csv
Role name,Description,Inherited roles,Privileges
METS Reporting Team,METS officers who build and publish reports,Report Author; Content Publisher,
```

That single inheritance grants everything an author and a publisher need — no privilege
has to be listed twice.

#### Where packs place the files

```
<your-content-pack>/
  configuration/
    roles/
      site-roles.csv          <- references Task: reportbuilder.* names
    privileges/
      (optional extra privileges of your own)
```

The module's own CSVs are loaded from inside the OMOD; your pack's `configuration/` folder
goes to the OpenMRS application data directory. Both are processed by the same initializer run —
module configuration first, so the privileges always exist before your roles reference them.

### Enforcement map

| API surface | Required privilege |
|---|---|
| Report definitions: list / get | `report.view` |
| Report definitions: save / retire | `report.add` / `report.edit` |
| Compile a report | `report.compile` |
| Run / download report output (incl. data export & evaluation endpoints) | `report.run` |
| Indicators / sections / themes / dashboards / categories / age groups: view vs save vs purge | matching `<domain>.view` / `.add`+`.edit` / `.purge` |
| SQL preview, section preview, allowed-table prefixes | `sql.execute` |
| Database schema browsing | `schema.view` |
| ETL sources + their table/column exploration | `etlsource.*` |
| ETL monitors | `etlmonitor.*` |
| Import distribution packages (directory import, single entity, legacy & generic imports) | `package.import` |
| Export / shipping packages, available packages | `package.export` |
| Dashboard patient search / cohort listings | `dashboard.view` |

### Rollout checklist

1. Install the initializer module (2.9.0+ verified) and deploy the report builder OMOD.
2. Log in as an administrator — System Developer holds all 46 privileges automatically.
3. Assign the module roles (or your own roles referencing the privileges) — **no other user
   can reach the module until this is done.**
4. To allow hard deletes, grant the specific `*.purge` privileges to a trusted role.

### Troubleshooting

- *"Privilege required: Task: reportbuilder.…"* — the user lacks a role carrying that
  privilege; check **Admin → Manage Roles** and the user's assigned roles.
- *Privilege missing from Admin → Roles entirely* — initializer did not run or the OMOD
  predates the security config; redeploy and check `openmrs.log` for initializer lines.
- *Renaming a privilege* is a breaking change: role assignments reference the name. Keep
  `ReportBuilderPrivileges.java` and the CSV in sync (they are verified to match).

## License

Mozilla Public License 2.0
