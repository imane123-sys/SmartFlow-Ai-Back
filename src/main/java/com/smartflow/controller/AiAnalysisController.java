package com.smartflow.controller;

import com.smartflow.dto.ai.AiAnalysisResponseDTO;
import com.smartflow.service.AiAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Analyses IA", description = "Endpoints de diagnostic et d'analyse des incidents via Google Gemini")
@SecurityRequirement(name = "bearerAuth")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/incidents/{id}/analyze")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Lancer l'analyse intelligente d'un incident par l'IA Gemini")
    public ResponseEntity<AiAnalysisResponseDTO> analyzeIncident(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AiAnalysisResponseDTO response = aiAnalysisService.analyzeIncident(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{id}/analyses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter la liste des analyses IA réalisées pour un incident")
    public ResponseEntity<List<AiAnalysisResponseDTO>> getAnalysesByIncident(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<AiAnalysisResponseDTO> response = aiAnalysisService.getAnalysesByIncident(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analyses/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter le détail d'une analyse IA spécifique")
    public ResponseEntity<AiAnalysisResponseDTO> getAnalysisById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AiAnalysisResponseDTO response = aiAnalysisService.getAnalysisById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
