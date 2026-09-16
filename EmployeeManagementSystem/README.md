# Employee Management System

CRUD-heavy design question. The interesting parts are not the CRUD - they are the
search/filter/sort/paginate API, the reporting hierarchy, and what happens when a
manager leaves.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out ems.Main
```

## Clarifying questions I would ask first

1. **Which operations must be supported?** Just CRUD, or search and reports too? *(assumed: CRUD + search + org hierarchy + aggregation reports)*
2. **Is there a reporting hierarchy?** Manager per employee, or teams? *(assumed: one manager per employee, so the org is a tree)*
3. **Delete = remove the row, or mark inactive?** *(assumed: soft delete. Payroll and audit need the history. Kept a `hardDelete` for genuine cleanup)*
4. **What happens to an exiting manager's reports?** *(assumed: caller must supply a replacement manager, otherwise reject - never orphan a subtree)*
5. **Departments** - fixed list or user created? *(assumed: enum)*
6. **Search requirements** - which fields, sorting, pagination? *(assumed: department / status / salary range / name, sortable, paginated)*
7. **Salary visibility / roles** - does an IC see everyone's salary? *(assumed: authorization is out of scope, mentioned it)*
8. **Storage** - in-memory or DB? *(assumed: in-memory behind a repository interface)*
9. **Unique key** - email? *(assumed: yes, email is unique, id is system generated)*

## Approach

Three layers so the rules do not leak into storage:

```
model/       Employee (builder), Department, EmployeeStatus
repository/  EmployeeRepository (interface) -> InMemoryEmployeeRepository
service/     EmployeeService (all rules), EmployeeQuery (search criteria)
exception/   EmployeeNotFound, DuplicateEmail, Validation
```

Key decisions and why:

- **`EmployeeQuery` criteria object instead of many finder methods.** Otherwise the
  service grows `findByDeptAndStatusAndSalaryRangeSortedBy...`. The query builds a
  `Predicate<Employee>` and a `Comparator<Employee>`, and the service does
  `filter -> sorted -> skip -> limit`. Adding a new filter is one method, and it
  maps 1:1 onto a SQL `WHERE`/`ORDER BY` later.
- **Sort always tie-breaks on id.** Without that, two employees on the same salary
  can swap places between pages and a row gets shown twice or skipped.
- **Builder on `Employee`.** Nine fields; a positional constructor would be unreadable
  and easy to get wrong at the call site.
- **Two secondary indexes in the repository** - `email -> id` for the uniqueness check,
  and `managerId -> reportees` for the org tree. Both avoid a full scan. `save()` is
  an upsert that fixes up the old index entries, which is why it is synchronized.
- **Repository returns copies** (`Employee.copy()`). Otherwise a caller mutating a
  returned object silently edits the store and bypasses every validation.
- **Soft delete via `EmployeeStatus.EXITED`.** All aggregations filter out exited
  employees, so payroll and headcount stay correct while history survives.
- **Cycle detection on manager assignment.** Walk up from the proposed manager; if we
  come back to the employee, reject. Without it one bad update makes
  `getReportingChain` loop forever.
- **BFS for `getAllReportsRecursive`** with a visited set - iterative, so a deep org
  cannot blow the stack, and it is defensive about bad data.

## Edge cases handled (see the demo output)

- Duplicate email -> `DuplicateEmailException`
- Malformed email, non-positive salary, blank name/designation, missing joining date -> `ValidationException`
- Joining date far in the future -> rejected (3 month grace for real offer letters)
- Unknown manager id on create -> `EmployeeNotFoundException`
- Employee set as their own manager -> rejected
- Manager assignment that would create a cycle -> rejected
- Exiting a manager with direct reports and no replacement -> rejected; with a replacement, the whole subtree is re-parented
- Editing the salary of an exited employee -> rejected
- "Promotion" that lowers the salary -> rejected
- `hardDelete` on someone who still has reports -> rejected; on a leaf -> allowed
- Page past the end of the result set -> empty list, not an error
- Negative page / zero page size -> rejected up front
- Exited employees excluded from headcount, averages and payroll

## Extensions I would mention if time was left

- **Swap the repository for JPA/JDBC.** `EmployeeQuery` maps directly onto a Criteria
  API query or a parameterised `WHERE` clause, so the service does not change.
- **Salary history table** instead of overwriting the field, so raises and promotions
  are auditable, plus a generic audit log of who changed what.
- **Role based access:** an IC sees their team, an HRBP sees their org, only finance
  sees full payroll.
- **Attendance / leave balance** as separate aggregates, related to but not embedded in `Employee`.
- **Bulk import** (CSV) with per-row validation and a partial-failure report.
- **Optimistic locking** (a `version` column) so two concurrent edits to the same
  employee do not silently overwrite each other. Today `save()` is last-write-wins.
