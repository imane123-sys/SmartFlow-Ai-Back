package com.smartflow.service;

import com.smartflow.dto.charter.CharterCreateRequestDTO;
import com.smartflow.dto.charter.CharterRejectRequestDTO;
import com.smartflow.dto.charter.CharterResponseDTO;
import com.smartflow.dto.charter.CharterStepRequestDTO;
import com.smartflow.dto.charter.CharterStepResponseDTO;
import com.smartflow.dto.charter.CharterUpdateRequestDTO;
import com.smartflow.dto.charter.GeminiCharterResult;
import com.smartflow.dto.charter.GeminiCharterStepResult;
import com.smartflow.entity.AiAnalysis;
import com.smartflow.entity.Charter;
import com.smartflow.entity.CharterStatus;
import com.smartflow.entity.CharterStep;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Role;
import com.smartflow.entity.User;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.AiAnalysisRepository;
import com.smartflow.repository.CharterRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharterService {

    private final CharterRepository charterRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final GeminiService geminiService;

    @Transactional
    public CharterResponseDTO generateCharterDraftFromAi(Long incidentId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = getIncidentEntity(incidentId);

        checkManagerOrAdminAccess(currentUser, incident);
        checkIncidentNotClosed(incident);

        List<AiAnalysis> analyses = aiAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        AiAnalysis latestAnalysis = analyses.isEmpty() ? null : analyses.get(0);

        GeminiCharterResult aiResult = geminiService.generateCharterDraft(incident, latestAnalysis);

        int versionNumber = charterRepository.countByIncidentId(incidentId) + 1;

        Charter charter = Charter.builder()
                .incident(incident)
                .title(aiResult.getTitle())
                .objective(aiResult.getObjective())
                .safetyPrecautions(aiResult.getSafetyPrecautions())
                .requiredResources(aiResult.getRequiredResources())
                .versionNumber(versionNumber)
                .status(CharterStatus.BROUILLON)
                .createdBy(currentUser)
                .build();

        if (aiResult.getSteps() != null) {
            for (GeminiCharterStepResult stepResult : aiResult.getSteps()) {
                CharterStep step = CharterStep.builder()
                        .stepOrder(stepResult.getStepOrder())
                        .title(stepResult.getTitle())
                        .description(stepResult.getDescription())
                        .estimatedDurationMinutes(stepResult.getEstimatedDurationMinutes())
                        .build();
                charter.addStep(step);
            }
        }

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("CHARTER_GENERATION")
                .oldValue(null)
                .newValue("v" + versionNumber + " - " + CharterStatus.BROUILLON)
                .comment("Génération automatique par l'IA d'une proposition de charte (version " + versionNumber + ")")
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte IA créée en brouillon (v{}) pour l'incident {} par {}", versionNumber, incident.getReference(), currentUser.getEmail());

        return mapToResponseDTO(savedCharter);
    }

    @Transactional
    public CharterResponseDTO createManualCharter(Long incidentId, CharterCreateRequestDTO request, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = getIncidentEntity(incidentId);

        checkManagerOrAdminAccess(currentUser, incident);
        checkIncidentNotClosed(incident);

        int versionNumber = charterRepository.countByIncidentId(incidentId) + 1;

        Charter charter = Charter.builder()
                .incident(incident)
                .title(request.getTitle().trim())
                .objective(request.getObjective().trim())
                .safetyPrecautions(request.getSafetyPrecautions() != null ? request.getSafetyPrecautions().trim() : null)
                .requiredResources(request.getRequiredResources() != null ? request.getRequiredResources().trim() : null)
                .versionNumber(versionNumber)
                .status(CharterStatus.BROUILLON)
                .createdBy(currentUser)
                .build();

        List<CharterStepRequestDTO> sortedSteps = request.getSteps().stream()
                .sorted(Comparator.comparing(CharterStepRequestDTO::getStepOrder))
                .toList();

        for (CharterStepRequestDTO stepDTO : sortedSteps) {
            CharterStep step = CharterStep.builder()
                    .stepOrder(stepDTO.getStepOrder())
                    .title(stepDTO.getTitle().trim())
                    .description(stepDTO.getDescription().trim())
                    .estimatedDurationMinutes(stepDTO.getEstimatedDurationMinutes())
                    .build();
            charter.addStep(step);
        }

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("CHARTER_CREATION")
                .oldValue(null)
                .newValue("v" + versionNumber + " - " + CharterStatus.BROUILLON)
                .comment("Création manuelle d'une charte d'intervention (version " + versionNumber + ")")
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte manuelle créée (v{}) pour l'incident {} par {}", versionNumber, incident.getReference(), currentUser.getEmail());

        return mapToResponseDTO(savedCharter);
    }

    @Transactional(readOnly = true)
    public CharterResponseDTO getCharterById(Long charterId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Charter charter = getCharterEntity(charterId);

        checkIncidentReadAccess(charter.getIncident(), currentUser);

        return mapToResponseDTO(charter);
    }

    @Transactional(readOnly = true)
    public List<CharterResponseDTO> getChartersByIncident(Long incidentId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = getIncidentEntity(incidentId);

        checkIncidentReadAccess(incident, currentUser);

        return charterRepository.findByIncidentIdOrderByVersionNumberDesc(incidentId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CharterResponseDTO updateCharter(Long charterId, CharterUpdateRequestDTO request, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Charter charter = getCharterEntity(charterId);

        checkManagerOrAdminAccess(currentUser, charter.getIncident());

        if (charter.getStatus() == CharterStatus.VALIDEE || charter.getStatus() == CharterStatus.ARCHIVEE) {
            throw new BusinessException("Impossible de modifier une charte déjà validée ou archivée");
        }

        charter.setTitle(request.getTitle().trim());
        charter.setObjective(request.getObjective().trim());
        charter.setSafetyPrecautions(request.getSafetyPrecautions() != null ? request.getSafetyPrecautions().trim() : null);
        charter.setRequiredResources(request.getRequiredResources() != null ? request.getRequiredResources().trim() : null);

        charter.getSteps().clear();

        List<CharterStepRequestDTO> sortedSteps = request.getSteps().stream()
                .sorted(Comparator.comparing(CharterStepRequestDTO::getStepOrder))
                .toList();

        for (CharterStepRequestDTO stepDTO : sortedSteps) {
            CharterStep step = CharterStep.builder()
                    .stepOrder(stepDTO.getStepOrder())
                    .title(stepDTO.getTitle().trim())
                    .description(stepDTO.getDescription().trim())
                    .estimatedDurationMinutes(stepDTO.getEstimatedDurationMinutes())
                    .build();
            charter.addStep(step);
        }

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(charter.getIncident())
                .actionType("CHARTER_UPDATE")
                .oldValue(charter.getStatus().name())
                .newValue(charter.getStatus().name())
                .comment("Mise à jour du contenu de la charte v" + charter.getVersionNumber())
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte {} (v{}) mise à jour par {}", charterId, charter.getVersionNumber(), currentUser.getEmail());

        return mapToResponseDTO(savedCharter);
    }

    @Transactional
    public CharterResponseDTO submitCharter(Long charterId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Charter charter = getCharterEntity(charterId);

        checkManagerOrAdminAccess(currentUser, charter.getIncident());

        if (charter.getStatus() != CharterStatus.BROUILLON && charter.getStatus() != CharterStatus.REFUSEE) {
            throw new BusinessException("Seule une charte au statut BROUILLON ou REFUSEE peut être soumise");
        }

        charter.setStatus(CharterStatus.EN_ATTENTE_VALIDATION);
        charter.setRejectionReason(null);

        Incident incident = charter.getIncident();
        if (incident.getStatus() == IncidentStatus.NOUVEAU || incident.getStatus() == IncidentStatus.EN_ANALYSE) {
            incident.setStatus(IncidentStatus.EN_ATTENTE_VALIDATION);
            incidentRepository.save(incident);
        }

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("CHARTER_SUBMISSION")
                .oldValue(CharterStatus.BROUILLON.name())
                .newValue(CharterStatus.EN_ATTENTE_VALIDATION.name())
                .comment("Soumission de la charte v" + charter.getVersionNumber() + " pour validation")
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte {} soumise pour validation par {}", charterId, currentUser.getEmail());

        return mapToResponseDTO(savedCharter);
    }

    @Transactional
    public CharterResponseDTO validateCharter(Long charterId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Charter charter = getCharterEntity(charterId);

        checkManagerOrAdminAccess(currentUser, charter.getIncident());

        if (charter.getStatus() == CharterStatus.VALIDEE) {
            throw new BusinessException("Cette charte est déjà validée");
        }

        if (charter.getStatus() == CharterStatus.ARCHIVEE) {
            throw new BusinessException("Une charte archivée ne peut pas être validée");
        }

        List<Charter> existingCharters = charterRepository.findByIncidentIdOrderByVersionNumberDesc(charter.getIncident().getId());
        for (Charter existing : existingCharters) {
            if (existing.getStatus() == CharterStatus.VALIDEE && !existing.getId().equals(charter.getId())) {
                existing.setStatus(CharterStatus.ARCHIVEE);
                charterRepository.save(existing);
            }
        }

        charter.setStatus(CharterStatus.VALIDEE);
        charter.setValidatedBy(currentUser);
        charter.setValidatedAt(LocalDateTime.now());
        charter.setRejectionReason(null);

        Incident incident = charter.getIncident();
        if (incident.getAssignedTo() != null) {
            incident.setStatus(IncidentStatus.ASSIGNE);
        } else if (incident.getStatus() == IncidentStatus.EN_ATTENTE_VALIDATION || incident.getStatus() == IncidentStatus.EN_ANALYSE || incident.getStatus() == IncidentStatus.NOUVEAU) {
            incident.setStatus(IncidentStatus.ASSIGNE);
        }
        incidentRepository.save(incident);

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("CHARTER_VALIDATION")
                .oldValue(CharterStatus.EN_ATTENTE_VALIDATION.name())
                .newValue(CharterStatus.VALIDEE.name())
                .comment("Charte validée (v" + charter.getVersionNumber() + ") par " + currentUser.getFullName())
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte {} validée par {}", charterId, currentUser.getEmail());

        return mapToResponseDTO(savedCharter);
    }

    @Transactional
    public CharterResponseDTO rejectCharter(Long charterId, CharterRejectRequestDTO request, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Charter charter = getCharterEntity(charterId);

        checkManagerOrAdminAccess(currentUser, charter.getIncident());

        if (charter.getStatus() == CharterStatus.VALIDEE) {
            throw new BusinessException("Une charte déjà validée ne peut pas être rejetée directement");
        }

        if (charter.getStatus() == CharterStatus.ARCHIVEE) {
            throw new BusinessException("Une charte archivée ne peut pas être rejetée");
        }

        if (request.getRejectionReason() == null || request.getRejectionReason().trim().isBlank()) {
            throw new BusinessException("Le motif de refus de la charte est obligatoire");
        }

        String reason = request.getRejectionReason().trim();
        charter.setStatus(CharterStatus.REFUSEE);
        charter.setRejectionReason(reason);
        charter.setValidatedBy(currentUser);
        charter.setValidatedAt(LocalDateTime.now());

        Charter savedCharter = charterRepository.save(charter);

        IncidentHistory history = IncidentHistory.builder()
                .incident(charter.getIncident())
                .actionType("CHARTER_REJECTION")
                .oldValue(CharterStatus.EN_ATTENTE_VALIDATION.name())
                .newValue(CharterStatus.REFUSEE.name())
                .comment("Charte refusée. Motif : " + reason)
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Charte {} refusée par {} avec motif : {}", charterId, currentUser.getEmail(), reason);

        return mapToResponseDTO(savedCharter);
    }

    private void checkManagerOrAdminAccess(User user, Incident incident) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Seuls les Managers et Administrateurs peuvent gérer les chartes");
        }

        if (user.getRole() == Role.MANAGER && user.getService() != null && incident.getService() != null) {
            if (!user.getService().getId().equals(incident.getService().getId())) {
                throw new AccessDeniedException("Vous ne pouvez gérer les chartes que pour les incidents de votre service");
            }
        }
    }

    private void checkIncidentReadAccess(Incident incident, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.EMPLOYEE && incident.getReporter().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == Role.AGENT && incident.getAssignedTo() != null && incident.getAssignedTo().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == Role.MANAGER) {
            if (user.getService() == null || incident.getService() == null || user.getService().getId().equals(incident.getService().getId())) {
                return;
            }
        }
        throw new AccessDeniedException("Accès non autorisé aux chartes de cet incident");
    }

    private void checkIncidentNotClosed(Incident incident) {
        if (incident.getStatus() == IncidentStatus.CLOTURE || incident.getStatus() == IncidentStatus.REJETE) {
            throw new BusinessException("Impossible de créer ou modifier une charte pour un incident clôturé ou rejeté");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));
    }

    private Incident getIncidentEntity(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident non trouvé avec l'id : " + id));
    }

    private Charter getCharterEntity(Long id) {
        return charterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charte non trouvée avec l'id : " + id));
    }

    private CharterResponseDTO mapToResponseDTO(Charter charter) {
        List<CharterStepResponseDTO> stepDTOs = charter.getSteps().stream()
                .sorted(Comparator.comparing(CharterStep::getStepOrder))
                .map(step -> CharterStepResponseDTO.builder()
                        .id(step.getId())
                        .stepOrder(step.getStepOrder())
                        .title(step.getTitle())
                        .description(step.getDescription())
                        .estimatedDurationMinutes(step.getEstimatedDurationMinutes())
                        .build())
                .collect(Collectors.toList());

        return CharterResponseDTO.builder()
                .id(charter.getId())
                .incidentId(charter.getIncident().getId())
                .incidentReference(charter.getIncident().getReference())
                .title(charter.getTitle())
                .objective(charter.getObjective())
                .safetyPrecautions(charter.getSafetyPrecautions())
                .requiredResources(charter.getRequiredResources())
                .versionNumber(charter.getVersionNumber())
                .status(charter.getStatus())
                .rejectionReason(charter.getRejectionReason())
                .createdById(charter.getCreatedBy() != null ? charter.getCreatedBy().getId() : null)
                .createdByName(charter.getCreatedBy() != null ? charter.getCreatedBy().getFullName() : null)
                .validatedById(charter.getValidatedBy() != null ? charter.getValidatedBy().getId() : null)
                .validatedByName(charter.getValidatedBy() != null ? charter.getValidatedBy().getFullName() : null)
                .validatedAt(charter.getValidatedAt())
                .createdAt(charter.getCreatedAt())
                .updatedAt(charter.getUpdatedAt())
                .steps(stepDTOs)
                .build();
    }
}
