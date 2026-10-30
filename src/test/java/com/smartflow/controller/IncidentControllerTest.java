package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.incident.IncidentAssignDTO;
import com.smartflow.dto.incident.IncidentCreateRequestDTO;
import com.smartflow.dto.incident.IncidentHistoryResponseDTO;
import com.smartflow.dto.incident.IncidentResponseDTO;
import com.smartflow.dto.incident.IncidentStatusUpdateDTO;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import com.smartflow.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void createIncident_ShouldReturn201() throws Exception {
        IncidentCreateRequestDTO request = IncidentCreateRequestDTO.builder()
                .title("Panne réseau")
                .description("Impossible de joindre le serveur")
                .location("Étage 1")
                .priority(Priority.HAUTE)
                .categoryId(1L)
                .serviceId(1L)
                .build();

        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(1L)
                .reference("INC-202610-0001")
                .title("Panne réseau")
                .status(IncidentStatus.NOUVEAU)
                .priority(Priority.HAUTE)
                .build();

        when(incidentService.createIncident(any(IncidentCreateRequestDTO.class), eq("emp@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reference").value("INC-202610-0001"))
                .andExpect(jsonPath("$.title").value("Panne réseau"));
    }

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void getIncidentById_ShouldReturn200() throws Exception {
        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(1L)
                .reference("INC-202610-0001")
                .title("Panne réseau")
                .status(IncidentStatus.NOUVEAU)
                .build();

        when(incidentService.getIncidentById(1L, "emp@smartflow.com")).thenReturn(response);

        mockMvc.perform(get("/api/incidents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reference").value("INC-202610-0001"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void searchIncidents_ShouldReturn200() throws Exception {
        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(1L)
                .reference("INC-202610-0001")
                .title("Panne réseau")
                .build();

        when(incidentService.searchIncidents(any(), any(), any(), any(), any(), any(Pageable.class), eq("manager@smartflow.com")))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value("INC-202610-0001"));
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void updateStatus_ShouldReturn200() throws Exception {
        IncidentStatusUpdateDTO request = IncidentStatusUpdateDTO.builder()
                .status(IncidentStatus.EN_COURS)
                .comment("En cours d'intervention")
                .build();

        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(1L)
                .status(IncidentStatus.EN_COURS)
                .build();

        when(incidentService.updateStatus(eq(1L), any(IncidentStatusUpdateDTO.class), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/incidents/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_COURS"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void assignIncident_AsManager_ShouldReturn200() throws Exception {
        IncidentAssignDTO request = IncidentAssignDTO.builder()
                .agentId(2L)
                .comment("Agent assigné")
                .build();

        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(1L)
                .status(IncidentStatus.ASSIGNE)
                .assignedToId(2L)
                .build();

        when(incidentService.assignIncident(eq(1L), any(IncidentAssignDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/incidents/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(2L))
                .andExpect(jsonPath("$.status").value("ASSIGNE"));
    }

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void assignIncident_AsEmployee_ShouldReturn403Forbidden() throws Exception {
        IncidentAssignDTO request = IncidentAssignDTO.builder()
                .agentId(2L)
                .build();

        mockMvc.perform(patch("/api/incidents/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void getIncidentHistory_ShouldReturn200() throws Exception {
        IncidentHistoryResponseDTO history = IncidentHistoryResponseDTO.builder()
                .id(1L)
                .actionType("CREATION")
                .newValue("NOUVEAU")
                .createdAt(LocalDateTime.now())
                .build();

        when(incidentService.getIncidentHistory(1L, "emp@smartflow.com")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/incidents/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("CREATION"));
    }
}
