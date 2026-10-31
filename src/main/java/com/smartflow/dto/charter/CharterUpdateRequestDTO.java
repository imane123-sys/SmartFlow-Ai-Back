package com.smartflow.dto.charter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class CharterUpdateRequestDTO {

    @NotBlank(message = "Le titre de la charte est obligatoire")
    @Size(min = 5, max = 150, message = "Le titre doit comporter entre 5 et 150 caractères")
    private String title;

    @NotBlank(message = "L'objectif de la charte est obligatoire")
    private String objective;

    private String safetyPrecautions;

    private String requiredResources;

    @NotEmpty(message = "La charte doit comporter au moins une étape")
    @Valid
    private List<CharterStepRequestDTO> steps;
}
