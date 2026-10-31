package com.smartflow.dto.intervention;

import com.smartflow.entity.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionStepResponseDTO {

    private Long id;
    private Long charterStepId;
    private Integer stepOrder;
    private String title;
    private String description;
    private Integer estimatedDurationMinutes;
    private Integer actualDurationMinutes;
    private StepStatus status;
    private String blockReason;
    private String agentNotes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
