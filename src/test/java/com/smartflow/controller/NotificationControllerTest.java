package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.notification.NotificationCreateRequestDTO;
import com.smartflow.dto.notification.NotificationResponseDTO;
import com.smartflow.dto.notification.NotificationSummaryDTO;
import com.smartflow.entity.NotificationType;
import com.smartflow.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void getUserNotifications_WhenAuthenticated_ShouldReturn200() throws Exception {
        NotificationResponseDTO response = NotificationResponseDTO.builder()
                .id(1L)
                .title("Alerte")
                .message("Message de test")
                .type(NotificationType.SYSTEM_ALERT)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.getUserNotifications(any(), any(), eq("employee@smartflow.com")))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].title").value("Alerte"));
    }

    @Test
    void getUserNotifications_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void getUnreadCount_WhenAuthenticated_ShouldReturn200() throws Exception {
        when(notificationService.getUnreadCount(eq("employee@smartflow.com"))).thenReturn(4L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4L));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void getNotificationSummary_WhenAuthenticated_ShouldReturn200() throws Exception {
        NotificationSummaryDTO summary = NotificationSummaryDTO.builder()
                .unreadCount(2L)
                .recentNotifications(List.of(
                        NotificationResponseDTO.builder().id(1L).title("Alerte 1").build()
                ))
                .build();

        when(notificationService.getNotificationSummary(eq("employee@smartflow.com")))
                .thenReturn(summary);

        mockMvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2L))
                .andExpect(jsonPath("$.recentNotifications[0].id").value(1L));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void markAsRead_WhenAuthenticated_ShouldReturn200() throws Exception {
        NotificationResponseDTO response = NotificationResponseDTO.builder()
                .id(1L)
                .isRead(true)
                .readAt(LocalDateTime.now())
                .build();

        when(notificationService.markAsRead(eq(1L), eq("employee@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void markAllAsRead_WhenAuthenticated_ShouldReturn200() throws Exception {
        when(notificationService.markAllAsRead(eq("employee@smartflow.com"))).thenReturn(5);

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(5));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void deleteNotification_WhenAuthenticated_ShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/notifications/1"))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(eq(1L), eq("employee@smartflow.com"));
    }

    @Test
    @WithMockUser(username = "admin@smartflow.com", roles = "ADMIN")
    void createManualNotification_WhenAdmin_ShouldReturn201() throws Exception {
        NotificationCreateRequestDTO request = NotificationCreateRequestDTO.builder()
                .recipientId(2L)
                .title("Alerte manuelle")
                .message("Message administrateur")
                .type(NotificationType.SYSTEM_ALERT)
                .build();

        NotificationResponseDTO response = NotificationResponseDTO.builder()
                .id(1L)
                .title("Alerte manuelle")
                .message("Message administrateur")
                .type(NotificationType.SYSTEM_ALERT)
                .build();

        when(notificationService.createManualNotification(any(NotificationCreateRequestDTO.class), eq("admin@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Alerte manuelle"));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void createManualNotification_WhenEmployee_ShouldReturn403() throws Exception {
        NotificationCreateRequestDTO request = NotificationCreateRequestDTO.builder()
                .recipientId(2L)
                .title("Alerte non autorisée")
                .message("Message non autorisé")
                .type(NotificationType.SYSTEM_ALERT)
                .build();

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
