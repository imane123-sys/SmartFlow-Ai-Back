package com.smartflow.repository;

import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class IncidentSpecification {

    private IncidentSpecification() {
    }

    public static Specification<Incident> withFilters(
            IncidentStatus status,
            Priority priority,
            Long serviceId,
            Long categoryId,
            Long reporterId,
            Long assignedToId,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (serviceId != null) {
                predicates.add(cb.equal(root.get("service").get("id"), serviceId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (reporterId != null) {
                predicates.add(cb.equal(root.get("reporter").get("id"), reporterId));
            }
            if (assignedToId != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedToId));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titleLike = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                Predicate refLike = cb.like(cb.lower(root.get("reference")), pattern);
                predicates.add(cb.or(titleLike, descLike, refLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
