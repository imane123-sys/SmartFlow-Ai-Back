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
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.InterventionRepository;
import com.smartflow.repository.NotificationRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private InterventionRepository interventionRepository;

    @Mock
    private CharterRepository charterRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User admin;
    private User manager;
    private User agent;
    private User employee;
    private Service serviceInformatique;
    private Incident incident;
    private Charter charter;
    private Intervention intervention;
    private InterventionStep step;
    private Notification notification;

    @BeforeEach
    void setUp() {
        serviceInformatique = Service.builder()
                .id(1L)
                .name("Informatique")
                .active(true)
                .build();

        admin = User.builder()
                .id(1L)
                .email("admin@smartflow.com")
                .firstName("Admin")
                .lastName("System")
                .role(Role.ADMIN)
                .active(true)
                .build();

        manager = User.builder()
                .id(2L)
                .email("manager@smartflow.com")
                .firstName("Marc")
                .lastName("Manager")
                .role(Role.MANAGER)
                .service(serviceInformatique)
                .active(true)
                .build();

        agent = User.builder()
                .id(3L)
                .email("agent@smartflow.com")
                .firstName("Alain")
                .lastName("Agent")
                .role(Role.AGENT)
                .service(serviceInformatique)
                .active(true)
                .build();

        employee = User.builder()
                .id(4L)
                .email("employee@smartflow.com")
                .firstName("Emma")
                .lastName("Employee")
                .role(Role.EMPLOYEE)
                .service(serviceInformatique)
                .active(true)
                .build();

        incident = Incident.builder()
                .id(10L)
                .reference("INC-202610-0010")
                .title("Panne réseau")
                .service(serviceInformatique)
                .reporter(employee)
                .assignedTo(agent)
                .status(IncidentStatus.ASSIGNE)
                .build();

        charter = Charter.builder()
                .id(20L)
                .incident(incident)
                .title("Charte réseau")
                .versionNumber(1)
                .createdBy(manager)
                .rejectionReason("Objectif imprécis")
                .build();

        intervention = Intervention.builder()
                .id(30L)
                .reference("INT-202610-0001")
                .incident(incident)
                .charter(charter)
                .assignedAgent(agent)
                .createdBy(manager)
                .report("Câble remplacé")
                .build();

        step = InterventionStep.builder()
                .id(201L)
                .stepOrder(1)
                .title("Changement switch")
                .blockReason("Switch de rechange indisponible")
                .build();

        notification = Notification.builder()
                .id(100L)
                .recipient(employee)
                .title("Incident mis à jour")
                .message("Votre incident est assigné")
                .type(NotificationType.INCIDENT_ASSIGNED)
                .isRead(false)
                .incident(incident)
                .build();
    }

    @Test
    void sendNotification_ShouldSaveAndReturnDTO() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(101L);
            return n;
        });

        NotificationResponseDTO result = notificationService.sendNotification(
                employee,
                "Test",
                "Message de test",
                NotificationType.SYSTEM_ALERT,
                incident,
                null,
                null
        );

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("Test", result.getTitle());
        assertEquals(NotificationType.SYSTEM_ALERT, result.getType());
        assertFalse(result.getIsRead());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendBulkNotifications_ShouldDeduplicateAndSaveAll() {
        List<User> recipients = List.of(employee, employee, manager);

        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Notification> list = invocation.getArgument(0);
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setId((long) (i + 1));
            }
            return list;
        });

        List<NotificationResponseDTO> results = notificationService.sendBulkNotifications(
                recipients,
                "Alerte générale",
                "Maintenance planifiée",
                NotificationType.SYSTEM_ALERT,
                null,
                null,
                null
        );

        assertNotNull(results);
        assertEquals(2, results.size());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void createManualNotification_WhenAdmin_ShouldCreateSuccessfully() {
        NotificationCreateRequestDTO request = NotificationCreateRequestDTO.builder()
                .recipientId(4L)
                .title("Alerte manuelle")
                .message("Message administrateur")
                .type(NotificationType.SYSTEM_ALERT)
                .incidentId(10L)
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.findById(4L)).thenReturn(Optional.of(employee));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(102L);
            return n;
        });

        NotificationResponseDTO result = notificationService.createManualNotification(request, admin.getEmail());

        assertNotNull(result);
        assertEquals(102L, result.getId());
        assertEquals("Alerte manuelle", result.getTitle());
    }

    @Test
    void createManualNotification_WhenNotAdmin_ShouldThrowBusinessException() {
        NotificationCreateRequestDTO request = NotificationCreateRequestDTO.builder()
                .recipientId(4L)
                .title("Alerte manuelle")
                .message("Message non autorisé")
                .type(NotificationType.SYSTEM_ALERT)
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));

        assertThrows(BusinessException.class, () ->
                notificationService.createManualNotification(request, employee.getEmail())
        );
    }

    @Test
    void getUserNotifications_WhenUnreadOnlyFalse_ShouldReturnAllUserNotifications() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(4L, pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponseDTO> result = notificationService.getUserNotifications(false, pageable, employee.getEmail());

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(100L, result.getContent().get(0).getId());
    }

    @Test
    void getUserNotifications_WhenUnreadOnlyTrue_ShouldReturnUnreadOnly() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(4L, false, pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponseDTO> result = notificationService.getUserNotifications(true, pageable, employee.getEmail());

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getUnreadCount_ShouldReturnCount() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(4L)).thenReturn(5L);

        long count = notificationService.getUnreadCount(employee.getEmail());

        assertEquals(5L, count);
    }

    @Test
    void getNotificationSummary_ShouldReturnCountAndRecent() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(4L)).thenReturn(1L);
        when(notificationRepository.findTop5ByRecipientIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(notification));

        NotificationSummaryDTO summary = notificationService.getNotificationSummary(employee.getEmail());

        assertNotNull(summary);
        assertEquals(1L, summary.getUnreadCount());
        assertEquals(1, summary.getRecentNotifications().size());
    }

    @Test
    void markAsRead_WhenUnread_ShouldMarkAsReadAndSetReadAt() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByIdAndRecipientId(100L, 4L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        NotificationResponseDTO result = notificationService.markAsRead(100L, employee.getEmail());

        assertNotNull(result);
        assertTrue(notification.getIsRead());
        assertNotNull(notification.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_WhenNotFoundOrNotOwner_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByIdAndRecipientId(999L, 4L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.markAsRead(999L, employee.getEmail())
        );
    }

    @Test
    void markAllAsRead_ShouldCallRepository() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.markAllAsReadByRecipientId(eq(4L), any(LocalDateTime.class))).thenReturn(3);

        int count = notificationService.markAllAsRead(employee.getEmail());

        assertEquals(3, count);
        verify(notificationRepository).markAllAsReadByRecipientId(eq(4L), any(LocalDateTime.class));
    }

    @Test
    void deleteNotification_WhenFound_ShouldDelete() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByIdAndRecipientId(100L, 4L)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(100L, employee.getEmail());

        verify(notificationRepository).delete(notification);
    }

    @Test
    void deleteNotification_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByIdAndRecipientId(999L, 4L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.deleteNotification(999L, employee.getEmail())
        );
    }

    @Test
    void notifyIncidentCreated_ShouldNotifyManagersAndAdmins() {
        when(userRepository.findByServiceIdAndRoleAndActive(1L, Role.MANAGER, true)).thenReturn(List.of(manager));
        when(userRepository.findByRoleAndActive(Role.ADMIN, true)).thenReturn(List.of(admin));
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyIncidentCreated(incident);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void notifyIncidentStatusChanged_ShouldNotifyReporterAndAssignedAgent() {
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyIncidentStatusChanged(incident, IncidentStatus.NOUVEAU, IncidentStatus.ASSIGNE);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void notifyCharterValidated_ShouldNotifyCreator() {
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyCharterValidated(charter);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void notifyCharterRejected_ShouldNotifyCreatorWithReason() {
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyCharterRejected(charter);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void notifyInterventionAssigned_ShouldNotifyAgent() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyInterventionAssigned(intervention);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyInterventionBlocked_ShouldNotifyManagersAndAdmins() {
        when(userRepository.findByServiceIdAndRoleAndActive(1L, Role.MANAGER, true)).thenReturn(List.of(manager));
        when(userRepository.findByRoleAndActive(Role.ADMIN, true)).thenReturn(List.of(admin));
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyInterventionBlocked(intervention, step);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void notifyInterventionCompleted_ShouldNotifyReporterAndCreator() {
        when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.notifyInterventionCompleted(intervention);

        verify(notificationRepository).saveAll(anyList());
    }
}
