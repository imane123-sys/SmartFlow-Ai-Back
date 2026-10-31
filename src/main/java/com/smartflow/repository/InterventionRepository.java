package com.smartflow.repository;

import com.smartflow.entity.Intervention;
import com.smartflow.entity.InterventionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long>, JpaSpecificationExecutor<Intervention> {

    Optional<Intervention> findByReference(String reference);

    boolean existsByReference(String reference);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Intervention> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<Intervention> findByCharterId(Long charterId);

    boolean existsByCharterIdAndStatusNotIn(Long charterId, List<InterventionStatus> statuses);

    long countByAssignedAgentIdAndStatus(Long agentId, InterventionStatus status);
}
