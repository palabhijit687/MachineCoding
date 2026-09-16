package ems.model;

public enum EmployeeStatus {
    ACTIVE,
    ON_LEAVE,
    /** Soft deleted - we keep the row for history/audit instead of removing it. */
    EXITED
}
