package com.smartflow.dto.charter;

import com.smartflow.entity.CharterStatus;
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
public class CharterResponseDTO {

    private Long id;
    private Long incidentId;
    private String incidentReference;
    private String title;
    private String objective;
    private String safetyPrecautions;
    private String requiredResources;
    private Integer versionNumber;
    private CharterStatus status;
    private String rejectionReason;
    private Long createdById;
    private String createdByName;
    private Long validatedById;
    private String validatedByName;
    private LocalDateTime validatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CharterStepResponseDTO> steps;
}
