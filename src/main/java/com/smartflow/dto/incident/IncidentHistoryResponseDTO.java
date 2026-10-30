package com.smartflow.dto.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentHistoryResponseDTO {

    private Long id;
    private String actionType;
    private String oldValue;
    private String newValue;
    private String comment;
    private Long operatorId;
    private String operatorName;
    private String operatorEmail;
    private LocalDateTime createdAt;
}
