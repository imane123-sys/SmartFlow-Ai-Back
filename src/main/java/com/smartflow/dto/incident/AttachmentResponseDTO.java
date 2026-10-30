package com.smartflow.dto.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponseDTO {

    private Long id;
    private String originalName;
    private String storedName;
    private String mimeType;
    private Long fileSize;
    private String storagePath;
    private LocalDateTime createdAt;
}
