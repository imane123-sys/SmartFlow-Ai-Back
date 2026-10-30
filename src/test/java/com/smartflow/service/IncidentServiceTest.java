package com.smartflow.service;

import com.smartflow.dto.incident.IncidentAssignDTO;
import com.smartflow.dto.incident.IncidentCreateRequestDTO;
import com.smartflow.dto.incident.IncidentHistoryResponseDTO;
import com.smartflow.dto.incident.IncidentResponseDTO;
import com.smartflow.dto.incident.IncidentStatusUpdateDTO;
import com.smartflow.entity.Category;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Priority;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.CategoryRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.ServiceRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private IncidentService incidentService;

    private User employee;
    private User manager;
    private User agent;
    private Category category;
    private Service service;
    private Incident incident;

    @BeforeEach
    void setUp() {
        service = Service.builder()
                .id(1L)
                .name("Informatique")
                .active(true)
                .build();

        category = Category.builder()
                .id(1L)
                .name("Matériel")
                .active(true)
                .build();

        employee = User.builder()
                .id(10L)
                .firstName("Jean")
                .lastName("Dupont")
                .email("jean.dupont@smartflow.com")
                .role(Role.EMPLOYEE)
                .service(service)
                .active(true)
                .build();

        manager = User.builder()
                .id(20L)
                .firstName("Marie")
                .lastName("Curie")
                .email("marie.curie@smartflow.com")
                .role(Role.MANAGER)
                .service(service)
                .active(true)
                .build();

        agent = User.builder()
                .id(30L)
                .firstName("Paul")
                .lastName("Tech")
                .email("paul.tech@smartflow.com")
                .role(Role.AGENT)
                .service(service)
                .active(true)
                .build();

        incident = Incident.builder()
                .id(100L)
                .reference("INC-202610-0001")
                .title("Écran noir au démarrage")
                .description("Mon poste ne s'allume plus")
                .location("Bureau 204")
                .priority(Priority.HAUTE)
                .status(IncidentStatus.NOUVEAU)
                .category(category)
                .service(service)
                .reporter(employee)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createIncident_Success() {
        IncidentCreateRequestDTO request = IncidentCreateRequestDTO.builder()
                .title("Écran noir au démarrage")
                .description("Mon poste ne s'allume plus")
                .location("Bureau 204")
                .priority(Priority.HAUTE)
                .categoryId(1L)
                .serviceId(1L)
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(incidentRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(incidentRepository.existsByReference(any())).thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            saved.setId(101L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        IncidentResponseDTO response = incidentService.createIncident(request, employee.getEmail());

        assertNotNull(response);
        assertEquals("Écran noir au démarrage", response.getTitle());
        assertEquals(IncidentStatus.NOUVEAU, response.getStatus());
        assertEquals(Priority.HAUTE, response.getPriority());
        assertEquals(1L, response.getCategoryId());
        assertEquals(1L, response.getServiceId());
        assertEquals(10L, response.getReporterId());

        verify(incidentRepository).save(any(Incident.class));
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void createIncident_InactiveCategory_ThrowsException() {
        category.setActive(false);
        IncidentCreateRequestDTO request = IncidentCreateRequestDTO.builder()
                .title("Panne réseau")
                .description("Description")
                .categoryId(1L)
                .serviceId(1L)
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                incidentService.createIncident(request, employee.getEmail())
        );

        assertEquals("Impossible de créer un incident avec une catégorie inactive", exception.getMessage());
    }

    @Test
    void getIncidentById_AllowedForReporter() {
        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));

        IncidentResponseDTO response = incidentService.getIncidentById(100L, employee.getEmail());

        assertNotNull(response);
        assertEquals("INC-202610-0001", response.getReference());
    }

    @Test
    void getIncidentById_ForbiddenForOtherEmployee() {
        User otherEmployee = User.builder()
                .id(99L)
                .email("autre@smartflow.com")
                .role(Role.EMPLOYEE)
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(otherEmployee.getEmail())).thenReturn(Optional.of(otherEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                incidentService.getIncidentById(100L, otherEmployee.getEmail())
        );

        assertEquals("Vous n'êtes pas autorisé à consulter cet incident", ex.getMessage());
    }

    @Test
    void updateStatus_Success_Manager() {
        IncidentStatusUpdateDTO updateDTO = IncidentStatusUpdateDTO.builder()
                .status(IncidentStatus.EN_COURS)
                .comment("Prise en charge validée")
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentResponseDTO response = incidentService.updateStatus(100L, updateDTO, manager.getEmail());

        assertNotNull(response);
        assertEquals(IncidentStatus.EN_COURS, response.getStatus());
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void updateStatus_Forbidden_Employee() {
        IncidentStatusUpdateDTO updateDTO = IncidentStatusUpdateDTO.builder()
                .status(IncidentStatus.RESOLU)
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                incidentService.updateStatus(100L, updateDTO, employee.getEmail())
        );

        assertEquals("Vous n'avez pas les autorisations nécessaires pour modifier le statut de cet incident", ex.getMessage());
    }

    @Test
    void assignIncident_Success_ByManager() {
        IncidentAssignDTO assignDTO = IncidentAssignDTO.builder()
                .agentId(agent.getId())
                .comment("Assignation pour diagnostic sur place")
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentResponseDTO response = incidentService.assignIncident(100L, assignDTO, manager.getEmail());

        assertNotNull(response);
        assertEquals(IncidentStatus.ASSIGNE, response.getStatus());
        assertEquals(agent.getId(), response.getAssignedToId());
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void assignIncident_Forbidden_ByEmployee() {
        IncidentAssignDTO assignDTO = IncidentAssignDTO.builder()
                .agentId(agent.getId())
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                incidentService.assignIncident(100L, assignDTO, employee.getEmail())
        );

        assertEquals("Seuls les managers ou administrateurs peuvent assigner un incident", ex.getMessage());
    }

    @Test
    void getIncidentHistory_Success() {
        IncidentHistory h1 = IncidentHistory.builder()
                .id(1L)
                .actionType("CREATION")
                .newValue("NOUVEAU")
                .comment("Création")
                .createdAt(LocalDateTime.now())
                .operator(employee)
                .incident(incident)
                .build();

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(historyRepository.findByIncidentIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(h1));

        List<IncidentHistoryResponseDTO> histories = incidentService.getIncidentHistory(100L, employee.getEmail());

        assertNotNull(histories);
        assertEquals(1, histories.size());
        assertEquals("CREATION", histories.get(0).getActionType());
    }
}
