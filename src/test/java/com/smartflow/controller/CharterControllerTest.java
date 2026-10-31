package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.charter.CharterCreateRequestDTO;
import com.smartflow.dto.charter.CharterRejectRequestDTO;
import com.smartflow.dto.charter.CharterResponseDTO;
import com.smartflow.dto.charter.CharterStepRequestDTO;
import com.smartflow.dto.charter.CharterStepResponseDTO;
import com.smartflow.dto.charter.CharterUpdateRequestDTO;
import com.smartflow.entity.CharterStatus;
import com.smartflow.service.CharterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CharterService charterService;

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void generateCharterDraft_WhenManager_ShouldReturn201() throws Exception {
        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .incidentId(10L)
                .incidentReference("INC-202610-0010")
                .title("Charte d'intervention IA")
                .status(CharterStatus.BROUILLON)
                .versionNumber(1)
                .steps(Collections.singletonList(
                        CharterStepResponseDTO.builder().id(1L).stepOrder(1).title("Étape 1").build()
                ))
                .createdAt(LocalDateTime.now())
                .build();

        when(charterService.generateCharterDraftFromAi(eq(10L), eq("manager@smartflow.com"))).thenReturn(response);

        mockMvc.perform(post("/api/incidents/10/charter-draft")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("BROUILLON"))
                .andExpect(jsonPath("$.title").value("Charte d'intervention IA"));
    }

    @Test
    @WithMockUser(username = "emp@smartflow.com", roles = "EMPLOYEE")
    void generateCharterDraft_WhenEmployee_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/incidents/10/charter-draft")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void createManualCharter_WhenValid_ShouldReturn201() throws Exception {
        CharterCreateRequestDTO request = CharterCreateRequestDTO.builder()
                .title("Procédure manuelle de réparation")
                .objective("Réparer le câble sectionné")
                .steps(Collections.singletonList(
                        CharterStepRequestDTO.builder().stepOrder(1).title("Soudure").description("Souder les brins").build()
                ))
                .build();

        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(2L)
                .incidentId(10L)
                .title("Procédure manuelle de réparation")
                .status(CharterStatus.BROUILLON)
                .versionNumber(1)
                .build();

        when(charterService.createManualCharter(eq(10L), any(CharterCreateRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/10/charters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.title").value("Procédure manuelle de réparation"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void createManualCharter_WhenEmptySteps_ShouldReturn400() throws Exception {
        CharterCreateRequestDTO invalidRequest = CharterCreateRequestDTO.builder()
                .title("Procédure sans étapes")
                .objective("Test")
                .steps(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/incidents/10/charters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "agent@smartflow.com", roles = "AGENT")
    void getCharterById_WhenAuthenticated_ShouldReturn200() throws Exception {
        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .incidentId(10L)
                .title("Charte validée")
                .status(CharterStatus.VALIDEE)
                .build();

        when(charterService.getCharterById(eq(1L), eq("agent@smartflow.com"))).thenReturn(response);

        mockMvc.perform(get("/api/charters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("VALIDEE"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void updateCharter_WhenManager_ShouldReturn200() throws Exception {
        CharterUpdateRequestDTO updateRequest = CharterUpdateRequestDTO.builder()
                .title("Titre modifié")
                .objective("Objectif modifié")
                .steps(Collections.singletonList(
                        CharterStepRequestDTO.builder().stepOrder(1).title("Étape 1").description("Description").build()
                ))
                .build();

        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .title("Titre modifié")
                .status(CharterStatus.BROUILLON)
                .build();

        when(charterService.updateCharter(eq(1L), any(CharterUpdateRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(put("/api/charters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre modifié"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void submitCharter_WhenManager_ShouldReturn200() throws Exception {
        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .status(CharterStatus.EN_ATTENTE_VALIDATION)
                .build();

        when(charterService.submitCharter(eq(1L), eq("manager@smartflow.com"))).thenReturn(response);

        mockMvc.perform(post("/api/charters/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE_VALIDATION"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void validateCharter_WhenManager_ShouldReturn200() throws Exception {
        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .status(CharterStatus.VALIDEE)
                .validatedByName("Jean Manager")
                .build();

        when(charterService.validateCharter(eq(1L), eq("manager@smartflow.com"))).thenReturn(response);

        mockMvc.perform(post("/api/charters/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"));
    }

    @Test
    @WithMockUser(username = "manager@smartflow.com", roles = "MANAGER")
    void rejectCharter_WhenManager_ShouldReturn200() throws Exception {
        CharterRejectRequestDTO rejectRequest = new CharterRejectRequestDTO("Procédure incomplète");
        CharterResponseDTO response = CharterResponseDTO.builder()
                .id(1L)
                .status(CharterStatus.REFUSEE)
                .rejectionReason("Procédure incomplète")
                .build();

        when(charterService.rejectCharter(eq(1L), any(CharterRejectRequestDTO.class), eq("manager@smartflow.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/charters/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSEE"))
                .andExpect(jsonPath("$.rejectionReason").value("Procédure incomplète"));
    }
}
