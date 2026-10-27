package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.user.UserRequestDTO;
import com.smartflow.dto.user.UserResponseDTO;
import com.smartflow.entity.Role;
import com.smartflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsersAsAdminShouldReturn200() throws Exception {
        UserResponseDTO user = UserResponseDTO.builder()
                .id(1L)
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();

        when(userService.getAllUsers(null, null, null)).thenReturn(List.of(user));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@test.com"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllUsersAsEmployeeShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserByIdAsAdminShouldReturn200() throws Exception {
        UserResponseDTO user = UserResponseDTO.builder()
                .id(2L)
                .email("agent@test.com")
                .role(Role.AGENT)
                .build();

        when(userService.getUserById(2L)).thenReturn(user);

        mockMvc.perform(get("/api/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("agent@test.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUserAsAdminShouldReturn201() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .firstName("Test")
                .lastName("User")
                .email("create@test.com")
                .password("Password123")
                .role(Role.AGENT)
                .build();

        UserResponseDTO response = UserResponseDTO.builder()
                .id(3L)
                .firstName("Test")
                .lastName("User")
                .email("create@test.com")
                .role(Role.AGENT)
                .build();

        when(userService.createUser(any(UserRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.email").value("create@test.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUserAsAdminShouldReturn200() throws Exception {
        UserRequestDTO request = UserRequestDTO.builder()
                .firstName("Updated")
                .lastName("User")
                .email("update@test.com")
                .role(Role.AGENT)
                .build();

        UserResponseDTO response = UserResponseDTO.builder()
                .id(3L)
                .firstName("Updated")
                .lastName("User")
                .email("update@test.com")
                .role(Role.AGENT)
                .build();

        when(userService.updateUser(eq(3L), any(UserRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleUserStatusAsAdminShouldReturn200() throws Exception {
        UserResponseDTO response = UserResponseDTO.builder()
                .id(3L)
                .active(false)
                .build();

        when(userService.toggleUserStatus(3L)).thenReturn(response);

        mockMvc.perform(patch("/api/users/3/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUserAsAdminShouldReturn204() throws Exception {
        doNothing().when(userService).deleteUser(3L);

        mockMvc.perform(delete("/api/users/3"))
                .andExpect(status().isNoContent());
    }
}
