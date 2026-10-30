package com.smartflow.repository;

import com.smartflow.entity.AiAnalysis;
import com.smartflow.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<AiAnalysis> findByIncidentOrderByCreatedAtDesc(Incident incident);
}
