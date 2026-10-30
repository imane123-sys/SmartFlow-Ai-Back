package com.smartflow.dto.incident;

import com.smartflow.entity.IncidentStatus;
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
public class IncidentStatusUpdateDTO {

    @NotNull(message = "Le nouveau statut est obligatoire")
    private IncidentStatus status;

    @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères")
    private String comment;
}
