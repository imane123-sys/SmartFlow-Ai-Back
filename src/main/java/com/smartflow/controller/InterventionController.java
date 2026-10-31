package com.smartflow.controller;

import com.smartflow.dto.intervention.InterventionAssignRequestDTO;
import com.smartflow.dto.intervention.InterventionCancelRequestDTO;
import com.smartflow.dto.intervention.InterventionCompleteRequestDTO;
import com.smartflow.dto.intervention.InterventionCreateRequestDTO;
import com.smartflow.dto.intervention.InterventionResponseDTO;
import com.smartflow.dto.intervention.InterventionStepUpdateRequestDTO;
import com.smartflow.entity.InterventionStatus;
import com.smartflow.service.InterventionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Interventions", description = "Endpoints de planification, affectation, suivi terrain et clôture des interventions")
@SecurityRequirement(name = "bearerAuth")
public class InterventionController {

    private final InterventionService interventionService;

    @PostMapping("/interventions")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Créer et planifier une nouvelle intervention à partir d'une charte validée")
    public ResponseEntity<InterventionResponseDTO> createIntervention(
            @Valid @RequestBody InterventionCreateRequestDTO request,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.createIntervention(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/interventions/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter le détail d'une intervention et le statut de ses étapes")
    public ResponseEntity<InterventionResponseDTO> getInterventionById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.getInterventionById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{incidentId}/interventions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lister les interventions rattachées à un incident")
    public ResponseEntity<List<InterventionResponseDTO>> getInterventionsByIncident(
            @PathVariable Long incidentId,
            Authentication authentication
    ) {
        List<InterventionResponseDTO> response = interventionService.getInterventionsByIncidentId(incidentId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/interventions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rechercher et filtrer les interventions avec pagination")
    public ResponseEntity<Page<InterventionResponseDTO>> searchInterventions(
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<InterventionResponseDTO> response = interventionService.searchInterventions(
                status,
                assignedAgentId,
                incidentId,
                search,
                pageable,
                authentication.getName()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/interventions/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Affecter un agent technique à une intervention")
    public ResponseEntity<InterventionResponseDTO> assignAgent(
            @PathVariable Long id,
            @Valid @RequestBody InterventionAssignRequestDTO request,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.assignAgent(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/interventions/{id}/start")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Démarrer l'exécution d'une intervention terrain")
    public ResponseEntity<InterventionResponseDTO> startIntervention(
            @PathVariable Long id,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.startIntervention(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/interventions/{id}/steps/{stepId}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Mettre à jour le statut, le blocage ou les notes d'une étape d'intervention")
    public ResponseEntity<InterventionResponseDTO> updateStep(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody InterventionStepUpdateRequestDTO request,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.updateStep(id, stepId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/interventions/{id}/complete")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Clôturer une intervention avec compte rendu obligatoire (résolution de l'incident)")
    public ResponseEntity<InterventionResponseDTO> completeIntervention(
            @PathVariable Long id,
            @Valid @RequestBody InterventionCompleteRequestDTO request,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.completeIntervention(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/interventions/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Annuler une intervention avec motif obligatoire")
    public ResponseEntity<InterventionResponseDTO> cancelIntervention(
            @PathVariable Long id,
            @Valid @RequestBody InterventionCancelRequestDTO request,
            Authentication authentication
    ) {
        InterventionResponseDTO response = interventionService.cancelIntervention(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
