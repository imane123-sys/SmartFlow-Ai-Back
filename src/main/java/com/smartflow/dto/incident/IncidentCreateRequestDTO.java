package com.smartflow.dto.incident;

import com.smartflow.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCreateRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @Size(max = 200, message = "La localisation ne doit pas dépasser 200 caractères")
    private String location;

    private Priority priority;

    @NotNull(message = "L'identifiant de la catégorie est obligatoire")
    private Long categoryId;

    @NotNull(message = "L'identifiant du service est obligatoire")
    private Long serviceId;
}
