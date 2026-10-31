package com.smartflow.service;

import com.smartflow.dto.intervention.InterventionAssignRequestDTO;
import com.smartflow.dto.intervention.InterventionCancelRequestDTO;
import com.smartflow.dto.intervention.InterventionCompleteRequestDTO;
import com.smartflow.dto.intervention.InterventionCreateRequestDTO;
import com.smartflow.dto.intervention.InterventionResponseDTO;
import com.smartflow.dto.intervention.InterventionStepResponseDTO;
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
import com.smartflow.entity.Role;
import com.smartflow.entity.StepStatus;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.InterventionRepository;
import com.smartflow.repository.InterventionStepRepository;
import com.smartflow.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final InterventionStepRepository interventionStepRepository;
    private final CharterRepository charterRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final UserRepository userRepository;

    @Transactional
    public InterventionResponseDTO createIntervention(InterventionCreateRequestDTO request, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);

        Charter charter = charterRepository.findById(request.getCharterId())
                .orElseThrow(() -> new ResourceNotFoundException("Charte non trouvée avec l'id : " + request.getCharterId()));

        Incident incident = charter.getIncident();
        checkManagerOrAdminAccess(currentUser, incident);

        if (charter.getStatus() != CharterStatus.VALIDEE) {
            throw new BusinessException("Impossible de créer une intervention à partir d'une charte non validée (statut actuel : " + charter.getStatus() + ")");
        }

        if (incident.getStatus() == IncidentStatus.CLOTURE || incident.getStatus() == IncidentStatus.REJETE) {
            throw new BusinessException("Impossible de planifier une intervention pour un incident clôturé ou rejeté");
        }

        boolean activeInterventionExists = interventionRepository.existsByCharterIdAndStatusNotIn(
                charter.getId(),
                List.of(InterventionStatus.TERMINEE, InterventionStatus.ANNULEE)
        );
        if (activeInterventionExists) {
            throw new BusinessException("Une intervention active existe déjà pour cette charte d'intervention");
        }

        String reference = generateReference();

        User assignedAgent = null;
        InterventionStatus initialStatus = InterventionStatus.PLANIFIEE;

        if (request.getAssignedAgentId() != null) {
            assignedAgent = userRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé avec l'id : " + request.getAssignedAgentId()));

            if (Boolean.FALSE.equals(assignedAgent.getActive())) {
                throw new BusinessException("Impossible d'affecter un agent inactif");
            }

            if (assignedAgent.getRole() != Role.AGENT) {
                throw new BusinessException("L'utilisateur sélectionné n'a pas le rôle AGENT");
            }

            initialStatus = InterventionStatus.AFFECTEE;
            incident.setAssignedTo(assignedAgent);
            if (incident.getStatus() == IncidentStatus.NOUVEAU || incident.getStatus() == IncidentStatus.EN_ATTENTE_VALIDATION) {
                incident.setStatus(IncidentStatus.ASSIGNE);
            }
            incidentRepository.save(incident);
        }

        Intervention intervention = Intervention.builder()
                .reference(reference)
                .incident(incident)
                .charter(charter)
                .assignedAgent(assignedAgent)
                .createdBy(currentUser)
                .status(initialStatus)
                .scheduledStartDate(request.getScheduledStartDate())
                .build();

        if (charter.getSteps() != null) {
            for (CharterStep charterStep : charter.getSteps()) {
                InterventionStep step = InterventionStep.builder()
                        .intervention(intervention)
                        .charterStep(charterStep)
                        .stepOrder(charterStep.getStepOrder())
                        .title(charterStep.getTitle())
                        .description(charterStep.getDescription())
                        .estimatedDurationMinutes(charterStep.getEstimatedDurationMinutes())
                        .status(StepStatus.A_FAIRE)
                        .build();
                intervention.addStep(step);
            }
        }

        Intervention savedIntervention = interventionRepository.save(intervention);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("INTERVENTION_CREATED")
                .oldValue(null)
                .newValue(reference + " - " + initialStatus)
                .comment("Création de l'intervention " + reference + (assignedAgent != null ? " affectée à " + assignedAgent.getFullName() : ""))
                .operator(currentUser)
                .build();
        historyRepository.save(history);

        log.info("Intervention {} créée avec succès pour l'incident {} par {}", reference, incident.getReference(), currentUser.getEmail());
        return mapToResponseDTO(savedIntervention);
    }

    @Transactional
    public InterventionResponseDTO assignAgent(Long interventionId, InterventionAssignRequestDTO request, String currentUserEmail) {
        Intervention intervention = getInterventionEntity(interventionId);
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = intervention.getIncident();

        checkManagerOrAdminAccess(currentUser, incident);

        if (intervention.getStatus() == InterventionStatus.TERMINEE || intervention.getStatus() == InterventionStatus.ANNULEE) {
            throw new BusinessException("Impossible d'assigner un agent à une intervention terminée ou annulée");
        }

        User agent = userRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé avec l'id : " + request.getAgentId()));

        if (Boolean.FALSE.equals(agent.getActive())) {
            throw new BusinessException("Impossible d'affecter un agent inactif");
        }

        if (agent.getRole() != Role.AGENT) {
            throw new BusinessException("L'utilisateur sélectionné n'a pas le rôle AGENT");
        }

        User previousAgent = intervention.getAssignedAgent();
        intervention.setAssignedAgent(agent);
        if (intervention.getStatus() == InterventionStatus.PLANIFIEE) {
            intervention.setStatus(InterventionStatus.AFFECTEE);
        }

        incident.setAssignedTo(agent);
        if (incident.getStatus() == IncidentStatus.NOUVEAU || incident.getStatus() == IncidentStatus.EN_ATTENTE_VALIDATION) {
            incident.setStatus(IncidentStatus.ASSIGNE);
        }
        incidentRepository.save(incident);

        Intervention savedIntervention = interventionRepository.save(intervention);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("INTERVENTION_ASSIGNED")
                .oldValue(previousAgent != null ? previousAgent.getFullName() : "Non assigné")
                .newValue(agent.getFullName())
                .comment("Affectation de l'intervention " + intervention.getReference() + " à " + agent.getFullName())
                .operator(currentUser)
                .build();
        historyRepository.save(history);

        log.info("Intervention {} affectée à l'agent {} par {}", intervention.getReference(), agent.getEmail(), currentUser.getEmail());
        return mapToResponseDTO(savedIntervention);
    }

    @Transactional
    public InterventionResponseDTO startIntervention(Long interventionId, String currentUserEmail) {
        Intervention intervention = getInterventionEntity(interventionId);
        User currentUser = getUserByEmail(currentUserEmail);

        checkCanOperateIntervention(currentUser, intervention);

        if (intervention.getStatus() == InterventionStatus.TERMINEE || intervention.getStatus() == InterventionStatus.ANNULEE) {
            throw new BusinessException("Impossible de démarrer une intervention terminée ou annulée");
        }

        if (intervention.getStatus() == InterventionStatus.EN_COURS) {
            throw new BusinessException("L'intervention est déjà en cours d'exécution");
        }

        if (intervention.getActualStartDate() == null) {
            intervention.setActualStartDate(LocalDateTime.now());
        }
        intervention.setStatus(InterventionStatus.EN_COURS);

        Incident incident = intervention.getIncident();
        incident.setStatus(IncidentStatus.EN_COURS);
        incidentRepository.save(incident);

        Intervention savedIntervention = interventionRepository.save(intervention);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("INTERVENTION_STARTED")
                .oldValue(IncidentStatus.ASSIGNE.name())
                .newValue(IncidentStatus.EN_COURS.name())
                .comment("Démarrage de l'intervention " + intervention.getReference() + " par " + currentUser.getFullName())
                .operator(currentUser)
                .build();
        historyRepository.save(history);

        log.info("Intervention {} démarrée par {}", intervention.getReference(), currentUser.getEmail());
        return mapToResponseDTO(savedIntervention);
    }

    @Transactional
    public InterventionResponseDTO updateStep(
            Long interventionId,
            Long stepId,
            InterventionStepUpdateRequestDTO request,
            String currentUserEmail) {

        Intervention intervention = getInterventionEntity(interventionId);
        User currentUser = getUserByEmail(currentUserEmail);

        checkCanOperateIntervention(currentUser, intervention);

        if (intervention.getStatus() != InterventionStatus.EN_COURS && intervention.getStatus() != InterventionStatus.EN_ATTENTE) {
            throw new BusinessException("L'intervention doit être en cours ou en attente pour mettre à jour une étape");
        }

        InterventionStep step = interventionStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Étape d'intervention non trouvée avec l'id : " + stepId));

        if (!step.getIntervention().getId().equals(intervention.getId())) {
            throw new BusinessException("L'étape n'appartient pas à l'intervention spécifiée");
        }

        StepStatus targetStatus = request.getStatus();
        step.setStatus(targetStatus);

        if (targetStatus == StepStatus.BLOQUEE) {
            if (request.getBlockReason() == null || request.getBlockReason().trim().isEmpty()) {
                throw new BusinessException("Le motif de blocage est obligatoire lorsqu'une étape est marquée comme bloquée");
            }
            step.setBlockReason(request.getBlockReason().trim());
            intervention.setStatus(InterventionStatus.EN_ATTENTE);

            Incident incident = intervention.getIncident();
            incident.setStatus(IncidentStatus.EN_ATTENTE);
            incidentRepository.save(incident);

            IncidentHistory history = IncidentHistory.builder()
                    .incident(incident)
                    .actionType("INTERVENTION_BLOCKED")
                    .oldValue(step.getTitle())
                    .newValue(StepStatus.BLOQUEE.name())
                    .comment("Étape bloquée : " + step.getTitle() + " - Motif : " + request.getBlockReason().trim())
                    .operator(currentUser)
                    .build();
            historyRepository.save(history);
        } else if (targetStatus == StepStatus.EN_COURS) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(LocalDateTime.now());
            }
            step.setBlockReason(null);

            boolean hasOtherBlocked = intervention.getSteps().stream()
                    .anyMatch(s -> !s.getId().equals(step.getId()) && s.getStatus() == StepStatus.BLOQUEE);
            if (!hasOtherBlocked) {
                intervention.setStatus(InterventionStatus.EN_COURS);
                Incident incident = intervention.getIncident();
                incident.setStatus(IncidentStatus.EN_COURS);
                incidentRepository.save(incident);
            }
        } else if (targetStatus == StepStatus.TERMINEE) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(LocalDateTime.now());
            }
            step.setCompletedAt(LocalDateTime.now());
            step.setBlockReason(null);

            boolean hasOtherBlocked = intervention.getSteps().stream()
                    .anyMatch(s -> !s.getId().equals(step.getId()) && s.getStatus() == StepStatus.BLOQUEE);
            if (!hasOtherBlocked && intervention.getStatus() == InterventionStatus.EN_ATTENTE) {
                intervention.setStatus(InterventionStatus.EN_COURS);
                Incident incident = intervention.getIncident();
                incident.setStatus(IncidentStatus.EN_COURS);
                incidentRepository.save(incident);
            }
        }

        if (request.getAgentNotes() != null) {
            step.setAgentNotes(request.getAgentNotes().trim());
        }

        if (request.getActualDurationMinutes() != null) {
            step.setActualDurationMinutes(request.getActualDurationMinutes());
        }

        interventionStepRepository.save(step);
        Intervention savedIntervention = interventionRepository.save(intervention);

        return mapToResponseDTO(savedIntervention);
    }

    @Transactional
    public InterventionResponseDTO completeIntervention(
            Long interventionId,
            InterventionCompleteRequestDTO request,
            String currentUserEmail) {

        Intervention intervention = getInterventionEntity(interventionId);
        User currentUser = getUserByEmail(currentUserEmail);

        checkCanOperateIntervention(currentUser, intervention);

        if (intervention.getStatus() != InterventionStatus.EN_COURS && intervention.getStatus() != InterventionStatus.EN_ATTENTE) {
            throw new BusinessException("Impossible de clôturer une intervention qui n'est pas en cours");
        }

        boolean hasUnfinishedSteps = intervention.getSteps().stream()
                .anyMatch(s -> s.getStatus() != StepStatus.TERMINEE);
        if (hasUnfinishedSteps) {
            throw new BusinessException("Toutes les étapes de l'intervention doivent être terminées avant de pouvoir clôturer");
        }

        intervention.setReport(request.getReport().trim());
        intervention.setStatus(InterventionStatus.TERMINEE);
        intervention.setActualEndDate(LocalDateTime.now());

        Incident incident = intervention.getIncident();
        incident.setStatus(IncidentStatus.RESOLU);
        incident.setResolvedAt(LocalDateTime.now());
        incidentRepository.save(incident);

        Intervention savedIntervention = interventionRepository.save(intervention);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("INTERVENTION_COMPLETED")
                .oldValue(InterventionStatus.EN_COURS.name())
                .newValue(InterventionStatus.TERMINEE.name())
                .comment("Intervention terminée avec succès. Compte rendu : " + request.getReport().trim())
                .operator(currentUser)
                .build();
        historyRepository.save(history);

        log.info("Intervention {} clôturée avec succès par {}", intervention.getReference(), currentUser.getEmail());
        return mapToResponseDTO(savedIntervention);
    }

    @Transactional
    public InterventionResponseDTO cancelIntervention(
            Long interventionId,
            InterventionCancelRequestDTO request,
            String currentUserEmail) {

        Intervention intervention = getInterventionEntity(interventionId);
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = intervention.getIncident();

        checkManagerOrAdminAccess(currentUser, incident);

        if (intervention.getStatus() == InterventionStatus.TERMINEE || intervention.getStatus() == InterventionStatus.ANNULEE) {
            throw new BusinessException("Impossible d'annuler une intervention déjà terminée ou annulée");
        }

        intervention.setStatus(InterventionStatus.ANNULEE);
        intervention.setCancellationReason(request.getCancellationReason().trim());
        intervention.setActualEndDate(LocalDateTime.now());

        Intervention savedIntervention = interventionRepository.save(intervention);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("INTERVENTION_CANCELLED")
                .oldValue(intervention.getStatus().name())
                .newValue(InterventionStatus.ANNULEE.name())
                .comment("Annulation de l'intervention " + intervention.getReference() + " : " + request.getCancellationReason().trim())
                .operator(currentUser)
                .build();
        historyRepository.save(history);

        log.info("Intervention {} annulée par {}", intervention.getReference(), currentUser.getEmail());
        return mapToResponseDTO(savedIntervention);
    }

    @Transactional(readOnly = true)
    public InterventionResponseDTO getInterventionById(Long id, String currentUserEmail) {
        Intervention intervention = getInterventionEntity(id);
        User currentUser = getUserByEmail(currentUserEmail);
        verifyViewAccess(intervention, currentUser);
        return mapToResponseDTO(intervention);
    }

    @Transactional(readOnly = true)
    public List<InterventionResponseDTO> getInterventionsByIncidentId(Long incidentId, String currentUserEmail) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident non trouvé avec l'id : " + incidentId));
        User currentUser = getUserByEmail(currentUserEmail);
        verifyIncidentViewAccess(incident, currentUser);

        return interventionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<InterventionResponseDTO> searchInterventions(
            InterventionStatus status,
            Long assignedAgentId,
            Long incidentId,
            String search,
            Pageable pageable,
            String currentUserEmail) {

        User currentUser = getUserByEmail(currentUserEmail);

        Specification<Intervention> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (currentUser.getRole() == Role.EMPLOYEE) {
                predicates.add(cb.equal(root.get("incident").get("reporter").get("id"), currentUser.getId()));
            } else if (currentUser.getRole() == Role.AGENT) {
                Predicate assignedToAgent = cb.equal(root.get("assignedAgent").get("id"), currentUser.getId());
                if (currentUser.getService() != null) {
                    Predicate sameService = cb.equal(root.get("incident").get("service").get("id"), currentUser.getService().getId());
                    predicates.add(cb.or(assignedToAgent, sameService));
                } else {
                    predicates.add(assignedToAgent);
                }
            } else if (currentUser.getRole() == Role.MANAGER) {
                if (currentUser.getService() != null) {
                    predicates.add(cb.equal(root.get("incident").get("service").get("id"), currentUser.getService().getId()));
                }
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (assignedAgentId != null) {
                predicates.add(cb.equal(root.get("assignedAgent").get("id"), assignedAgentId));
            }

            if (incidentId != null) {
                predicates.add(cb.equal(root.get("incident").get("id"), incidentId));
            }

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate refPredicate = cb.like(cb.lower(root.get("reference")), pattern);
                Predicate reportPredicate = cb.like(cb.lower(root.get("report")), pattern);
                Predicate incidentRefPredicate = cb.like(cb.lower(root.get("incident").get("reference")), pattern);
                Predicate incidentTitlePredicate = cb.like(cb.lower(root.get("incident").get("title")), pattern);
                predicates.add(cb.or(refPredicate, reportPredicate, incidentRefPredicate, incidentTitlePredicate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return interventionRepository.findAll(spec, pageable).map(this::mapToResponseDTO);
    }

    private synchronized String generateReference() {
        LocalDateTime now = LocalDateTime.now();
        String prefix = String.format("INT-%04d%02d-", now.getYear(), now.getMonthValue());
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

        long count = interventionRepository.countByCreatedAtBetween(startOfMonth, endOfMonth) + 1;
        String ref = String.format("%s%04d", prefix, count);

        while (interventionRepository.existsByReference(ref)) {
            count++;
            ref = String.format("%s%04d", prefix, count);
        }

        return ref;
    }

    private Intervention getInterventionEntity(Long id) {
        return interventionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention non trouvée avec l'id : " + id));
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));
    }

    private void checkManagerOrAdminAccess(User user, Incident incident) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.MANAGER && user.getService() != null && incident.getService() != null
                && user.getService().getId().equals(incident.getService().getId())) {
            return;
        }
        throw new BusinessException("Seuls l'administrateur ou le responsable du service concerné peuvent effectuer cette action");
    }

    private void checkCanOperateIntervention(User user, Intervention intervention) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.AGENT && intervention.getAssignedAgent() != null
                && intervention.getAssignedAgent().getId().equals(user.getId())) {
            return;
        }
        Incident incident = intervention.getIncident();
        if (user.getRole() == Role.MANAGER && user.getService() != null && incident.getService() != null
                && user.getService().getId().equals(incident.getService().getId())) {
            return;
        }
        throw new BusinessException("Vous n'êtes pas autorisé à opérer sur cette intervention");
    }

    private void verifyViewAccess(Intervention intervention, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        Incident incident = intervention.getIncident();
        if (user.getRole() == Role.EMPLOYEE && incident.getReporter().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == Role.AGENT) {
            if (intervention.getAssignedAgent() != null && intervention.getAssignedAgent().getId().equals(user.getId())) {
                return;
            }
            if (user.getService() != null && incident.getService() != null && user.getService().getId().equals(incident.getService().getId())) {
                return;
            }
        }
        if (user.getRole() == Role.MANAGER && user.getService() != null && incident.getService() != null
                && user.getService().getId().equals(incident.getService().getId())) {
            return;
        }
        throw new BusinessException("Vous n'avez pas accès à cette intervention");
    }

    private void verifyIncidentViewAccess(Incident incident, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.EMPLOYEE && incident.getReporter().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == Role.AGENT) {
            if (incident.getAssignedTo() != null && incident.getAssignedTo().getId().equals(user.getId())) {
                return;
            }
            if (user.getService() != null && incident.getService() != null && user.getService().getId().equals(incident.getService().getId())) {
                return;
            }
        }
        if (user.getRole() == Role.MANAGER && user.getService() != null && incident.getService() != null
                && user.getService().getId().equals(incident.getService().getId())) {
            return;
        }
        throw new BusinessException("Vous n'avez pas accès à cet incident");
    }

    public InterventionResponseDTO mapToResponseDTO(Intervention intervention) {
        List<InterventionStepResponseDTO> stepDTOs = intervention.getSteps() != null
                ? intervention.getSteps().stream().map(this::mapStepToDTO).collect(Collectors.toList())
                : List.of();

        return InterventionResponseDTO.builder()
                .id(intervention.getId())
                .reference(intervention.getReference())
                .incidentId(intervention.getIncident().getId())
                .incidentReference(intervention.getIncident().getReference())
                .incidentTitle(intervention.getIncident().getTitle())
                .charterId(intervention.getCharter().getId())
                .charterTitle(intervention.getCharter().getTitle())
                .charterVersion(intervention.getCharter().getVersionNumber())
                .assignedAgentId(intervention.getAssignedAgent() != null ? intervention.getAssignedAgent().getId() : null)
                .assignedAgentName(intervention.getAssignedAgent() != null ? intervention.getAssignedAgent().getFullName() : null)
                .assignedAgentEmail(intervention.getAssignedAgent() != null ? intervention.getAssignedAgent().getEmail() : null)
                .createdById(intervention.getCreatedBy().getId())
                .createdByName(intervention.getCreatedBy().getFullName())
                .status(intervention.getStatus())
                .scheduledStartDate(intervention.getScheduledStartDate())
                .actualStartDate(intervention.getActualStartDate())
                .actualEndDate(intervention.getActualEndDate())
                .report(intervention.getReport())
                .cancellationReason(intervention.getCancellationReason())
                .createdAt(intervention.getCreatedAt())
                .updatedAt(intervention.getUpdatedAt())
                .steps(stepDTOs)
                .build();
    }

    public InterventionStepResponseDTO mapStepToDTO(InterventionStep step) {
        return InterventionStepResponseDTO.builder()
                .id(step.getId())
                .charterStepId(step.getCharterStep() != null ? step.getCharterStep().getId() : null)
                .stepOrder(step.getStepOrder())
                .title(step.getTitle())
                .description(step.getDescription())
                .estimatedDurationMinutes(step.getEstimatedDurationMinutes())
                .actualDurationMinutes(step.getActualDurationMinutes())
                .status(step.getStatus())
                .blockReason(step.getBlockReason())
                .agentNotes(step.getAgentNotes())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();
    }
}
