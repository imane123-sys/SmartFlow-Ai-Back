package com.smartflow.service;

import com.smartflow.dto.intervention.InterventionAssignRequestDTO;
import com.smartflow.dto.intervention.InterventionCancelRequestDTO;
import com.smartflow.dto.intervention.InterventionCompleteRequestDTO;
import com.smartflow.dto.intervention.InterventionCreateRequestDTO;
import com.smartflow.dto.intervention.InterventionResponseDTO;
import com.smartflow.dto.intervention.InterventionStepUpdateRequestDTO;
import com.smartflow.entity.Charter;
import com.smartflow.entity.CharterStatus;
import com.smartflow.entity.CharterStep;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Intervention;
import com.smartflow.entity.InterventionStatus;
import com.smartflow.entity.InterventionStep;
import com.smartflow.entity.Priority;
import com.smartflow.entity.Role;
import com.smartflow.entity.Service;
import com.smartflow.entity.StepStatus;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.InterventionRepository;
import com.smartflow.repository.InterventionStepRepository;
import com.smartflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
class InterventionServiceTest {

    @Mock
    private InterventionRepository interventionRepository;

    @Mock
    private InterventionStepRepository interventionStepRepository;

    @Mock
    private CharterRepository charterRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InterventionService interventionService;

    private Service serviceInformatique;
    private User admin;
    private User manager;
    private User agent;
    private User employee;
    private Incident incident;
    private Charter charter;
    private CharterStep charterStep1;
    private CharterStep charterStep2;
    private Intervention intervention;
    private InterventionStep step1;
    private InterventionStep step2;

    @BeforeEach
    void setUp() {
        serviceInformatique = Service.builder()
                .id(1L)
                .name("Informatique")
                .active(true)
                .build();

        admin = User.builder()
                .id(1L)
                .email("admin@smartflow.com")
                .firstName("Admin")
                .lastName("System")
                .role(Role.ADMIN)
                .active(true)
                .build();

        manager = User.builder()
                .id(2L)
                .email("manager@smartflow.com")
                .firstName("Marc")
                .lastName("Manager")
                .role(Role.MANAGER)
                .service(serviceInformatique)
                .active(true)
                .build();

        agent = User.builder()
                .id(3L)
                .email("agent@smartflow.com")
                .firstName("Alain")
                .lastName("Agent")
                .role(Role.AGENT)
                .service(serviceInformatique)
                .active(true)
                .build();

        employee = User.builder()
                .id(4L)
                .email("employee@smartflow.com")
                .firstName("Emma")
                .lastName("Employee")
                .role(Role.EMPLOYEE)
                .service(serviceInformatique)
                .active(true)
                .build();

        incident = Incident.builder()
                .id(10L)
                .reference("INC-202610-0010")
                .title("Panne serveur")
                .description("Le serveur principal est injoignable")
                .priority(Priority.HAUTE)
                .status(IncidentStatus.EN_ATTENTE_VALIDATION)
                .service(serviceInformatique)
                .reporter(employee)
                .build();

        charterStep1 = CharterStep.builder()
                .id(101L)
                .stepOrder(1)
                .title("Diagnostic physique")
                .description("Vérifier le câblage et les voyants")
                .estimatedDurationMinutes(15)
                .build();

        charterStep2 = CharterStep.builder()
                .id(102L)
                .stepOrder(2)
                .title("Remplacement alimentation")
                .description("Changer le bloc d'alimentation défectueux")
                .estimatedDurationMinutes(30)
                .build();

        charter = Charter.builder()
                .id(20L)
                .incident(incident)
                .title("Charte d'intervention serveur")
                .objective("Rétablir l'accès au serveur")
                .versionNumber(1)
                .status(CharterStatus.VALIDEE)
                .createdBy(manager)
                .validatedBy(manager)
                .validatedAt(LocalDateTime.now())
                .steps(new ArrayList<>(List.of(charterStep1, charterStep2)))
                .build();

        step1 = InterventionStep.builder()
                .id(201L)
                .stepOrder(1)
                .title("Diagnostic physique")
                .description("Vérifier le câblage et les voyants")
                .estimatedDurationMinutes(15)
                .status(StepStatus.A_FAIRE)
                .build();

        step2 = InterventionStep.builder()
                .id(202L)
                .stepOrder(2)
                .title("Remplacement alimentation")
                .description("Changer le bloc d'alimentation défectueux")
                .estimatedDurationMinutes(30)
                .status(StepStatus.A_FAIRE)
                .build();

        intervention = Intervention.builder()
                .id(30L)
                .reference("INT-202610-0001")
                .incident(incident)
                .charter(charter)
                .assignedAgent(agent)
                .createdBy(manager)
                .status(InterventionStatus.AFFECTEE)
                .steps(new ArrayList<>(List.of(step1, step2)))
                .build();

        step1.setIntervention(intervention);
        step2.setIntervention(intervention);
    }

    @Test
    void createIntervention_WithValidCharterAndAgent_ShouldCreateSuccessfully() {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .assignedAgentId(3L)
                .scheduledStartDate(LocalDateTime.now().plusHours(2))
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));
        when(interventionRepository.existsByCharterIdAndStatusNotIn(eq(20L), any())).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(agent));
        when(interventionRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(interventionRepository.existsByReference(any())).thenReturn(false);
        when(interventionRepository.save(any(Intervention.class))).thenAnswer(invocation -> {
            Intervention i = invocation.getArgument(0);
            i.setId(30L);
            return i;
        });

        InterventionResponseDTO result = interventionService.createIntervention(request, manager.getEmail());

        assertNotNull(result);
        assertEquals(30L, result.getId());
        assertEquals(InterventionStatus.AFFECTEE, result.getStatus());
        assertEquals(3L, result.getAssignedAgentId());
        assertEquals(IncidentStatus.ASSIGNE, incident.getStatus());
        assertEquals(2, result.getSteps().size());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void createIntervention_WithoutAgent_ShouldCreateInPlanifieeStatus() {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));
        when(interventionRepository.existsByCharterIdAndStatusNotIn(eq(20L), any())).thenReturn(false);
        when(interventionRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(interventionRepository.existsByReference(any())).thenReturn(false);
        when(interventionRepository.save(any(Intervention.class))).thenAnswer(invocation -> {
            Intervention i = invocation.getArgument(0);
            i.setId(31L);
            return i;
        });

        InterventionResponseDTO result = interventionService.createIntervention(request, manager.getEmail());

        assertNotNull(result);
        assertEquals(InterventionStatus.PLANIFIEE, result.getStatus());
    }

    @Test
    void createIntervention_WhenCharterNotValidee_ShouldThrowBusinessException() {
        charter.setStatus(CharterStatus.BROUILLON);
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));

        assertThrows(BusinessException.class, () ->
                interventionService.createIntervention(request, manager.getEmail())
        );
    }

    @Test
    void createIntervention_WhenIncidentClosed_ShouldThrowBusinessException() {
        incident.setStatus(IncidentStatus.CLOTURE);
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));

        assertThrows(BusinessException.class, () ->
                interventionService.createIntervention(request, manager.getEmail())
        );
    }

    @Test
    void createIntervention_WhenActiveInterventionAlreadyExists_ShouldThrowBusinessException() {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));
        when(interventionRepository.existsByCharterIdAndStatusNotIn(eq(20L), any())).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                interventionService.createIntervention(request, manager.getEmail())
        );
    }

    @Test
    void createIntervention_WhenUserNotAgentRole_ShouldThrowBusinessException() {
        InterventionCreateRequestDTO request = InterventionCreateRequestDTO.builder()
                .charterId(20L)
                .assignedAgentId(4L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(charterRepository.findById(20L)).thenReturn(Optional.of(charter));
        when(interventionRepository.existsByCharterIdAndStatusNotIn(eq(20L), any())).thenReturn(false);
        when(userRepository.findById(4L)).thenReturn(Optional.of(employee));

        assertThrows(BusinessException.class, () ->
                interventionService.createIntervention(request, manager.getEmail())
        );
    }

    @Test
    void assignAgent_WhenValid_ShouldAssignAndChangeStatusToAffectee() {
        intervention.setStatus(InterventionStatus.PLANIFIEE);
        intervention.setAssignedAgent(null);

        InterventionAssignRequestDTO request = InterventionAssignRequestDTO.builder()
                .agentId(3L)
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(3L)).thenReturn(Optional.of(agent));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.assignAgent(30L, request, manager.getEmail());

        assertNotNull(result);
        assertEquals(InterventionStatus.AFFECTEE, intervention.getStatus());
        assertEquals(agent, intervention.getAssignedAgent());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void assignAgent_WhenInterventionTerminee_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.TERMINEE);
        InterventionAssignRequestDTO request = InterventionAssignRequestDTO.builder()
                .agentId(3L)
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));

        assertThrows(BusinessException.class, () ->
                interventionService.assignAgent(30L, request, manager.getEmail())
        );
    }

    @Test
    void startIntervention_WhenAffectee_ShouldStartAndTransitionIncidentToEnCours() {
        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.startIntervention(30L, agent.getEmail());

        assertNotNull(result);
        assertEquals(InterventionStatus.EN_COURS, intervention.getStatus());
        assertEquals(IncidentStatus.EN_COURS, incident.getStatus());
        assertNotNull(intervention.getActualStartDate());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void startIntervention_WhenAlreadyEnCours_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));

        assertThrows(BusinessException.class, () ->
                interventionService.startIntervention(30L, agent.getEmail())
        );
    }

    @Test
    void updateStep_WhenStepBloquee_ShouldRequireBlockReasonAndSetInterventionToEnAttente() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        InterventionStepUpdateRequestDTO request = InterventionStepUpdateRequestDTO.builder()
                .status(StepStatus.BLOQUEE)
                .blockReason("Pièce de rechange manquante")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionStepRepository.findById(201L)).thenReturn(Optional.of(step1));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.updateStep(30L, 201L, request, agent.getEmail());

        assertNotNull(result);
        assertEquals(StepStatus.BLOQUEE, step1.getStatus());
        assertEquals("Pièce de rechange manquante", step1.getBlockReason());
        assertEquals(InterventionStatus.EN_ATTENTE, intervention.getStatus());
        assertEquals(IncidentStatus.EN_ATTENTE, incident.getStatus());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void updateStep_WhenStepBloqueeWithoutReason_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        InterventionStepUpdateRequestDTO request = InterventionStepUpdateRequestDTO.builder()
                .status(StepStatus.BLOQUEE)
                .blockReason("   ")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionStepRepository.findById(201L)).thenReturn(Optional.of(step1));

        assertThrows(BusinessException.class, () ->
                interventionService.updateStep(30L, 201L, request, agent.getEmail())
        );
    }

    @Test
    void updateStep_WhenStepTerminee_ShouldSetCompletedAt() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        InterventionStepUpdateRequestDTO request = InterventionStepUpdateRequestDTO.builder()
                .status(StepStatus.TERMINEE)
                .actualDurationMinutes(20)
                .agentNotes("Vérification terminée avec succès")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionStepRepository.findById(201L)).thenReturn(Optional.of(step1));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.updateStep(30L, 201L, request, agent.getEmail());

        assertNotNull(result);
        assertEquals(StepStatus.TERMINEE, step1.getStatus());
        assertNotNull(step1.getCompletedAt());
        assertEquals(20, step1.getActualDurationMinutes());
        assertEquals("Vérification terminée avec succès", step1.getAgentNotes());
    }

    @Test
    void updateStep_WhenStepDoesNotBelongToIntervention_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        Intervention otherIntervention = Intervention.builder().id(999L).build();
        step1.setIntervention(otherIntervention);

        InterventionStepUpdateRequestDTO request = InterventionStepUpdateRequestDTO.builder()
                .status(StepStatus.EN_COURS)
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionStepRepository.findById(201L)).thenReturn(Optional.of(step1));

        assertThrows(BusinessException.class, () ->
                interventionService.updateStep(30L, 201L, request, agent.getEmail())
        );
    }

    @Test
    void completeIntervention_WhenAllStepsTerminee_ShouldResolveIncidentAndSetTerminee() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        step1.setStatus(StepStatus.TERMINEE);
        step2.setStatus(StepStatus.TERMINEE);

        InterventionCompleteRequestDTO request = InterventionCompleteRequestDTO.builder()
                .report("Le remplacement d'alimentation s'est déroulé sans accroc. Le serveur redémarre correctement.")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.completeIntervention(30L, request, agent.getEmail());

        assertNotNull(result);
        assertEquals(InterventionStatus.TERMINEE, intervention.getStatus());
        assertEquals(IncidentStatus.RESOLU, incident.getStatus());
        assertNotNull(intervention.getActualEndDate());
        assertNotNull(incident.getResolvedAt());
        assertEquals(request.getReport(), intervention.getReport());
        verify(incidentRepository).save(incident);
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void completeIntervention_WhenUnfinishedStepsExist_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.EN_COURS);
        step1.setStatus(StepStatus.TERMINEE);
        step2.setStatus(StepStatus.A_FAIRE);

        InterventionCompleteRequestDTO request = InterventionCompleteRequestDTO.builder()
                .report("Rapport partiel")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));

        assertThrows(BusinessException.class, () ->
                interventionService.completeIntervention(30L, request, agent.getEmail())
        );
    }

    @Test
    void cancelIntervention_WhenValid_ShouldCancelWithReason() {
        InterventionCancelRequestDTO request = InterventionCancelRequestDTO.builder()
                .cancellationReason("Erreur de planification du créneau horaire")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(interventionRepository.save(any(Intervention.class))).thenReturn(intervention);

        InterventionResponseDTO result = interventionService.cancelIntervention(30L, request, manager.getEmail());

        assertNotNull(result);
        assertEquals(InterventionStatus.ANNULEE, intervention.getStatus());
        assertEquals("Erreur de planification du créneau horaire", intervention.getCancellationReason());
        assertNotNull(intervention.getActualEndDate());
        verify(historyRepository).save(any(IncidentHistory.class));
    }

    @Test
    void cancelIntervention_WhenAlreadyTerminee_ShouldThrowBusinessException() {
        intervention.setStatus(InterventionStatus.TERMINEE);
        InterventionCancelRequestDTO request = InterventionCancelRequestDTO.builder()
                .cancellationReason("Annulation impossible")
                .build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));

        assertThrows(BusinessException.class, () ->
                interventionService.cancelIntervention(30L, request, manager.getEmail())
        );
    }

    @Test
    void getInterventionById_WhenEmployeeNotReporter_ShouldThrowBusinessException() {
        User otherEmployee = User.builder().id(99L).role(Role.EMPLOYEE).build();

        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail("other@smartflow.com")).thenReturn(Optional.of(otherEmployee));

        assertThrows(BusinessException.class, () ->
                interventionService.getInterventionById(30L, "other@smartflow.com")
        );
    }

    @Test
    void getInterventionById_WhenAdmin_ShouldReturnIntervention() {
        when(interventionRepository.findById(30L)).thenReturn(Optional.of(intervention));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        InterventionResponseDTO result = interventionService.getInterventionById(30L, admin.getEmail());

        assertNotNull(result);
        assertEquals(30L, result.getId());
    }

    @Test
    void getInterventionsByIncidentId_ShouldReturnList() {
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(interventionRepository.findByIncidentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(intervention));

        List<InterventionResponseDTO> results = interventionService.getInterventionsByIncidentId(10L, admin.getEmail());

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(30L, results.get(0).getId());
    }
}
