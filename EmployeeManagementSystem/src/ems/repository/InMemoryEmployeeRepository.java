package ems.repository;

import ems.model.Employee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store with two secondary indexes:
 *   email   -> id          (uniqueness check without scanning everyone)
 *   manager -> reportees   (org tree walk without scanning everyone)
 *
 * save() is an upsert and keeps both indexes in sync, so it is synchronized. The
 * reads are plain map lookups.
 */
public class InMemoryEmployeeRepository implements EmployeeRepository {

    private final Map<String, Employee> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByEmail = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> reporteesByManager = new ConcurrentHashMap<>();

    @Override
    public synchronized Employee save(Employee employee) {
        Employee previous = byId.get(employee.getId());
        if (previous != null) {
            // clean up the old index entries before writing the new ones
            if (!previous.getEmail().equalsIgnoreCase(employee.getEmail())) {
                idByEmail.remove(previous.getEmail().toLowerCase());
            }
            if (previous.getManagerId() != null
                    && !previous.getManagerId().equals(employee.getManagerId())) {
                Set<String> old = reporteesByManager.get(previous.getManagerId());
                if (old != null) {
                    old.remove(previous.getId());
                }
            }
        }

        byId.put(employee.getId(), employee.copy());
        idByEmail.put(employee.getEmail().toLowerCase(), employee.getId());
        if (employee.getManagerId() != null) {
            reporteesByManager
                    .computeIfAbsent(employee.getManagerId(), k -> new LinkedHashSet<>())
                    .add(employee.getId());
        }
        return employee.copy();
    }

    @Override
    public Optional<Employee> findById(String id) {
        return Optional.ofNullable(byId.get(id)).map(Employee::copy);
    }

    @Override
    public Optional<Employee> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String id = idByEmail.get(email.toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public List<Employee> findAll() {
        // sorted by id so listings are stable and easy to eyeball
        List<Employee> all = new ArrayList<>();
        byId.values().forEach(e -> all.add(e.copy()));
        all.sort(Comparator.comparing(Employee::getId));
        return all;
    }

    @Override
    public List<Employee> findByManagerId(String managerId) {
        Set<String> ids = reporteesByManager.getOrDefault(managerId, Set.of());
        List<Employee> reportees = new ArrayList<>();
        for (String id : ids) {
            findById(id).ifPresent(reportees::add);
        }
        reportees.sort(Comparator.comparing(Employee::getId));
        return reportees;
    }

    @Override
    public synchronized boolean deleteById(String id) {
        Employee removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        idByEmail.remove(removed.getEmail().toLowerCase());
        if (removed.getManagerId() != null) {
            Set<String> siblings = reporteesByManager.get(removed.getManagerId());
            if (siblings != null) {
                siblings.remove(id);
            }
        }
        reporteesByManager.remove(id);
        return true;
    }

    @Override
    public int count() {
        return byId.size();
    }
}
