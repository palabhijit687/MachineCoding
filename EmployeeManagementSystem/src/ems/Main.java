package ems;

import ems.exception.DuplicateEmailException;
import ems.exception.EmployeeNotFoundException;
import ems.exception.ValidationException;
import ems.model.Department;
import ems.model.Employee;
import ems.model.EmployeeStatus;
import ems.repository.InMemoryEmployeeRepository;
import ems.service.EmployeeQuery;
import ems.service.EmployeeQuery.SortField;
import ems.service.EmployeeService;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static EmployeeService service;

    public static void main(String[] args) {
        service = new EmployeeService(new InMemoryEmployeeRepository());

        String ceo = seed();
        listAll();
        searchAndSort();
        pagination();
        hierarchy(ceo);
        reports();
        promotionsAndRaises();
        exitFlow();
        validationCases();
    }

    /** Builds a small org: CEO -> VPs -> managers -> ICs. */
    private static String seed() {
        System.out.println("=== 1. onboarding employees ===");

        String ceo = add("Ravi Menon", "ravi@corp.com", Department.ENGINEERING, "CEO",
                900000, null, LocalDate.of(2015, 4, 1));

        String vpEng = add("Sneha Rao", "sneha@corp.com", Department.ENGINEERING, "VP Engineering",
                600000, ceo, LocalDate.of(2017, 6, 15));
        String vpSales = add("Imran Shah", "imran@corp.com", Department.SALES, "VP Sales",
                550000, ceo, LocalDate.of(2018, 1, 10));

        String em1 = add("Priya Nair", "priya@corp.com", Department.ENGINEERING, "Engineering Manager",
                400000, vpEng, LocalDate.of(2019, 3, 20));

        add("Abhijeet Pal", "abhijeet@corp.com", Department.ENGINEERING, "SDE II",
                220000, em1, LocalDate.of(2022, 7, 11));
        add("Karan Gupta", "karan@corp.com", Department.ENGINEERING, "SDE I",
                150000, em1, LocalDate.of(2023, 9, 1));
        add("Neha Singh", "neha@corp.com", Department.ENGINEERING, "SDE III",
                310000, em1, LocalDate.of(2020, 2, 17));

        add("Rahul Verma", "rahul@corp.com", Department.SALES, "Account Executive",
                180000, vpSales, LocalDate.of(2021, 11, 5));
        add("Divya Iyer", "divya@corp.com", Department.PRODUCT, "Product Manager",
                350000, ceo, LocalDate.of(2021, 5, 3));
        add("Meera Joshi", "meera@corp.com", Department.HR, "HR Business Partner",
                200000, ceo, LocalDate.of(2020, 8, 24));

        System.out.println("  onboarded " + service.totalEmployees() + " employees\n");
        return ceo;
    }

    private static void listAll() {
        System.out.println("=== 2. all employees ===");
        service.search(EmployeeQuery.create()).forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    private static void searchAndSort() {
        System.out.println("=== 3. search: engineering, salary 150k-400k, highest paid first ===");
        List<Employee> result = service.search(EmployeeQuery.create()
                .department(Department.ENGINEERING)
                .salaryBetween(150000.0, 400000.0)
                .sortBy(SortField.SALARY, false));
        result.forEach(e -> System.out.println("  " + e));

        System.out.println("\n  name search 'a' + sort by joining date (oldest first):");
        service.search(EmployeeQuery.create()
                        .nameContains("a")
                        .sortBy(SortField.JOINING_DATE, true))
                .forEach(e -> System.out.println("    " + e.getName() + " joined " + e.getJoiningDate()));
        System.out.println();
    }

    private static void pagination() {
        System.out.println("=== 4. pagination, 4 per page sorted by name ===");
        long total = service.countMatching(EmployeeQuery.create());
        int pageSize = 4;
        int pages = (int) Math.ceil((double) total / pageSize);
        for (int page = 0; page < pages; page++) {
            List<Employee> chunk = service.search(EmployeeQuery.create()
                    .sortBy(SortField.NAME, true)
                    .page(page, pageSize));
            System.out.println("  page " + (page + 1) + "/" + pages + ": "
                    + chunk.stream().map(Employee::getName).collect(Collectors.joining(", ")));
        }
        // asking for a page past the end should be empty, not an error
        List<Employee> beyond = service.search(EmployeeQuery.create().page(50, pageSize));
        System.out.println("  page 51 (past the end) returned " + beyond.size() + " rows\n");
    }

    private static void hierarchy(String ceoId) {
        System.out.println("=== 5. org hierarchy ===");
        Employee abhijeet = service.findByEmail("abhijeet@corp.com").orElseThrow();

        System.out.println("  direct reports of CEO: "
                + service.getDirectReports(ceoId).stream()
                .map(Employee::getName).collect(Collectors.joining(", ")));

        System.out.println("  reporting chain of " + abhijeet.getName() + ": "
                + service.getReportingChain(abhijeet.getId()).stream()
                .map(e -> e.getName() + " (" + e.getDesignation() + ")")
                .collect(Collectors.joining(" -> ")));

        System.out.println("  everyone under CEO: " + service.getAllReportsRecursive(ceoId).size()
                + " people");

        Employee vpEng = service.findByEmail("sneha@corp.com").orElseThrow();
        System.out.println("  everyone under " + vpEng.getName() + ": "
                + service.getAllReportsRecursive(vpEng.getId()).stream()
                .map(Employee::getName).collect(Collectors.joining(", ")));
        System.out.println();
    }

    private static void reports() {
        System.out.println("=== 6. management reports ===");
        System.out.println("  headcount by department:");
        service.headcountByDepartment()
                .forEach((dept, count) -> System.out.printf("    %-12s %d%n", dept, count));

        System.out.println("  average salary by department:");
        service.averageSalaryByDepartment()
                .forEach((dept, avg) -> System.out.printf("    %-12s %.2f%n", dept, avg));

        System.out.println("  highest paid per department:");
        service.highestPaidByDepartment()
                .forEach((dept, e) -> System.out.printf("    %-12s %s (%.0f)%n",
                        dept, e.getName(), e.getSalary()));

        System.out.printf("  total monthly payroll: %.2f%n", service.totalMonthlyPayroll());
        System.out.println("  top 3 paid: " + service.topPaid(3).stream()
                .map(e -> e.getName() + "=" + (long) e.getSalary())
                .collect(Collectors.joining(", ")));
        System.out.println();
    }

    private static void promotionsAndRaises() {
        System.out.println("=== 7. promotion and department raise ===");
        Employee karan = service.findByEmail("karan@corp.com").orElseThrow();
        System.out.println("  before: " + karan.getDesignation() + " @ " + (long) karan.getSalary());
        Employee promoted = service.promote(karan.getId(), "SDE II", 210000);
        System.out.println("  after : " + promoted.getDesignation() + " @ " + (long) promoted.getSalary());

        try {
            service.promote(promoted.getId(), "SDE III", 150000);
        } catch (ValidationException e) {
            System.out.println("  pay cut promotion blocked: " + e.getMessage());
        }

        int updated = service.giveRaiseToDepartment(Department.SALES, 10);
        System.out.println("  10% raise applied to " + updated + " sales employees:");
        service.search(EmployeeQuery.create().department(Department.SALES))
                .forEach(e -> System.out.println("    " + e.getName() + " -> " + e.getSalary()));
        System.out.println();
    }

    private static void exitFlow() {
        System.out.println("=== 8. employee exit ===");
        Employee em = service.findByEmail("priya@corp.com").orElseThrow();
        System.out.println("  " + em.getName() + " has "
                + service.getDirectReports(em.getId()).size() + " direct reports");

        try {
            service.exitEmployee(em.getId(), null);
        } catch (ValidationException e) {
            System.out.println("  exit without replacement blocked: " + e.getMessage());
        }

        Employee vpEng = service.findByEmail("sneha@corp.com").orElseThrow();
        service.exitEmployee(em.getId(), vpEng.getId());
        System.out.println("  " + em.getName() + " exited, reports moved to " + vpEng.getName());
        System.out.println("  " + vpEng.getName() + "'s direct reports now: "
                + service.getDirectReports(vpEng.getId()).stream()
                .map(Employee::getName).collect(Collectors.joining(", ")));
        System.out.println("  status of exited employee: "
                + service.getById(em.getId()).getStatus() + " (row kept for audit)");
        System.out.println("  active employees counted in payroll: "
                + service.countMatching(EmployeeQuery.create().status(EmployeeStatus.ACTIVE)));

        try {
            service.updateSalary(em.getId(), 500000);
        } catch (ValidationException e) {
            System.out.println("  salary change on exited employee blocked: " + e.getMessage());
        }
        System.out.println();
    }

    private static void validationCases() {
        System.out.println("=== 9. validation and edge cases ===");

        try {
            add("Duplicate Guy", "abhijeet@corp.com", Department.ENGINEERING, "SDE I",
                    100000, null, LocalDate.of(2024, 1, 1));
        } catch (DuplicateEmailException e) {
            System.out.println("  duplicate email blocked: " + e.getMessage());
        }

        try {
            add("Bad Email", "not-an-email", Department.HR, "Recruiter",
                    100000, null, LocalDate.of(2024, 1, 1));
        } catch (ValidationException e) {
            System.out.println("  bad email blocked: " + e.getMessage());
        }

        try {
            add("No Salary", "nosalary@corp.com", Department.HR, "Recruiter",
                    -5000, null, LocalDate.of(2024, 1, 1));
        } catch (ValidationException e) {
            System.out.println("  negative salary blocked: " + e.getMessage());
        }

        try {
            add("Future Joiner", "future@corp.com", Department.HR, "Recruiter",
                    100000, null, LocalDate.now().plusYears(2));
        } catch (ValidationException e) {
            System.out.println("  far future joining date blocked: " + e.getMessage());
        }

        try {
            add("Ghost Manager", "ghost@corp.com", Department.HR, "Recruiter",
                    100000, "E999", LocalDate.of(2024, 1, 1));
        } catch (EmployeeNotFoundException e) {
            System.out.println("  unknown manager blocked: " + e.getMessage());
        }

        Employee neha = service.findByEmail("neha@corp.com").orElseThrow();
        try {
            service.assignManager(neha.getId(), neha.getId());
        } catch (ValidationException e) {
            System.out.println("  self manager blocked: " + e.getMessage());
        }

        // cycle: Sneha reports to Ravi; making Ravi report to Sneha's subtree is a loop
        Employee ravi = service.findByEmail("ravi@corp.com").orElseThrow();
        try {
            service.assignManager(ravi.getId(), neha.getId());
        } catch (ValidationException e) {
            System.out.println("  cycle blocked: " + e.getMessage());
        }

        try {
            service.getById("E999");
        } catch (EmployeeNotFoundException e) {
            System.out.println("  unknown id blocked: " + e.getMessage());
        }

        try {
            service.hardDelete(ravi.getId());
        } catch (ValidationException e) {
            System.out.println("  hard delete with reportees blocked: " + e.getMessage());
        }

        Employee meera = service.findByEmail("meera@corp.com").orElseThrow();
        System.out.println("  hard delete of a leaf employee (" + meera.getName() + "): "
                + service.hardDelete(meera.getId()));
        System.out.println("  employees remaining: " + service.totalEmployees());

        try {
            EmployeeQuery.create().page(-1, 10);
        } catch (IllegalArgumentException e) {
            System.out.println("  bad pagination blocked: " + e.getMessage());
        }
    }

    private static String add(String name, String email, Department dept, String designation,
                             double salary, String managerId, LocalDate joining) {
        Employee saved = service.addEmployee(Employee.builder()
                .name(name)
                .email(email)
                .department(dept)
                .designation(designation)
                .salary(salary)
                .managerId(managerId)
                .joiningDate(joining)
                .build());
        return saved.getId();
    }
}
