package com.smartflow.dto.ai;

import com.smartflow.entity.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiAnalysisResult {

    private String summary;
    private List<String> possibleCauses;
    private Priority suggestedPriority;
    private String priorityReason;
    private List<String> recommendations;
    private List<String> missingInfo;
    private String rawResponse;
}
