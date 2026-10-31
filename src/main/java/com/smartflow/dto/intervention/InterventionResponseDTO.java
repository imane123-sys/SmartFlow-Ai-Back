package com.smartflow.dto.intervention;

import com.smartflow.entity.InterventionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionResponseDTO {

    private Long id;
    private String reference;
    private Long incidentId;
    private String incidentReference;
    private String incidentTitle;
    private Long charterId;
    private String charterTitle;
    private Integer charterVersion;
    private Long assignedAgentId;
    private String assignedAgentName;
    private String assignedAgentEmail;
    private Long createdById;
    private String createdByName;
    private InterventionStatus status;
    private LocalDateTime scheduledStartDate;
    private LocalDateTime actualStartDate;
    private LocalDateTime actualEndDate;
    private String report;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InterventionStepResponseDTO> steps;
}
