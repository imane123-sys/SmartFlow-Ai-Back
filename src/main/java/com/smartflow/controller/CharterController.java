package com.smartflow.controller;

import com.smartflow.dto.charter.CharterCreateRequestDTO;
import com.smartflow.dto.charter.CharterRejectRequestDTO;
import com.smartflow.dto.charter.CharterResponseDTO;
import com.smartflow.dto.charter.CharterUpdateRequestDTO;
import com.smartflow.service.CharterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Chartes d'Intervention", description = "Endpoints de génération, édition, validation et cycle de vie des chartes d'intervention")
@SecurityRequirement(name = "bearerAuth")
public class CharterController {

    private final CharterService charterService;

    @PostMapping("/incidents/{incidentId}/charter-draft")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Générer une proposition de charte d'intervention par l'IA Gemini (Brouillon)")
    public ResponseEntity<CharterResponseDTO> generateCharterDraft(
            @PathVariable Long incidentId,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.generateCharterDraftFromAi(incidentId, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/incidents/{incidentId}/charters")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Créer manuellement une charte d'intervention (Brouillon)")
    public ResponseEntity<CharterResponseDTO> createManualCharter(
            @PathVariable Long incidentId,
            @Valid @RequestBody CharterCreateRequestDTO request,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.createManualCharter(incidentId, request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/charters/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter le détail d'une charte d'intervention et ses étapes")
    public ResponseEntity<CharterResponseDTO> getCharterById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.getCharterById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{incidentId}/charters")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter l'historique et les versions des chartes d'un incident")
    public ResponseEntity<List<CharterResponseDTO>> getChartersByIncident(
            @PathVariable Long incidentId,
            Authentication authentication
    ) {
        List<CharterResponseDTO> response = charterService.getChartersByIncident(incidentId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/charters/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Modifier une charte au statut BROUILLON ou REFUSEE")
    public ResponseEntity<CharterResponseDTO> updateCharter(
            @PathVariable Long id,
            @Valid @RequestBody CharterUpdateRequestDTO request,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.updateCharter(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charters/{id}/submit")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Soumettre une charte pour validation")
    public ResponseEntity<CharterResponseDTO> submitCharter(
            @PathVariable Long id,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.submitCharter(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charters/{id}/validate")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Valider définitivement une charte d'intervention (Manager / Admin)")
    public ResponseEntity<CharterResponseDTO> validateCharter(
            @PathVariable Long id,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.validateCharter(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charters/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Refuser une charte d'intervention avec un motif explicite obligatoire")
    public ResponseEntity<CharterResponseDTO> rejectCharter(
            @PathVariable Long id,
            @Valid @RequestBody CharterRejectRequestDTO request,
            Authentication authentication
    ) {
        CharterResponseDTO response = charterService.rejectCharter(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
