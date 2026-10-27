package com.smartflow.service;

import com.smartflow.dto.auth.AuthResponseDTO;
import com.smartflow.dto.auth.LoginRequestDTO;
import com.smartflow.dto.auth.RegisterRequestDTO;
import com.smartflow.dto.user.UserResponseDTO;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.ServiceRepository;
import com.smartflow.repository.UserRepository;
import com.smartflow.security.JwtUtils;
import com.smartflow.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginWithValidCredentialsShouldReturnTokenAndUser() {
        LoginRequestDTO request = new LoginRequestDTO("test@smartflow.com", "Password123!");
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .active(true)
                .authorities(Collections.emptyList())
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtUtils.generateToken(authentication)).thenReturn("mocked-jwt-token");

        AuthResponseDTO response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.getToken());
        assertEquals("test@smartflow.com", response.getEmail());
        assertEquals("John", response.getFirstName());
    }

    @Test
    void loginWithInvalidCredentialsShouldThrowBadCredentialsException() {
        LoginRequestDTO request = new LoginRequestDTO("test@smartflow.com", "WrongPassword");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void registerWithValidDataShouldReturnAuthResponse() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@smartflow.com")
                .password("Secret123!")
                .role(Role.AGENT)
                .build();

        User savedUser = User.builder()
                .id(2L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@smartflow.com")
                .password("encoded-password")
                .role(Role.AGENT)
                .active(true)
                .build();

        when(userRepository.existsByEmail("alice@smartflow.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtils.generateTokenFromUserPrincipal(any(UserPrincipal.class))).thenReturn("new-user-token");

        AuthResponseDTO response = authService.register(request);

        assertNotNull(response);
        assertEquals("new-user-token", response.getToken());
        assertEquals(2L, response.getId());
        assertEquals("alice@smartflow.com", response.getEmail());
        assertEquals(Role.AGENT, response.getRole());
    }

    @Test
    void registerWithExistingEmailShouldThrowBusinessException() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("existing@smartflow.com")
                .password("Secret123!")
                .build();

        when(userRepository.existsByEmail("existing@smartflow.com")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.register(request));
    }

    @Test
    void registerWithInvalidServiceIdShouldThrowResourceNotFoundException() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .firstName("Bob")
                .lastName("Builder")
                .email("bob@smartflow.com")
                .password("Secret123!")
                .serviceId(999L)
                .build();

        when(userRepository.existsByEmail("bob@smartflow.com")).thenReturn(false);
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.register(request));
    }

    @Test
    void getCurrentUserShouldReturnAuthenticatedUser() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("test@smartflow.com");
        when(authentication.getName()).thenReturn("test@smartflow.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        User user = User.builder()
                .id(1L)
                .email("test@smartflow.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        when(userRepository.findByEmail("test@smartflow.com")).thenReturn(Optional.of(user));

        UserResponseDTO response = authService.getCurrentUser();

        assertNotNull(response);
        assertEquals("test@smartflow.com", response.getEmail());
        assertEquals("John", response.getFirstName());
    }
}
