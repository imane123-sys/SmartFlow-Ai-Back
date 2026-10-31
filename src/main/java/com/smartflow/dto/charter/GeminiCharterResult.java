package com.smartflow.dto.charter;

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
public class GeminiCharterResult {

    private String title;
    private String objective;
    private String safetyPrecautions;
    private String requiredResources;
    private List<GeminiCharterStepResult> steps;
    private String rawResponse;
}
