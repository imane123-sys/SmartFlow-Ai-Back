package com.smartflow.service;

import com.smartflow.dto.charter.CharterCreateRequestDTO;
import com.smartflow.dto.charter.CharterRejectRequestDTO;
import com.smartflow.dto.charter.CharterResponseDTO;
import com.smartflow.dto.charter.CharterStepRequestDTO;
import com.smartflow.dto.charter.CharterUpdateRequestDTO;
import com.smartflow.dto.charter.GeminiCharterResult;
import com.smartflow.dto.charter.GeminiCharterStepResult;
import com.smartflow.entity.Charter;
import com.smartflow.entity.CharterStatus;
import com.smartflow.entity.CharterStep;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.AiAnalysisRepository;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharterServiceTest {

    @Mock
    private CharterRepository charterRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiAnalysisRepository aiAnalysisRepository;

    @Mock
    private GeminiService geminiService;

    @InjectMocks
    private CharterService charterService;

    private Service service;
    private User manager;
    private User employee;
    private Incident incident;

    @BeforeEach
    void setUp() {
        service = Service.builder().id(1L).name("Maintenance").build();

        manager = User.builder()
                .id(1L)
                .email("manager@smartflow.com")
                .firstName("Jean")
                .lastName("Manager")
                .role(Role.MANAGER)
                .service(service)
                .build();

        employee = User.builder()
                .id(2L)
                .email("emp@smartflow.com")
                .firstName("Paul")
                .lastName("Employe")
                .role(Role.EMPLOYEE)
                .service(service)
                .build();

        incident = Incident.builder()
                .id(10L)
                .reference("INC-202610-0010")
                .title("Fuite d'eau climatisation serveur")
                .description("Fuite d'eau détectée au dessus de la baie informatique.")
                .status(IncidentStatus.NOUVEAU)
                .priority(Priority.CRITIQUE)
                .service(service)
                .reporter(employee)
                .build();
    }

    @Test
    void generateCharterDraftFromAi_WhenAuthorizedManager_ShouldCreateCharterAndSteps() {
        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));
        when(aiAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(charterRepository.countByIncidentId(10L)).thenReturn(0);

        GeminiCharterResult aiResult = GeminiCharterResult.builder()
                .title("Charte d'intervention : Fuite clim")
                .objective("Arrêter l'écoulement d'eau et protéger les serveurs")
                .safetyPrecautions("Bâcher les équipements et couper l'arrivée d'eau")
                .requiredResources("Bâche étanche, seau, serpillères professionnelles")
                .steps(Arrays.asList(
                        GeminiCharterStepResult.builder().stepOrder(1).title("Sécurisation baie").description("Couvrir la baie").estimatedDurationMinutes(5).build(),
                        GeminiCharterStepResult.builder().stepOrder(2).title("Fermeture vanne").description("Couper la vanne quart de tour").estimatedDurationMinutes(10).build()
                ))
                .build();

        when(geminiService.generateCharterDraft(eq(incident), any())).thenReturn(aiResult);

        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> {
            Charter c = invocation.getArgument(0);
            c.setId(100L);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        CharterResponseDTO response = charterService.generateCharterDraftFromAi(10L, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Charte d'intervention : Fuite clim", response.getTitle());
        assertEquals(CharterStatus.BROUILLON, response.getStatus());
        assertEquals(1, response.getVersionNumber());
        assertEquals(2, response.getSteps().size());

        verify(charterRepository).save(any(Charter.class));
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void createManualCharter_WhenValid_ShouldSaveCharterWithOrderedSteps() {
        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));
        when(charterRepository.countByIncidentId(10L)).thenReturn(1);

        CharterCreateRequestDTO request = CharterCreateRequestDTO.builder()
                .title("Procédure manuelle d'intervention")
                .objective("Nettoyage et remise en route")
                .safetyPrecautions("Port de gants")
                .requiredResources("Kit nettoyage")
                .steps(Arrays.asList(
                        CharterStepRequestDTO.builder().stepOrder(2).title("Étape 2").description("Action 2").build(),
                        CharterStepRequestDTO.builder().stepOrder(1).title("Étape 1").description("Action 1").build()
                ))
                .build();

        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> {
            Charter c = invocation.getArgument(0);
            c.setId(101L);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        CharterResponseDTO response = charterService.createManualCharter(10L, request, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals(2, response.getVersionNumber());
        assertEquals(2, response.getSteps().size());
        assertEquals(1, response.getSteps().get(0).getStepOrder());
    }

    @Test
    void updateCharter_WhenDraft_ShouldUpdateContentAndSteps() {
        Charter existing = Charter.builder()
                .id(50L)
                .title("Ancien titre")
                .objective("Ancien objectif")
                .status(CharterStatus.BROUILLON)
                .versionNumber(1)
                .incident(incident)
                .createdBy(manager)
                .steps(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharterUpdateRequestDTO updateRequest = CharterUpdateRequestDTO.builder()
                .title("Nouveau titre charte")
                .objective("Nouvel objectif clair")
                .steps(Collections.singletonList(
                        CharterStepRequestDTO.builder().stepOrder(1).title("Étape unique").description("Description").build()
                ))
                .build();

        CharterResponseDTO response = charterService.updateCharter(50L, updateRequest, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals("Nouveau titre charte", response.getTitle());
        assertEquals(1, response.getSteps().size());
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void updateCharter_WhenAlreadyValidated_ShouldThrowBusinessException() {
        Charter validated = Charter.builder()
                .id(50L)
                .status(CharterStatus.VALIDEE)
                .incident(incident)
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(validated));

        CharterUpdateRequestDTO updateRequest = CharterUpdateRequestDTO.builder()
                .title("Titre")
                .objective("Objectif")
                .steps(Collections.singletonList(
                        CharterStepRequestDTO.builder().stepOrder(1).title("Étape").description("Desc").build()
                ))
                .build();

        assertThrows(BusinessException.class, () -> charterService.updateCharter(50L, updateRequest, "manager@smartflow.com"));
    }

    @Test
    void submitCharter_WhenDraft_ShouldTransitionToPendingValidation() {
        Charter draft = Charter.builder()
                .id(50L)
                .status(CharterStatus.BROUILLON)
                .versionNumber(1)
                .incident(incident)
                .steps(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(draft));
        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharterResponseDTO response = charterService.submitCharter(50L, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(CharterStatus.EN_ATTENTE_VALIDATION, response.getStatus());
        assertEquals(IncidentStatus.EN_ATTENTE_VALIDATION, incident.getStatus());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void validateCharter_ShouldSetStatusValidatedAndArchivePrevious() {
        Charter oldCharter = Charter.builder()
                .id(40L)
                .versionNumber(1)
                .status(CharterStatus.VALIDEE)
                .incident(incident)
                .steps(new ArrayList<>())
                .build();

        Charter newCharter = Charter.builder()
                .id(50L)
                .versionNumber(2)
                .status(CharterStatus.EN_ATTENTE_VALIDATION)
                .incident(incident)
                .steps(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(newCharter));
        when(charterRepository.findByIncidentIdOrderByVersionNumberDesc(10L)).thenReturn(Arrays.asList(newCharter, oldCharter));
        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharterResponseDTO response = charterService.validateCharter(50L, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(CharterStatus.VALIDEE, response.getStatus());
        assertEquals(CharterStatus.ARCHIVEE, oldCharter.getStatus());
        assertNotNull(response.getValidatedAt());
        verify(charterRepository).save(oldCharter);
        verify(charterRepository).save(newCharter);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void rejectCharter_WhenValidReason_ShouldSetStatusRefusee() {
        Charter charter = Charter.builder()
                .id(50L)
                .status(CharterStatus.EN_ATTENTE_VALIDATION)
                .versionNumber(1)
                .incident(incident)
                .steps(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(charter));
        when(charterRepository.save(any(Charter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharterRejectRequestDTO rejectRequest = new CharterRejectRequestDTO("Procédure insuffisante, manque la coupure générale");
        CharterResponseDTO response = charterService.rejectCharter(50L, rejectRequest, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(CharterStatus.REFUSEE, response.getStatus());
        assertEquals("Procédure insuffisante, manque la coupure générale", response.getRejectionReason());
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void rejectCharter_WhenBlankReason_ShouldThrowBusinessException() {
        Charter charter = Charter.builder()
                .id(50L)
                .status(CharterStatus.EN_ATTENTE_VALIDATION)
                .incident(incident)
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(charter));

        CharterRejectRequestDTO emptyReason = new CharterRejectRequestDTO("   ");
        assertThrows(BusinessException.class, () -> charterService.rejectCharter(50L, emptyReason, "manager@smartflow.com"));
    }

    @Test
    void generateCharterDraftFromAi_WhenEmployeeAttempts_ShouldThrowAccessDeniedException() {
        when(userRepository.findByEmail("emp@smartflow.com")).thenReturn(Optional.of(employee));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        assertThrows(AccessDeniedException.class, () -> charterService.generateCharterDraftFromAi(10L, "emp@smartflow.com"));
    }

    @Test
    void getCharterById_WhenUnauthorized_ShouldThrowAccessDeniedException() {
        User otherEmployee = User.builder().id(99L).email("other@smartflow.com").role(Role.EMPLOYEE).build();
        Charter charter = Charter.builder().id(50L).incident(incident).steps(new ArrayList<>()).build();

        when(userRepository.findByEmail("other@smartflow.com")).thenReturn(Optional.of(otherEmployee));
        when(charterRepository.findById(50L)).thenReturn(Optional.of(charter));

        assertThrows(AccessDeniedException.class, () -> charterService.getCharterById(50L, "other@smartflow.com"));
    }
}
