package com.smartflow.repository;

import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByReference(String reference);

    boolean existsByReference(String reference);

    Page<Incident> findByReporterId(Long reporterId, Pageable pageable);

    Page<Incident> findByAssignedToId(Long assignedToId, Pageable pageable);

    Page<Incident> findByServiceId(Long serviceId, Pageable pageable);

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findByPriority(Priority priority, Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(IncidentStatus status);
}
