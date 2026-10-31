package com.smartflow.dto.intervention;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class InterventionCancelRequestDTO {

    @NotBlank(message = "Le motif d'annulation est obligatoire")
    @Size(min = 5, max = 1000, message = "Le motif d'annulation doit contenir entre 5 et 1000 caractères")
    private String cancellationReason;
}
