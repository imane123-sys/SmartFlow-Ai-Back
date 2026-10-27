package com.smartflow.dto.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponseDTO {

    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
}
