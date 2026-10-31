package com.smartflow.dto.intervention;

import jakarta.validation.constraints.NotNull;
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
public class InterventionAssignRequestDTO {

    @NotNull(message = "L'identifiant de l'agent est obligatoire")
    private Long agentId;
}
