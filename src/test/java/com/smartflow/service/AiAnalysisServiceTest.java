package com.smartflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.ai.AiAnalysisResponseDTO;
import com.smartflow.dto.ai.GeminiAnalysisResult;
import com.smartflow.entity.AiAnalysis;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import org.springframework.security.access.AccessDeniedException;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.AiAnalysisRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private AiAnalysisRepository aiAnalysisRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeminiService geminiService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiAnalysisService aiAnalysisService;

    private Service service;
    private User manager;
    private User employee;
    private Incident incident;

    @BeforeEach
    void setUp() {
        service = Service.builder().id(1L).name("Informatique").build();

        manager = User.builder()
                .id(1L)
                .email("manager@smartflow.com")
                .firstName("Alice")
                .lastName("Manager")
                .role(Role.MANAGER)
                .service(service)
                .build();

        employee = User.builder()
                .id(2L)
                .email("emp@smartflow.com")
                .firstName("Bob")
                .lastName("Employee")
                .role(Role.EMPLOYEE)
                .service(service)
                .build();

        incident = Incident.builder()
                .id(10L)
                .reference("INC-202610-0010")
                .title("Coupure fibre optique")
                .description("Plus aucune connexion internet dans tout l'immeuble.")
                .status(IncidentStatus.NOUVEAU)
                .priority(Priority.HAUTE)
                .service(service)
                .reporter(employee)
                .build();
    }

    @Test
    void analyzeIncident_WhenManagerAuthorized_ShouldSaveAnalysisAndUpdateIncidentAndHistory() {
        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        GeminiAnalysisResult mockResult = GeminiAnalysisResult.builder()
                .summary("Rupture de liaison réseau WAN")
                .possibleCauses(Arrays.asList("Câble sectionné", "Panne routeur opérateur"))
                .suggestedPriority(Priority.CRITIQUE)
                .priorityReason("Impacte la totalité de la production")
                .recommendations(Arrays.asList("Contacter le support opérateur", "Basculer sur le lien 4G"))
                .missingInfo(Collections.singletonList("Numéro de contrat opérateur"))
                .rawResponse("{}")
                .build();

        when(geminiService.analyzeIncident(incident)).thenReturn(mockResult);

        when(aiAnalysisRepository.save(any(AiAnalysis.class))).thenAnswer(invocation -> {
            AiAnalysis a = invocation.getArgument(0);
            a.setId(100L);
            a.setCreatedAt(LocalDateTime.now());
            return a;
        });

        AiAnalysisResponseDTO response = aiAnalysisService.analyzeIncident(10L, "manager@smartflow.com");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("INC-202610-0010", response.getIncidentReference());
        assertEquals(Priority.CRITIQUE, response.getSuggestedPriority());
        assertEquals(2, response.getPossibleCauses().size());
        assertEquals(IncidentStatus.EN_ANALYSE, incident.getStatus());

        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
        verify(aiAnalysisRepository).save(any(AiAnalysis.class));
    }

    @Test
    void analyzeIncident_WhenEmployeeAttempts_ShouldThrowAccessDeniedException() {
        when(userRepository.findByEmail("emp@smartflow.com")).thenReturn(Optional.of(employee));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        assertThrows(AccessDeniedException.class, () -> aiAnalysisService.analyzeIncident(10L, "emp@smartflow.com"));
    }

    @Test
    void analyzeIncident_WhenManagerDifferentService_ShouldThrowAccessDeniedException() {
        Service otherService = Service.builder().id(99L).name("Logistique").build();
        User otherManager = User.builder()
                .id(3L)
                .email("other@smartflow.com")
                .role(Role.MANAGER)
                .service(otherService)
                .build();

        when(userRepository.findByEmail("other@smartflow.com")).thenReturn(Optional.of(otherManager));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        assertThrows(AccessDeniedException.class, () -> aiAnalysisService.analyzeIncident(10L, "other@smartflow.com"));
    }

    @Test
    void analyzeIncident_WhenIncidentClosed_ShouldThrowBusinessException() {
        incident.setStatus(IncidentStatus.CLOTURE);
        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        assertThrows(BusinessException.class, () -> aiAnalysisService.analyzeIncident(10L, "manager@smartflow.com"));
    }

    @Test
    void getAnalysesByIncident_WhenAuthorizedReporter_ShouldReturnList() {
        when(userRepository.findByEmail("emp@smartflow.com")).thenReturn(Optional.of(employee));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        AiAnalysis analysis = AiAnalysis.builder()
                .id(1L)
                .incident(incident)
                .requestedBy(manager)
                .summary("Analyse effectuée")
                .possibleCausesJson("[\"Cause 1\"]")
                .suggestedPriority(Priority.HAUTE)
                .priorityReason("Raison test")
                .recommendationsJson("[\"Rec 1\"]")
                .missingInfoJson("[]")
                .createdAt(LocalDateTime.now())
                .build();

        when(aiAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(10L))
                .thenReturn(Collections.singletonList(analysis));

        List<AiAnalysisResponseDTO> list = aiAnalysisService.getAnalysesByIncident(10L, "emp@smartflow.com");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getId());
        assertEquals("Cause 1", list.get(0).getPossibleCauses().get(0));
    }

    @Test
    void getAnalysesByIncident_WhenUnauthorizedUser_ShouldThrowAccessDeniedException() {
        User outsider = User.builder()
                .id(99L)
                .email("outsider@smartflow.com")
                .role(Role.EMPLOYEE)
                .build();

        when(userRepository.findByEmail("outsider@smartflow.com")).thenReturn(Optional.of(outsider));
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        assertThrows(AccessDeniedException.class, () -> aiAnalysisService.getAnalysesByIncident(10L, "outsider@smartflow.com"));
    }

    @Test
    void getAnalysisById_WhenValid_ShouldReturnDTO() {
        AiAnalysis analysis = AiAnalysis.builder()
                .id(50L)
                .incident(incident)
                .requestedBy(manager)
                .summary("Synthèse")
                .suggestedPriority(Priority.MOYENNE)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("manager@smartflow.com")).thenReturn(Optional.of(manager));
        when(aiAnalysisRepository.findById(50L)).thenReturn(Optional.of(analysis));

        AiAnalysisResponseDTO dto = aiAnalysisService.getAnalysisById(50L, "manager@smartflow.com");

        assertNotNull(dto);
        assertEquals(50L, dto.getId());
        assertEquals("Synthèse", dto.getSummary());
    }
}
