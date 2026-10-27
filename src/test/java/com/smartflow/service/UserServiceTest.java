package com.smartflow.service;

import com.smartflow.dto.user.UserRequestDTO;
import com.smartflow.dto.user.UserResponseDTO;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.ServiceRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void getAllUsersShouldFilterCorrectly() {
        Service itService = Service.builder().id(10L).name("IT").build();
        User u1 = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).active(true).build();
        User u2 = User.builder().id(2L).email("agent@test.com").role(Role.AGENT).service(itService).active(true).build();
        User u3 = User.builder().id(3L).email("emp@test.com").role(Role.EMPLOYEE).active(false).build();

        when(userRepository.findAll()).thenReturn(List.of(u1, u2, u3));

        List<UserResponseDTO> all = userService.getAllUsers(null, null, null);
        assertEquals(3, all.size());

        List<UserResponseDTO> agents = userService.getAllUsers(Role.AGENT, null, null);
        assertEquals(1, agents.size());
        assertEquals("agent@test.com", agents.get(0).getEmail());

        List<UserResponseDTO> serviceFiltered = userService.getAllUsers(null, 10L, null);
        assertEquals(1, serviceFiltered.size());

        List<UserResponseDTO> activeOnly = userService.getAllUsers(null, null, true);
        assertEquals(2, activeOnly.size());
    }

    @Test
    void getUserByIdShouldReturnUserWhenFound() {
        User user = User.builder().id(1L).email("user@test.com").role(Role.EMPLOYEE).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponseDTO response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("user@test.com", response.getEmail());
    }

    @Test
    void getUserByIdShouldThrowWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void createUserWithValidDataShouldSucceed() {
        UserRequestDTO request = UserRequestDTO.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@test.com")
                .password("Password123")
                .role(Role.MANAGER)
                .build();

        User savedUser = User.builder()
                .id(5L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@test.com")
                .password("encoded")
                .role(Role.MANAGER)
                .active(true)
                .build();

        when(userRepository.existsByEmail("jane@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponseDTO response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("jane@test.com", response.getEmail());
        assertEquals(Role.MANAGER, response.getRole());
    }

    @Test
    void createUserWithDuplicateEmailShouldThrowBusinessException() {
        UserRequestDTO request = UserRequestDTO.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("existing@test.com")
                .password("Password123")
                .role(Role.MANAGER)
                .build();

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.createUser(request));
    }

    @Test
    void createUserWithoutPasswordShouldThrowBusinessException() {
        UserRequestDTO request = UserRequestDTO.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@test.com")
                .password("")
                .role(Role.MANAGER)
                .build();

        when(userRepository.existsByEmail("jane@test.com")).thenReturn(false);

        assertThrows(BusinessException.class, () -> userService.createUser(request));
    }

    @Test
    void updateUserShouldModifyFields() {
        User user = User.builder()
                .id(1L)
                .firstName("Old")
                .lastName("Name")
                .email("old@test.com")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        UserRequestDTO updateReq = UserRequestDTO.builder()
                .firstName("New")
                .lastName("Name")
                .email("old@test.com")
                .role(Role.AGENT)
                .active(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.updateUser(1L, updateReq);

        assertEquals("New", response.getFirstName());
        assertEquals(Role.AGENT, response.getRole());
        assertFalse(response.getActive());
    }

    @Test
    void toggleUserStatusShouldInvertStatus() {
        User user = User.builder().id(1L).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.toggleUserStatus(1L);

        assertFalse(response.getActive());
    }

    @Test
    void deleteUserShouldCallRepositoryDelete() {
        User user = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).delete(user);
    }
}
