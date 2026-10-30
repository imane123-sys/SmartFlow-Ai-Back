package com.smartflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.ai.AiAnalysisResponseDTO;
import com.smartflow.dto.ai.GeminiAnalysisResult;
import com.smartflow.entity.AiAnalysis;
import com.smartflow.entity.Incident;
import com.smartflow.entity.IncidentHistory;
import com.smartflow.entity.IncidentStatus;
import com.smartflow.entity.Role;
import com.smartflow.entity.User;
import org.springframework.security.access.AccessDeniedException;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.AiAnalysisRepository;
import com.smartflow.repository.IncidentHistoryRepository;
import com.smartflow.repository.IncidentRepository;
import com.smartflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiAnalysisResponseDTO analyzeIncident(Long incidentId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = getIncidentEntity(incidentId);

        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Seuls les Managers et Administrateurs peuvent déclencher l'analyse IA");
        }

        if (currentUser.getRole() == Role.MANAGER && currentUser.getService() != null && incident.getService() != null) {
            if (!currentUser.getService().getId().equals(incident.getService().getId())) {
                throw new AccessDeniedException("Vous ne pouvez analyser que les incidents rattachés à votre service");
            }
        }

        if (incident.getStatus() == IncidentStatus.CLOTURE || incident.getStatus() == IncidentStatus.REJETE) {
            throw new BusinessException("Impossible de lancer une analyse IA sur un incident clôturé ou rejeté");
        }

        GeminiAnalysisResult analysisResult = geminiService.analyzeIncident(incident);

        String previousStatus = incident.getStatus().name();
        if (incident.getStatus() == IncidentStatus.NOUVEAU) {
            incident.setStatus(IncidentStatus.EN_ANALYSE);
            incident.setUpdatedAt(LocalDateTime.now());
            incidentRepository.save(incident);
        }

        String causesJson = serializeList(analysisResult.getPossibleCauses());
        String recommendationsJson = serializeList(analysisResult.getRecommendations());
        String missingInfoJson = serializeList(analysisResult.getMissingInfo());

        AiAnalysis aiAnalysis = AiAnalysis.builder()
                .incident(incident)
                .requestedBy(currentUser)
                .summary(analysisResult.getSummary())
                .possibleCausesJson(causesJson)
                .suggestedPriority(analysisResult.getSuggestedPriority())
                .priorityReason(analysisResult.getPriorityReason())
                .recommendationsJson(recommendationsJson)
                .missingInfoJson(missingInfoJson)
                .rawResponse(analysisResult.getRawResponse())
                .build();

        AiAnalysis savedAnalysis = aiAnalysisRepository.save(aiAnalysis);

        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .actionType("AI_ANALYSIS")
                .oldValue(previousStatus)
                .newValue(incident.getStatus().name())
                .comment("Diagnostic IA effectué. Priorité suggérée : " + analysisResult.getSuggestedPriority())
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        log.info("Analyse IA réalisée avec succès pour l'incident {} par {}", incident.getReference(), currentUser.getEmail());

        return mapToResponseDTO(savedAnalysis);
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisResponseDTO> getAnalysesByIncident(Long incidentId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        Incident incident = getIncidentEntity(incidentId);

        checkIncidentReadAccess(incident, currentUser);

        return aiAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponseDTO getAnalysisById(Long analysisId, String currentUserEmail) {
        User currentUser = getUserByEmail(currentUserEmail);
        AiAnalysis analysis = aiAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse IA non trouvée avec l'id : " + analysisId));

        checkIncidentReadAccess(analysis.getIncident(), currentUser);

        return mapToResponseDTO(analysis);
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
        throw new AccessDeniedException("Accès non autorisé aux analyses de cet incident");
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));
    }

    private Incident getIncidentEntity(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident non trouvé avec l'id : " + id));
    }

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Erreur de sérialisation d'une liste JSON", e);
            return "[]";
        }
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Erreur de désérialisation d'une liste JSON : {}", json, e);
            return Collections.emptyList();
        }
    }

    private AiAnalysisResponseDTO mapToResponseDTO(AiAnalysis analysis) {
        return AiAnalysisResponseDTO.builder()
                .id(analysis.getId())
                .incidentId(analysis.getIncident().getId())
                .incidentReference(analysis.getIncident().getReference())
                .requestedById(analysis.getRequestedBy().getId())
                .requestedByName(analysis.getRequestedBy().getFullName())
                .summary(analysis.getSummary())
                .possibleCauses(deserializeList(analysis.getPossibleCausesJson()))
                .suggestedPriority(analysis.getSuggestedPriority())
                .priorityReason(analysis.getPriorityReason())
                .recommendations(deserializeList(analysis.getRecommendationsJson()))
                .missingInfo(deserializeList(analysis.getMissingInfoJson()))
                .createdAt(analysis.getCreatedAt())
                .build();
    }
}
