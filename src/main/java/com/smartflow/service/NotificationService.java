package com.smartflow.service;

import com.smartflow.dto.notification.NotificationCreateRequestDTO;
import com.smartflow.dto.notification.NotificationResponseDTO;
import com.smartflow.dto.notification.NotificationSummaryDTO;
import com.smartflow.entity.Charter;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Intervention;
import com.smartflow.entity.InterventionStep;
import com.smartflow.entity.Notification;
import com.smartflow.entity.NotificationType;
import com.smartflow.entity.Role;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.InterventionRepository;
import com.smartflow.repository.NotificationRepository;
import com.smartflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final InterventionRepository interventionRepository;
    private final CharterRepository charterRepository;

    @Transactional
    public NotificationResponseDTO sendNotification(
            User recipient,
            String title,
            String message,
            NotificationType type,
            Incident incident,
            Intervention intervention,
            Charter charter) {

        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .incident(incident)
                .intervention(intervention)
                .charter(charter)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification envoyée à {} (Type : {})", recipient.getEmail(), type);
        return mapToDTO(saved);
    }

    @Transactional
    public List<NotificationResponseDTO> sendBulkNotifications(
            List<User> recipients,
            String title,
            String message,
            NotificationType type,
            Incident incident,
            Intervention intervention,
            Charter charter) {

        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }

        Set<Long> seenUserIds = new HashSet<>();
        List<Notification> notificationsToSave = new ArrayList<>();

        for (User user : recipients) {
            if (user != null && user.getId() != null && seenUserIds.add(user.getId())) {
                notificationsToSave.add(Notification.builder()
                        .recipient(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .incident(incident)
                        .intervention(intervention)
                        .charter(charter)
                        .build());
            }
        }

        List<Notification> saved = notificationRepository.saveAll(notificationsToSave);
        return saved.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public NotificationResponseDTO createManualNotification(NotificationCreateRequestDTO request, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        if (currentUser.getRole() != Role.ADMIN) {
            throw new BusinessException("Seul un administrateur peut émettre manuellement des alertes système");
        }

        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire non trouvé avec l'id : " + request.getRecipientId()));

        Incident incident = null;
        if (request.getIncidentId() != null) {
            incident = incidentRepository.findById(request.getIncidentId()).orElse(null);
        }

        Intervention intervention = null;
        if (request.getInterventionId() != null) {
            intervention = interventionRepository.findById(request.getInterventionId()).orElse(null);
        }

        Charter charter = null;
        if (request.getCharterId() != null) {
            charter = charterRepository.findById(request.getCharterId()).orElse(null);
        }

        return sendNotification(recipient, request.getTitle().trim(), request.getMessage().trim(), request.getType(), incident, intervention, charter);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponseDTO> getUserNotifications(Boolean unreadOnly, Pageable pageable, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);

        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(currentUser.getId(), false, pageable)
                    .map(this::mapToDTO);
        }

        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDTO getNotificationSummary(String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
        List<NotificationResponseDTO> recent = notificationRepository.findTop5ByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return NotificationSummaryDTO.builder()
                .unreadCount(unreadCount)
                .recentNotifications(recent)
                .build();
    }

    @Transactional
    public NotificationResponseDTO markAsRead(Long notificationId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée avec l'id : " + notificationId));

        if (Boolean.FALSE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return mapToDTO(notification);
    }

    @Transactional
    public int markAllAsRead(String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        return notificationRepository.markAllAsReadByRecipientId(currentUser.getId(), LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(Long notificationId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée avec l'id : " + notificationId));

        notificationRepository.delete(notification);
    }

    @Transactional
    public void notifyIncidentCreated(Incident incident) {
        List<User> recipients = new ArrayList<>();
        if (incident.getService() != null) {
            recipients.addAll(userRepository.findByServiceIdAndRoleAndActive(incident.getService().getId(), Role.MANAGER, true));
        }
        recipients.addAll(userRepository.findByRoleAndActive(Role.ADMIN, true));

        recipients.removeIf(u -> u.getId().equals(incident.getReporter().getId()));

        String title = "Nouvel incident : " + incident.getReference();
        String message = "Un nouvel incident \"" + incident.getTitle() + "\" a été signalé par " + incident.getReporter().getFullName() + ".";

        sendBulkNotifications(recipients, title, message, NotificationType.INCIDENT_CREATED, incident, null, null);
    }

    @Transactional
    public void notifyIncidentStatusChanged(Incident incident, IncidentStatus oldStatus, IncidentStatus newStatus) {
        List<User> recipients = new ArrayList<>();
        if (incident.getReporter() != null) {
            recipients.add(incident.getReporter());
        }
        if (incident.getAssignedTo() != null) {
            recipients.add(incident.getAssignedTo());
        }

        String title = "Mise à jour incident : " + incident.getReference();
        String message = "Le statut de l'incident est passé de " + oldStatus + " à " + newStatus + ".";

        sendBulkNotifications(recipients, title, message, NotificationType.INCIDENT_STATUS_CHANGED, incident, null, null);
    }

    @Transactional
    public void notifyCharterValidated(Charter charter) {
        List<User> recipients = new ArrayList<>();
        if (charter.getCreatedBy() != null) {
            recipients.add(charter.getCreatedBy());
        }

        String title = "Charte validée : " + charter.getTitle();
        String message = "La charte d'intervention (v" + charter.getVersionNumber() + ") pour l'incident "
                + charter.getIncident().getReference() + " a été validée.";

        sendBulkNotifications(recipients, title, message, NotificationType.CHARTER_VALIDATED, charter.getIncident(), null, charter);
    }

    @Transactional
    public void notifyCharterRejected(Charter charter) {
        List<User> recipients = new ArrayList<>();
        if (charter.getCreatedBy() != null) {
            recipients.add(charter.getCreatedBy());
        }

        String title = "Charte refusée : " + charter.getTitle();
        String message = "La charte d'intervention (v" + charter.getVersionNumber() + ") a été refusée. Motif : "
                + (charter.getRejectionReason() != null ? charter.getRejectionReason() : "Non précisé");

        sendBulkNotifications(recipients, title, message, NotificationType.CHARTER_REJECTED, charter.getIncident(), null, charter);
    }

    @Transactional
    public void notifyInterventionAssigned(Intervention intervention) {
        if (intervention.getAssignedAgent() == null) {
            return;
        }

        String title = "Intervention affectée : " + intervention.getReference();
        String message = "Vous avez été affecté à l'intervention " + intervention.getReference()
                + " concernant l'incident " + intervention.getIncident().getReference() + ".";

        sendNotification(intervention.getAssignedAgent(), title, message, NotificationType.INTERVENTION_ASSIGNED,
                intervention.getIncident(), intervention, intervention.getCharter());
    }

    @Transactional
    public void notifyInterventionBlocked(Intervention intervention, InterventionStep step) {
        List<User> recipients = new ArrayList<>();
        if (intervention.getIncident().getService() != null) {
            recipients.addAll(userRepository.findByServiceIdAndRoleAndActive(intervention.getIncident().getService().getId(), Role.MANAGER, true));
        }
        recipients.addAll(userRepository.findByRoleAndActive(Role.ADMIN, true));

        String title = "ALERTE : Intervention bloquée (" + intervention.getReference() + ")";
        String message = "L'intervention " + intervention.getReference() + " est bloquée sur l'étape \""
                + step.getTitle() + "\". Motif : " + step.getBlockReason();

        sendBulkNotifications(recipients, title, message, NotificationType.INTERVENTION_BLOCKED,
                intervention.getIncident(), intervention, intervention.getCharter());
    }

    @Transactional
    public void notifyInterventionCompleted(Intervention intervention) {
        List<User> recipients = new ArrayList<>();
        if (intervention.getIncident().getReporter() != null) {
            recipients.add(intervention.getIncident().getReporter());
        }
        if (intervention.getCreatedBy() != null) {
            recipients.add(intervention.getCreatedBy());
        }

        String title = "Intervention terminée : " + intervention.getReference();
        String message = "L'intervention est terminée et l'incident " + intervention.getIncident().getReference()
                + " est désormais résolu.";

        sendBulkNotifications(recipients, title, message, NotificationType.INTERVENTION_COMPLETED,
                intervention.getIncident(), intervention, intervention.getCharter());
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));
    }

    public NotificationResponseDTO mapToDTO(Notification notification) {
        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .recipientId(notification.getRecipient().getId())
                .recipientEmail(notification.getRecipient().getEmail())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .incidentId(notification.getIncident() != null ? notification.getIncident().getId() : null)
                .incidentReference(notification.getIncident() != null ? notification.getIncident().getReference() : null)
                .interventionId(notification.getIntervention() != null ? notification.getIntervention().getId() : null)
                .interventionReference(notification.getIntervention() != null ? notification.getIntervention().getReference() : null)
                .charterId(notification.getCharter() != null ? notification.getCharter().getId() : null)
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
