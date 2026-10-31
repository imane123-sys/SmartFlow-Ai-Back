package com.smartflow.dto.intervention;

import jakarta.validation.constraints.NotNull;
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
public class InterventionCreateRequestDTO {

    @NotNull(message = "L'identifiant de la charte est obligatoire")
    private Long charterId;

    private Long assignedAgentId;

    private LocalDateTime scheduledStartDate;
}
