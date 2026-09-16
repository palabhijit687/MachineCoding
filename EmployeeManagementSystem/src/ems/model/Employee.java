package ems.model;

import java.time.LocalDate;

/**
 * Employee record. Built with a builder because there are 7+ fields and a
 * constructor with that many args is impossible to read at the call site.
 *
 * id is assigned by the service, everything else comes from the caller.
 */
public class Employee {

    private String id;
    private String name;
    private String email;
    private Department department;
    private String designation;
    private double salary;
    private String managerId;         // null for the CEO / top of the tree
    private LocalDate joiningDate;
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    private Employee() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public double getSalary() {
        return salary;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }

    /** Defensive copy so callers cannot mutate what is inside the repository. */
    public Employee copy() {
        Employee e = new Employee();
        e.id = id;
        e.name = name;
        e.email = email;
        e.department = department;
        e.designation = designation;
        e.salary = salary;
        e.managerId = managerId;
        e.joiningDate = joiningDate;
        e.status = status;
        return e;
    }

    @Override
    public String toString() {
        return String.format("%-6s %-16s %-12s %-18s %10.2f  mgr=%-6s %-8s %s",
                id, name, department, designation, salary,
                managerId == null ? "-" : managerId, status, joiningDate);
    }

    public static class Builder {

        private final Employee employee = new Employee();

        public Builder name(String name) {
            employee.name = name;
            return this;
        }

        public Builder email(String email) {
            employee.email = email;
            return this;
        }

        public Builder department(Department department) {
            employee.department = department;
            return this;
        }

        public Builder designation(String designation) {
            employee.designation = designation;
            return this;
        }

        public Builder salary(double salary) {
            employee.salary = salary;
            return this;
        }

        public Builder managerId(String managerId) {
            employee.managerId = managerId;
            return this;
        }

        public Builder joiningDate(LocalDate joiningDate) {
            employee.joiningDate = joiningDate;
            return this;
        }

        public Builder status(EmployeeStatus status) {
            employee.status = status;
            return this;
        }

        public Employee build() {
            return employee;
        }
    }
}
