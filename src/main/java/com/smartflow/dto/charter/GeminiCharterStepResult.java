package com.smartflow.dto.charter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiCharterStepResult {

    private Integer stepOrder;
    private String title;
    private String description;
    private Integer estimatedDurationMinutes;
}
