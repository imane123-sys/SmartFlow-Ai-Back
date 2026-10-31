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
public class InterventionCompleteRequestDTO {

    @NotBlank(message = "Le compte rendu d'intervention est obligatoire")
    @Size(min = 10, max = 3000, message = "Le compte rendu doit contenir entre 10 et 3000 caractères")
    private String report;
}
