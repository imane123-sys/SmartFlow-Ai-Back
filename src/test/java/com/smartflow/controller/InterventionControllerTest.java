package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.intervention.InterventionAssignRequestDTO;
import com.smartflow.dto.intervention.InterventionCancelRequestDTO;
import com.smartflow.dto.intervention.InterventionCompleteRequestDTO;
import com.smartflow.dto.intervention.InterventionCreateRequestDTO;
import com.smartflow.dto.intervention.InterventionResponseDTO;
import com.smartflow.dto.intervention.InterventionStepResponseDTO;
import com.smartflow.dto.intervention.InterventionStepUpdateRequestDTO;
import com.smartflow.entity.InterventionStatus;
import com.smartflow.entity.StepStatus;
import com.smartflow.service.InterventionService;
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
import java.util.Collections;
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
class InterventionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InterventionService interventionService;

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void createIntervention_WhenManager_ShouldReturn201() throws Exception {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .assignedAgentId(3L)
                .build();

        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .incidentId(10L)
                .charterId(20L)
                .status(InterventionStatus.AFFECTEE)
                .steps(Collections.singletonList(
                        InterventionStepResponseDTO.builder().id(101L).stepOrder(1).title("Étape 1").status(StepStatus.A_FAIRE).build()
                ))
                .createdAt(LocalDateTime.now())
                .build();

        when(interventionService.createIntervention(any(InterventionCreateRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/interventions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reference").value("INT-202610-0001"))
                .andExpect(jsonPath("$.status").value("AFFECTEE"));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void createIntervention_WhenEmployee_ShouldReturn403() throws Exception {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .build();

        mockMvc.perform(post("/api/interventions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInterventionById_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/interventions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void getInterventionById_WhenAuthenticated_ShouldReturn200() throws Exception {
        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .status(InterventionStatus.AFFECTEE)
                .build();

        when(interventionService.getInterventionById(eq(1L), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(get("/api/interventions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reference").value("INT-202610-0001"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void assignAgent_WhenManager_ShouldReturn200() throws Exception {
        InterventionAssignRequestDTO request = InterventionAssignRequestDTO.builder()
                .agentId(3L)
                .build();

        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .assignedAgentId(3L)
                .status(InterventionStatus.AFFECTEE)
                .build();

        when(interventionService.assignAgent(eq(1L), any(InterventionAssignRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/interventions/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgentId").value(3L))
                .andExpect(jsonPath("$.status").value("AFFECTEE"));
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void assignAgent_WhenAgent_ShouldReturn403() throws Exception {
        InterventionAssignRequestDTO request = InterventionAssignRequestDTO.builder()
                .agentId(3L)
                .build();

        mockMvc.perform(post("/api/interventions/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void startIntervention_WhenAgent_ShouldReturn200() throws Exception {
        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .status(InterventionStatus.EN_COURS)
                .actualStartDate(LocalDateTime.now())
                .build();

        when(interventionService.startIntervention(eq(1L), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/interventions/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_COURS"));
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void updateStep_WhenAgent_ShouldReturn200() throws Exception {
        InterventionStepUpdateRequestDTO request = InterventionStepUpdateRequestDTO.builder()
                .status(StepStatus.EN_COURS)
                .agentNotes("Démarrage de l'analyse réseau")
                .actualDurationMinutes(10)
                .build();

        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .status(InterventionStatus.EN_COURS)
                .build();

        when(interventionService.updateStep(eq(1L), eq(101L), any(InterventionStepUpdateRequestDTO.class), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/interventions/1/steps/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void completeIntervention_WhenAgent_ShouldReturn200() throws Exception {
        InterventionCompleteRequestDTO request = InterventionCompleteRequestDTO.builder()
                .report("Intervention terminée avec succès, panne résolue.")
                .build();

        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .status(InterventionStatus.TERMINEE)
                .report("Intervention terminée avec succès, panne résolue.")
                .build();

        when(interventionService.completeIntervention(eq(1L), any(InterventionCompleteRequestDTO.class), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/interventions/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINEE"))
                .andExpect(jsonPath("$.report").value("Intervention terminée avec succès, panne résolue."));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void cancelIntervention_WhenManager_ShouldReturn200() throws Exception {
        InterventionCancelRequestDTO request = InterventionCancelRequestDTO.builder()
                .cancellationReason("Doublon avec une autre intervention")
                .build();

        InterventionResponseDTO response = InterventionResponseDTO.builder()
                .id(1L)
                .reference("INT-202610-0001")
                .status(InterventionStatus.ANNULEE)
                .cancellationReason("Doublon avec une autre intervention")
                .build();

        when(interventionService.cancelIntervention(eq(1L), any(InterventionCancelRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/interventions/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULEE"))
                .andExpect(jsonPath("$.cancellationReason").value("Doublon avec une autre intervention"));
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void cancelIntervention_WhenEmployee_ShouldReturn403() throws Exception {
        InterventionCancelRequestDTO request = InterventionCancelRequestDTO.builder()
                .cancellationReason("Annulation non permise")
                .build();

        mockMvc.perform(post("/api/interventions/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee@smartflow.com", roles = "EMPLOYEE")
    void getInterventionsByIncident_WhenAuthenticated_ShouldReturn200() throws Exception {
        when(interventionService.getInterventionsByIncidentId(eq(10L), eq("employee@smartflow.com")))
                .thenReturn(List.of(
                        InterventionResponseDTO.builder().id(1L).reference("INT-202610-0001").build()
                ));

        mockMvc.perform(get("/api/incidents/10/interventions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("INT-202610-0001"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void searchInterventions_WhenManager_ShouldReturnPagedResults() throws Exception {
        when(interventionService.searchInterventions(any(), any(), any(), any(), any(), eq("manager@smartflow.com")))
                .thenReturn(new PageImpl<>(List.of(
                        InterventionResponseDTO.builder().id(1L).reference("INT-202610-0001").build()
                )));

        mockMvc.perform(get("/api/interventions?search=INT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value("INT-202610-0001"));
    }
}
