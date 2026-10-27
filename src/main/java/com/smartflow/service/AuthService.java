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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().trim().toLowerCase(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String jwt = jwtUtils.generateToken(authentication);

        return AuthResponseDTO.builder()
                .token(jwt)
                .type("Bearer")
                .id(userPrincipal.getId())
                .firstName(userPrincipal.getFirstName())
                .lastName(userPrincipal.getLastName())
                .email(userPrincipal.getEmail())
                .role(userPrincipal.getRole())
                .serviceId(userPrincipal.getServiceId())
                .serviceName(userPrincipal.getServiceName())
                .build();
    }

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Un compte avec cette adresse email existe déjà");
        }

        Service service = null;
        if (request.getServiceId() != null) {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'id : " + request.getServiceId()));
        }

        Role role = request.getRole() != null ? request.getRole() : Role.EMPLOYEE;

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .service(service)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        UserPrincipal userPrincipal = UserPrincipal.create(savedUser);
        String jwt = jwtUtils.generateTokenFromUserPrincipal(userPrincipal);

        return AuthResponseDTO.builder()
                .token(jwt)
                .type("Bearer")
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .serviceId(savedUser.getService() != null ? savedUser.getService().getId() : null)
                .serviceName(savedUser.getService() != null ? savedUser.getService().getName() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("Aucun utilisateur authentifié");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));

        return mapToResponseDTO(user);
    }

    public UserResponseDTO mapToResponseDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .serviceId(user.getService() != null ? user.getService().getId() : null)
                .serviceName(user.getService() != null ? user.getService().getName() : null)
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
