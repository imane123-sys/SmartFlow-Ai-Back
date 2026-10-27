package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.auth.AuthResponseDTO;
import com.smartflow.dto.auth.LoginRequestDTO;
import com.smartflow.dto.auth.RegisterRequestDTO;
import com.smartflow.dto.user.UserResponseDTO;
import com.smartflow.entity.Role;
import com.smartflow.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void loginWithValidCredentialsShouldReturn200AndToken() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("user@smartflow.com", "Password123!");
        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("jwt-sample-token")
                .type("Bearer")
                .id(1L)
                .email("user@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .build();

        when(authService.login(any(LoginRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-sample-token"))
                .andExpect(jsonPath("$.email").value("user@smartflow.com"));
    }

    @Test
    void loginWithInvalidEmailShouldReturn400() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("invalid-email", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void registerWithValidDataShouldReturn201() throws Exception {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@smartflow.com")
                .password("Password123!")
                .role(Role.EMPLOYEE)
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("jwt-sample-token")
                .id(1L)
                .email("john.doe@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .build();

        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-sample-token"))
                .andExpect(jsonPath("$.email").value("john.doe@smartflow.com"));
    }

    @Test
    void registerWithShortPasswordShouldReturn400() throws Exception {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@smartflow.com")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @WithMockUser(username = "john.doe@smartflow.com", roles = "EMPLOYEE")
    void getCurrentUserShouldReturn200() throws Exception {
        UserResponseDTO userResponse = UserResponseDTO.builder()
                .id(1L)
                .email("john.doe@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        when(authService.getCurrentUser()).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@smartflow.com"))
                .andExpect(jsonPath("$.firstName").value("John"));
    }
}
