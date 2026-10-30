package com.smartflow.controller;

import com.smartflow.dto.incident.IncidentAssignDTO;
import com.smartflow.dto.incident.IncidentCreateRequestDTO;
import com.smartflow.dto.incident.IncidentHistoryResponseDTO;
import com.smartflow.dto.incident.IncidentResponseDTO;
import com.smartflow.dto.incident.IncidentStatusUpdateDTO;
import com.smartflow.dto.incident.IncidentUpdateRequestDTO;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import com.smartflow.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Endpoints de gestion du cycle de vie des incidents")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    @Operation(summary = "Déclarer un nouvel incident")
    public ResponseEntity<IncidentResponseDTO> createIncident(
            @Valid @RequestBody IncidentCreateRequestDTO request,
            Authentication authentication) {
        IncidentResponseDTO response = incidentService.createIncident(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Rechercher et lister les incidents avec pagination et filtres")
    public ResponseEntity<Page<IncidentResponseDTO>> searchIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<IncidentResponseDTO> result = incidentService.searchIncidents(
                status, priority, serviceId, categoryId, search, pageable, authentication.getName()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir le détail d'un incident par ID")
    public ResponseEntity<IncidentResponseDTO> getIncidentById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncidentById(id, authentication.getName()));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Obtenir le détail d'un incident par référence (ex: INC-202610-0001)")
    public ResponseEntity<IncidentResponseDTO> getIncidentByReference(
            @PathVariable String reference,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncidentByReference(reference, authentication.getName()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour les informations d'un incident")
    public ResponseEntity<IncidentResponseDTO> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentUpdateRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un incident")
    public ResponseEntity<IncidentResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody IncidentStatusUpdateDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Assigner un incident à un agent (Réservé Manager / Admin)")
    public ResponseEntity<IncidentResponseDTO> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentAssignDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.assignIncident(id, request, authentication.getName()));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Consulter l'historique et la timeline des événements d'un incident")
    public ResponseEntity<List<IncidentHistoryResponseDTO>> getIncidentHistory(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(incidentService.getIncidentHistory(id, authentication.getName()));
    }
}
