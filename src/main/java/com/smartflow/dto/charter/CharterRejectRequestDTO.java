package com.smartflow.dto.charter;

import jakarta.validation.constraints.NotBlank;
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
public class CharterRejectRequestDTO {

    @NotBlank(message = "Le motif de refus est obligatoire")
    private String rejectionReason;
}
