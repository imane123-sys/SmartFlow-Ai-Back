package com.smartflow.dto.notification;

import com.smartflow.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class NotificationCreateRequestDTO {

    @NotNull(message = "L'identifiant du destinataire est obligatoire")
    private Long recipientId;

    @NotBlank(message = "Le titre de la notification est obligatoire")
    @Size(min = 3, max = 150, message = "Le titre doit contenir entre 3 et 150 caractères")
    private String title;

    @NotBlank(message = "Le message de la notification est obligatoire")
    @Size(min = 3, max = 2000, message = "Le message doit contenir entre 3 et 2000 caractères")
    private String message;

    @NotNull(message = "Le type de notification est obligatoire")
    private NotificationType type;

    private Long incidentId;
    private Long interventionId;
    private Long charterId;
}
