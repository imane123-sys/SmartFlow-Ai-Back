package com.smartflow.repository;

import com.smartflow.entity.InterventionStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionStepRepository extends JpaRepository<InterventionStep, Long> {

    List<InterventionStep> findByInterventionIdOrderByStepOrderAsc(Long interventionId);
}
