package ems.service;

import ems.exception.DuplicateEmailException;
import ems.exception.EmployeeNotFoundException;
import ems.exception.ValidationException;
import ems.model.Department;
import ems.model.Employee;
import ems.model.EmployeeStatus;
import ems.repository.EmployeeRepository;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * All business rules live here. The repository only stores and indexes.
 */
public class EmployeeService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EmployeeRepository repository;
    private final AtomicInteger idSequence = new AtomicInteger(100);

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    // ---------- create / read / update / delete ----------

    public Employee addEmployee(Employee employee) {
        validateForCreate(employee);
        repository.findByEmail(employee.getEmail()).ifPresent(existing -> {
            throw new DuplicateEmailException("email already used by " + existing.getId());
        });
        if (employee.getManagerId() != null) {
            requireExisting(employee.getManagerId());
        }
        employee.setId("E" + idSequence.incrementAndGet());
        return repository.save(employee);
    }

    public Employee getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("no employee with id " + id));
    }

    public Optional<Employee> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public Employee updateSalary(String id, double newSalary) {
        if (newSalary <= 0) {
            throw new ValidationException("salary must be positive");
        }
        Employee employee = getById(id);
        if (employee.getStatus() == EmployeeStatus.EXITED) {
            throw new ValidationException("cannot change salary of an exited employee " + id);
        }
        employee.setSalary(newSalary);
        return repository.save(employee);
    }

    public Employee promote(String id, String newDesignation, double newSalary) {
        if (newDesignation == null || newDesignation.isBlank()) {
            throw new ValidationException("designation is required");
        }
        Employee employee = getById(id);
        if (newSalary < employee.getSalary()) {
            throw new ValidationException("a promotion cannot reduce the salary");
        }
        employee.setDesignation(newDesignation);
        employee.setSalary(newSalary);
        return repository.save(employee);
    }

    public Employee transferDepartment(String id, Department newDepartment, String newManagerId) {
        Employee employee = getById(id);
        if (newDepartment == null) {
            throw new ValidationException("department is required");
        }
        employee.setDepartment(newDepartment);
        employee.setManagerId(resolveManager(id, newManagerId));
        return repository.save(employee);
    }

    public Employee assignManager(String employeeId, String managerId) {
        Employee employee = getById(employeeId);
        employee.setManagerId(resolveManager(employeeId, managerId));
        return repository.save(employee);
    }

    public Employee changeStatus(String id, EmployeeStatus status) {
        Employee employee = getById(id);
        employee.setStatus(status);
        return repository.save(employee);
    }

    /**
     * Soft delete. If the employee has direct reports, a replacement manager is
     * mandatory - otherwise we would orphan a whole subtree.
     */
    public Employee exitEmployee(String id, String replacementManagerId) {
        Employee employee = getById(id);
        List<Employee> reports = repository.findByManagerId(id);

        if (!reports.isEmpty()) {
            if (replacementManagerId == null) {
                throw new ValidationException("employee " + id + " has " + reports.size()
                        + " direct reports, a replacement manager is required");
            }
            if (replacementManagerId.equals(id)) {
                throw new ValidationException("replacement manager cannot be the exiting employee");
            }
            requireExisting(replacementManagerId);
            for (Employee report : reports) {
                report.setManagerId(replacementManagerId);
                repository.save(report);
            }
        }
        employee.setStatus(EmployeeStatus.EXITED);
        employee.setManagerId(null);
        return repository.save(employee);
    }

    /** Real delete, only for data cleanup. Same reportee rule applies. */
    public boolean hardDelete(String id) {
        getById(id);
        if (!repository.findByManagerId(id).isEmpty()) {
            throw new ValidationException("cannot delete " + id + ", reassign the reportees first");
        }
        return repository.deleteById(id);
    }

    // ---------- search ----------

    /** Filter + sort + paginate in one place. */
    public List<Employee> search(EmployeeQuery query) {
        return repository.findAll().stream()
                .filter(query.toPredicate())
                .sorted(query.toComparator())
                .skip((long) query.getPage() * query.getPageSize())
                .limit(query.getPageSize())
                .collect(Collectors.toList());
    }

    public long countMatching(EmployeeQuery query) {
        return repository.findAll().stream().filter(query.toPredicate()).count();
    }

    // ---------- org hierarchy ----------

    public List<Employee> getDirectReports(String managerId) {
        requireExisting(managerId);
        return repository.findByManagerId(managerId);
    }

    /** Employee -> manager -> ... -> top of the tree. */
    public List<Employee> getReportingChain(String employeeId) {
        List<Employee> chain = new ArrayList<>();
        Employee current = getById(employeeId);
        Set<String> seen = new HashSet<>();
        while (current.getManagerId() != null && seen.add(current.getId())) {
            current = getById(current.getManagerId());
            chain.add(current);
        }
        return chain;
    }

    /** Everyone below this employee, BFS over the tree. */
    public List<Employee> getAllReportsRecursive(String managerId) {
        requireExisting(managerId);
        List<Employee> all = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(managerId);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            for (Employee report : repository.findByManagerId(currentId)) {
                if (visited.add(report.getId())) {
                    all.add(report);
                    queue.add(report.getId());
                }
            }
        }
        return all;
    }

    // ---------- reports / aggregations ----------

    public Map<Department, Long> headcountByDepartment() {
        return activeEmployees().stream()
                .collect(Collectors.groupingBy(Employee::getDepartment,
                        () -> new LinkedHashMap<>(), Collectors.counting()));
    }

    public Map<Department, Double> averageSalaryByDepartment() {
        return activeEmployees().stream()
                .collect(Collectors.groupingBy(Employee::getDepartment,
                        () -> new LinkedHashMap<>(), Collectors.averagingDouble(Employee::getSalary)));
    }

    public Map<Department, Employee> highestPaidByDepartment() {
        Map<Department, Employee> result = new LinkedHashMap<>();
        activeEmployees().stream()
                .collect(Collectors.groupingBy(Employee::getDepartment,
                        () -> new LinkedHashMap<>(),
                        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))))
                .forEach((dept, employee) -> employee.ifPresent(e -> result.put(dept, e)));
        return result;
    }

    public double totalMonthlyPayroll() {
        return activeEmployees().stream().mapToDouble(Employee::getSalary).sum();
    }

    public List<Employee> topPaid(int n) {
        return activeEmployees().stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /** Percent hike for a whole department. Returns how many rows changed. */
    public int giveRaiseToDepartment(Department department, double percent) {
        if (percent <= 0) {
            throw new ValidationException("raise percent must be positive");
        }
        List<Employee> target = search(EmployeeQuery.create()
                .department(department)
                .status(EmployeeStatus.ACTIVE));
        for (Employee employee : target) {
            employee.setSalary(round2(employee.getSalary() * (1 + percent / 100)));
            repository.save(employee);
        }
        return target.size();
    }

    public int totalEmployees() {
        return repository.count();
    }

    // ---------- helpers ----------

    private List<Employee> activeEmployees() {
        return repository.findAll().stream()
                .filter(e -> e.getStatus() != EmployeeStatus.EXITED)
                .collect(Collectors.toList());
    }

    private String resolveManager(String employeeId, String managerId) {
        if (managerId == null) {
            return null;
        }
        if (managerId.equals(employeeId)) {
            throw new ValidationException("an employee cannot be their own manager");
        }
        requireExisting(managerId);
        if (createsCycle(employeeId, managerId)) {
            throw new ValidationException(
                    "assigning " + managerId + " to " + employeeId + " would create a cycle");
        }
        return managerId;
    }

    /** Walk up from the proposed manager - if we reach the employee, it is a loop. */
    private boolean createsCycle(String employeeId, String managerId) {
        String current = managerId;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current.equals(employeeId)) {
                return true;
            }
            current = repository.findById(current).map(Employee::getManagerId).orElse(null);
        }
        return false;
    }

    private void requireExisting(String id) {
        if (repository.findById(id).isEmpty()) {
            throw new EmployeeNotFoundException("no employee with id " + id);
        }
    }

    private void validateForCreate(Employee employee) {
        if (employee == null) {
            throw new ValidationException("employee is required");
        }
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new ValidationException("name is required");
        }
        if (employee.getEmail() == null || !EMAIL_PATTERN.matcher(employee.getEmail()).matches()) {
            throw new ValidationException("invalid email: " + employee.getEmail());
        }
        if (employee.getDepartment() == null) {
            throw new ValidationException("department is required");
        }
        if (employee.getDesignation() == null || employee.getDesignation().isBlank()) {
            throw new ValidationException("designation is required");
        }
        if (employee.getSalary() <= 0) {
            throw new ValidationException("salary must be positive");
        }
        if (employee.getJoiningDate() == null) {
            throw new ValidationException("joining date is required");
        }
        if (employee.getJoiningDate().isAfter(LocalDate.now().plusMonths(3))) {
            throw new ValidationException("joining date is too far in the future");
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
