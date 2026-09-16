package ems.service;

import ems.model.Department;
import ems.model.Employee;
import ems.model.EmployeeStatus;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Search criteria object. Without this the service ends up with
 * findByDeptAndStatusAndSalaryRange(...) style method explosion.
 *
 * Every filter is optional - a null field means "do not filter on this".
 */
public class EmployeeQuery {

    public enum SortField {
        ID, NAME, SALARY, JOINING_DATE
    }

    private Department department;
    private EmployeeStatus status;
    private Double minSalary;
    private Double maxSalary;
    private String nameContains;
    private SortField sortField = SortField.ID;
    private boolean ascending = true;
    private int page = 0;
    private int pageSize = Integer.MAX_VALUE;

    public static EmployeeQuery create() {
        return new EmployeeQuery();
    }

    public EmployeeQuery department(Department department) {
        this.department = department;
        return this;
    }

    public EmployeeQuery status(EmployeeStatus status) {
        this.status = status;
        return this;
    }

    public EmployeeQuery salaryBetween(Double min, Double max) {
        this.minSalary = min;
        this.maxSalary = max;
        return this;
    }

    public EmployeeQuery nameContains(String text) {
        this.nameContains = text;
        return this;
    }

    public EmployeeQuery sortBy(SortField field, boolean ascending) {
        this.sortField = field;
        this.ascending = ascending;
        return this;
    }

    public EmployeeQuery page(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("page must be >= 0 and pageSize > 0");
        }
        this.page = page;
        this.pageSize = pageSize;
        return this;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** All filters combined with AND. */
    public Predicate<Employee> toPredicate() {
        Predicate<Employee> predicate = e -> true;
        if (department != null) {
            predicate = predicate.and(e -> e.getDepartment() == department);
        }
        if (status != null) {
            predicate = predicate.and(e -> e.getStatus() == status);
        }
        if (minSalary != null) {
            predicate = predicate.and(e -> e.getSalary() >= minSalary);
        }
        if (maxSalary != null) {
            predicate = predicate.and(e -> e.getSalary() <= maxSalary);
        }
        if (nameContains != null && !nameContains.isBlank()) {
            String needle = nameContains.toLowerCase();
            predicate = predicate.and(e -> e.getName().toLowerCase().contains(needle));
        }
        return predicate;
    }

    public Comparator<Employee> toComparator() {
        Comparator<Employee> comparator = switch (sortField) {
            case ID -> Comparator.comparing(Employee::getId);
            case NAME -> Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER);
            case SALARY -> Comparator.comparingDouble(Employee::getSalary);
            case JOINING_DATE -> Comparator.comparing(Employee::getJoiningDate);
        };
        // tie break on id so paging is stable when two rows have the same salary
        comparator = comparator.thenComparing(Employee::getId);
        return ascending ? comparator : comparator.reversed();
    }
}
