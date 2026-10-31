package com.smartflow.dto.notification;

import com.smartflow.entity.NotificationType;
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
public class NotificationResponseDTO {

    private Long id;
    private Long recipientId;
    private String recipientEmail;
    private String title;
    private String message;
    private NotificationType type;
    private Boolean isRead;
    private LocalDateTime readAt;
    private Long incidentId;
    private String incidentReference;
    private Long interventionId;
    private String interventionReference;
    private Long charterId;
    private LocalDateTime createdAt;
}
