package com.smartflow.dto.charter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CharterStepRequestDTO {

    @NotNull(message = "L'ordre de l'étape est obligatoire")
    @Positive(message = "L'ordre de l'étape doit être strictement supérieur à 0")
    private Integer stepOrder;

    @NotBlank(message = "Le titre de l'étape est obligatoire")
    @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
    private String title;

    @NotBlank(message = "La description de l'étape est obligatoire")
    private String description;

    @Positive(message = "La durée estimée doit être positive")
    private Integer estimatedDurationMinutes;
}
