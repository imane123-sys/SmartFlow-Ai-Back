package com.smartflow.config;

import com.smartflow.entity.Role;
import com.smartflow.entity.User;
import com.smartflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@smartflow.com")) {
            User admin = User.builder()
                    .firstName("Admin")
                    .lastName("SmartFlow")
                    .email("admin@smartflow.com")
                    .password(passwordEncoder.encode("Admin123!"))
                    .role(Role.ADMIN)
                    .active(true)
                    .build();

            userRepository.save(admin);
            log.info("Compte administrateur par défaut créé : admin@smartflow.com / Admin123!");
        }
    }
}
