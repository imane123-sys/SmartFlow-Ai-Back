package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.service.ServiceRequestDTO;
import com.smartflow.dto.service.ServiceResponseDTO;
import com.smartflow.service.ServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceService serviceService;

    @Test
    void getAllServicesShouldReturn200AndList() throws Exception {
        ServiceResponseDTO response = new ServiceResponseDTO(1L, "IT Support", "Desc", true, LocalDateTime.now());
        when(serviceService.getAllServices(null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("IT Support"));
    }

    @Test
    void createServiceWithValidDataShouldReturn201() throws Exception {
        ServiceRequestDTO request = new ServiceRequestDTO("IT Support", "Desc", true);
        ServiceResponseDTO response = new ServiceResponseDTO(1L, "IT Support", "Desc", true, LocalDateTime.now());
        when(serviceService.createService(any(ServiceRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("IT Support"));
    }

    @Test
    void createServiceWithBlankNameShouldReturn400() throws Exception {
        ServiceRequestDTO request = new ServiceRequestDTO("", "Desc", true);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details").isArray());
    }
}
