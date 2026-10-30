package com.smartflow.dto.incident;

import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponseDTO {

    private Long id;
    private String reference;
    private String title;
    private String description;
    private String location;
    private Priority priority;
    private IncidentStatus status;

    private Long categoryId;
    private String categoryName;

    private Long serviceId;
    private String serviceName;

    private Long reporterId;
    private String reporterName;
    private String reporterEmail;

    private Long assignedToId;
    private String assignedToName;
    private String assignedToEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    private List<AttachmentResponseDTO> attachments;
}
