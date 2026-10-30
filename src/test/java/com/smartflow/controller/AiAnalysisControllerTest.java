package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.ai.AiAnalysisResponseDTO;
import com.smartflow.entity.Priority;
import com.smartflow.service.AiAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiAnalysisService aiAnalysisService;

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void analyzeIncident_WhenManager_ShouldReturn200() throws Exception {
        AiAnalysisResponseDTO response = AiAnalysisResponseDTO.builder()
                .id(1L)
                .incidentId(10L)
                .incidentReference("INC-202610-0010")
                .summary("Problème réseau identifié")
                .possibleCauses(Arrays.asList("Câble sectionné", "Panne switch"))
                .suggestedPriority(Priority.CRITIQUE)
                .priorityReason("Impact fort")
                .recommendations(Collections.singletonList("Vérifier les voyants"))
                .missingInfo(Collections.emptyList())
                .createdAt(LocalDateTime.now())
                .build();

        when(aiAnalysisService.analyzeIncident(eq(10L), eq("manager@smartflow.com"))).thenReturn(response);

        mockMvc.perform(post("/api/incidents/10/analyze")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.incidentReference").value("INC-202610-0010"))
                .andExpect(jsonPath("$.suggestedPriority").value("CRITIQUE"))
                .andExpect(jsonPath("$.possibleCauses[0]").value("Câble sectionné"));
    }

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void analyzeIncident_WhenEmployee_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/incidents/10/analyze")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@smartflow.com", roles = "EMPLOYEE")
    void getAnalysesByIncident_WhenAuthenticated_ShouldReturn200() throws Exception {
        AiAnalysisResponseDTO response = AiAnalysisResponseDTO.builder()
                .id(1L)
                .incidentId(10L)
                .incidentReference("INC-202610-0010")
                .summary("Problème analysé")
                .suggestedPriority(Priority.HAUTE)
                .build();

        when(aiAnalysisService.getAnalysesByIncident(eq(10L), eq("user@smartflow.com")))
                .thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/api/incidents/10/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].incidentReference").value("INC-202610-0010"));
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void getAnalysisById_WhenAuthenticated_ShouldReturn200() throws Exception {
        AiAnalysisResponseDTO response = AiAnalysisResponseDTO.builder()
                .id(5L)
                .incidentId(10L)
                .summary("Détail analyse")
                .suggestedPriority(Priority.MOYENNE)
                .build();

        when(aiAnalysisService.getAnalysisById(eq(5L), eq("agent@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(get("/api/analyses/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.summary").value("Détail analyse"));
    }
}
