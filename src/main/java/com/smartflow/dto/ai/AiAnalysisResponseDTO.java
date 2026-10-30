package com.smartflow.dto.ai;

import com.smartflow.entity.Priority;
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
public class AiAnalysisResponseDTO {

    private Long id;
    private Long incidentId;
    private String incidentReference;
    private Long requestedById;
    private String requestedByName;
    private String summary;
    private List<String> possibleCauses;
    private Priority suggestedPriority;
    private String priorityReason;
    private List<String> recommendations;
    private List<String> missingInfo;
    private LocalDateTime createdAt;
}
