package com.smartflow.controller;

import com.smartflow.dto.notification.NotificationCreateRequestDTO;
import com.smartflow.dto.notification.NotificationResponseDTO;
import com.smartflow.dto.notification.NotificationSummaryDTO;
import com.smartflow.service.NotificationService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Endpoints de consultation, marquage et gestion des notifications internes")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter les notifications de l'utilisateur connecté avec pagination")
    public ResponseEntity<Page<NotificationResponseDTO>> getUserNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<NotificationResponseDTO> notifications = notificationService.getUserNotifications(
                unreadOnly,
                pageable,
                authentication.getName()
        );
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtenir le nombre de notifications non lues")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        long count = notificationService.getUnreadCount(authentication.getName());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtenir un résumé des notifications (compteur non lu + 5 dernières notifications)")
    public ResponseEntity<NotificationSummaryDTO> getNotificationSummary(Authentication authentication) {
        NotificationSummaryDTO summary = notificationService.getNotificationSummary(authentication.getName());
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<NotificationResponseDTO> markAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        NotificationResponseDTO response = notificationService.markAsRead(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {
        int updatedCount = notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(Map.of("updatedCount", updatedCount));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Supprimer une notification")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id,
            Authentication authentication
    ) {
        notificationService.deleteNotification(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Émettre manuellement une alerte système (Administrateur uniquement)")
    public ResponseEntity<NotificationResponseDTO> createManualNotification(
            @Valid @RequestBody NotificationCreateRequestDTO request,
            Authentication authentication
    ) {
        NotificationResponseDTO response = notificationService.createManualNotification(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
