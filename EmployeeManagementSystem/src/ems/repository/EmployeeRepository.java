package ems.repository;

import ems.model.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository {

    Employee save(Employee employee);

    Optional<Employee> findById(String id);

    Optional<Employee> findByEmail(String email);

    List<Employee> findAll();

    /** Direct reports of a manager - kept as an index so it is O(1). */
    List<Employee> findByManagerId(String managerId);

    boolean deleteById(String id);

    int count();
}
