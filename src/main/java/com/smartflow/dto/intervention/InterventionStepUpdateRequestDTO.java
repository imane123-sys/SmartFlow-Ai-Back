package com.smartflow.dto.intervention;

import com.smartflow.entity.StepStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class InterventionStepUpdateRequestDTO {

    @NotNull(message = "Le statut de l'étape est obligatoire")
    private StepStatus status;

    private String blockReason;

    private String agentNotes;

    @Positive(message = "La durée réelle doit être positive")
    private Integer actualDurationMinutes;
}
