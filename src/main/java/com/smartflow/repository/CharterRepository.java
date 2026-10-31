package com.smartflow.repository;

import com.smartflow.entity.Charter;
import com.smartflow.entity.CharterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharterRepository extends JpaRepository<Charter, Long> {

    List<Charter> findByIncidentIdOrderByVersionNumberDesc(Long incidentId);

    Optional<Charter> findByIncidentIdAndStatus(Long incidentId, CharterStatus status);

    int countByIncidentId(Long incidentId);
}
