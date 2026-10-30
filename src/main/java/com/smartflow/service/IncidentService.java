package com.smartflow.service;

import com.smartflow.dto.incident.AttachmentResponseDTO;
import com.smartflow.dto.incident.IncidentAssignDTO;
import com.smartflow.dto.incident.IncidentCreateRequestDTO;
import com.smartflow.dto.incident.IncidentHistoryResponseDTO;
import com.smartflow.dto.incident.IncidentResponseDTO;
import com.smartflow.dto.incident.IncidentStatusUpdateDTO;
import com.smartflow.dto.incident.IncidentUpdateRequestDTO;
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
import com.smartflow.repository.IncidentSpecification;
import com.smartflow.repository.ServiceRepository;
import com.smartflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public IncidentResponseDTO createIncident(IncidentCreateRequestDTO request, String currentUserEmail) {
        User reporter = getUserByEmail(currentUserEmail);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'id : " + request.getCategoryId()));

        if (Boolean.FALSE.equals(category.getActive())) {
            throw new BusinessException("Impossible de créer un incident avec une catégorie inactive");
        }

        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'id : " + request.getServiceId()));

        if (Boolean.FALSE.equals(service.getActive())) {
            throw new BusinessException("Impossible de créer un incident avec un service inactif");
        }

        String reference = generateReference();

        Incident incident = Incident.builder()
                .reference(reference)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .location(request.getLocation() != null ? request.getLocation().trim() : null)
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MOYENNE)
                .status(IncidentStatus.NOUVEAU)
                .category(category)
                .service(service)
                .reporter(reporter)
                .build();

        Incident savedIncident = incidentRepository.save(incident);

        IncidentHistory history = IncidentHistory.builder()
                .incident(savedIncident)
                .actionType("CREATION")
                .oldValue(null)
                .newValue(IncidentStatus.NOUVEAU.name())
                .comment("Déclaration initiale de l'incident")
                .operator(reporter)
                .build();

        historyRepository.save(history);
        log.info("Incident créé avec succès avec la référence {} par {}", reference, reporter.getEmail());

        return mapToResponseDTO(savedIncident);
    }

    @Transactional(readOnly = true)
    public IncidentResponseDTO getIncidentById(Long id, String currentUserEmail) {
        Incident incident = getIncidentEntity(id);
        User currentUser = getUserByEmail(currentUserEmail);
        verifyViewAccess(incident, currentUser);
        return mapToResponseDTO(incident);
    }

    @Transactional(readOnly = true)
    public IncidentResponseDTO getIncidentByReference(String reference, String currentUserEmail) {
        Incident incident = incidentRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Incident non trouvé avec la référence : " + reference));
        User currentUser = getUserByEmail(currentUserEmail);
        verifyViewAccess(incident, currentUser);
        return mapToResponseDTO(incident);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponseDTO> searchIncidents(
            IncidentStatus status,
            Priority priority,
            Long serviceId,
            Long categoryId,
            String search,
            Pageable pageable,
            String currentUserEmail) {

        User currentUser = getUserByEmail(currentUserEmail);

        Long filterReporterId = null;
        Long filterAssignedToId = null;

        if (currentUser.getRole() == Role.EMPLOYEE) {
            filterReporterId = currentUser.getId();
        } else if (currentUser.getRole() == Role.AGENT) {
            // Un agent peut voir ses incidents ou chercher par statut
            // Si aucun agent n'est spécifié, il filtre selon ses assignations si pertinent ou consulte sa file
        }

        Specification<Incident> spec = IncidentSpecification.withFilters(
                status,
                priority,
                serviceId,
                categoryId,
                filterReporterId,
                filterAssignedToId,
                search
        );

        return incidentRepository.findAll(spec, pageable).map(this::mapToResponseDTO);
    }

    @Transactional
    public IncidentResponseDTO updateStatus(Long id, IncidentStatusUpdateDTO request, String currentUserEmail) {
        Incident incident = getIncidentEntity(id);
        User operator = getUserByEmail(currentUserEmail);

        verifyStatusUpdateAccess(incident, operator);

        IncidentStatus oldStatus = incident.getStatus();
        IncidentStatus newStatus = request.getStatus();

        if (oldStatus == newStatus) {
            return mapToResponseDTO(incident);
        }

        if (newStatus == IncidentStatus.RESOLU && incident.getResolvedAt() == null) {
            incident.setResolvedAt(LocalDateTime.now());
        }

        if (newStatus == IncidentStatus.CLOTURE && incident.getClosedAt() == null) {
            incident.setClosedAt(LocalDateTime.now());
        }

        incident.setStatus(newStatus);
        Incident updatedIncident = incidentRepository.save(incident);

        IncidentHistory history = IncidentHistory.builder()
                .incident(updatedIncident)
                .actionType("STATUT_CHANGE")
                .oldValue(oldStatus.name())
                .newValue(newStatus.name())
                .comment(request.getComment())
                .operator(operator)
                .build();

        historyRepository.save(history);
        log.info("Statut de l'incident {} mis à jour de {} à {} par {}", incident.getReference(), oldStatus, newStatus, operator.getEmail());

        return mapToResponseDTO(updatedIncident);
    }

    @Transactional
    public IncidentResponseDTO assignIncident(Long id, IncidentAssignDTO request, String currentUserEmail) {
        Incident incident = getIncidentEntity(id);
        User operator = getUserByEmail(currentUserEmail);

        if (operator.getRole() != Role.ADMIN && operator.getRole() != Role.MANAGER) {
            throw new BusinessException("Seuls les managers ou administrateurs peuvent assigner un incident");
        }

        User agent = userRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé avec l'id : " + request.getAgentId()));

        if (Boolean.FALSE.equals(agent.getActive())) {
            throw new BusinessException("Impossible d'assigner l'incident à un utilisateur inactif");
        }

        if (agent.getRole() != Role.AGENT && agent.getRole() != Role.MANAGER && agent.getRole() != Role.ADMIN) {
            throw new BusinessException("L'utilisateur sélectionné n'a pas un rôle compatible pour une intervention");
        }

        User previousAgent = incident.getAssignedTo();
        incident.setAssignedTo(agent);

        if (incident.getStatus() == IncidentStatus.NOUVEAU || incident.getStatus() == IncidentStatus.EN_ATTENTE_VALIDATION) {
            incident.setStatus(IncidentStatus.ASSIGNE);
        }

        Incident updatedIncident = incidentRepository.save(incident);

        String oldValue = previousAgent != null ? previousAgent.getFullName() : "Non assigné";
        String newValue = agent.getFullName();

        IncidentHistory history = IncidentHistory.builder()
                .incident(updatedIncident)
                .actionType("ASSIGNATION")
                .oldValue(oldValue)
                .newValue(newValue)
                .comment(request.getComment())
                .operator(operator)
                .build();

        historyRepository.save(history);
        log.info("Incident {} assigné à {} par {}", incident.getReference(), agent.getEmail(), operator.getEmail());

        return mapToResponseDTO(updatedIncident);
    }

    @Transactional
    public IncidentResponseDTO updateIncident(Long id, IncidentUpdateRequestDTO request, String currentUserEmail) {
        Incident incident = getIncidentEntity(id);
        User currentUser = getUserByEmail(currentUserEmail);

        boolean isReporter = incident.getReporter().getId().equals(currentUser.getId());
        boolean isPrivileged = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;

        if (!isPrivileged && (!isReporter || incident.getStatus() != IncidentStatus.NOUVEAU)) {
            throw new BusinessException("Vous n'êtes pas autorisé à modifier les détails de cet incident");
        }

        if (incident.getStatus() == IncidentStatus.RESOLU || incident.getStatus() == IncidentStatus.CLOTURE) {
            throw new BusinessException("Impossible de modifier un incident déjà résolu ou clôturé");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'id : " + request.getCategoryId()));

        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'id : " + request.getServiceId()));

        incident.setTitle(request.getTitle().trim());
        incident.setDescription(request.getDescription().trim());
        incident.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);
        if (request.getPriority() != null) {
            incident.setPriority(request.getPriority());
        }
        incident.setCategory(category);
        incident.setService(service);

        Incident updatedIncident = incidentRepository.save(incident);

        IncidentHistory history = IncidentHistory.builder()
                .incident(updatedIncident)
                .actionType("MODIFICATION")
                .oldValue(null)
                .newValue("Champs modifiés")
                .comment("Mise à jour des informations générales de l'incident")
                .operator(currentUser)
                .build();

        historyRepository.save(history);
        return mapToResponseDTO(updatedIncident);
    }

    @Transactional(readOnly = true)
    public List<IncidentHistoryResponseDTO> getIncidentHistory(Long id, String currentUserEmail) {
        Incident incident = getIncidentEntity(id);
        User currentUser = getUserByEmail(currentUserEmail);
        verifyViewAccess(incident, currentUser);

        return historyRepository.findByIncidentIdOrderByCreatedAtDesc(id).stream()
                .map(this::mapHistoryToDTO)
                .collect(Collectors.toList());
    }

    private synchronized String generateReference() {
        LocalDateTime now = LocalDateTime.now();
        String prefix = String.format("INC-%04d%02d-", now.getYear(), now.getMonthValue());
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

        long count = incidentRepository.countByCreatedAtBetween(startOfMonth, endOfMonth) + 1;
        String ref = String.format("%s%04d", prefix, count);
        while (incidentRepository.existsByReference(ref)) {
            count++;
            ref = String.format("%s%04d", prefix, count);
        }
        return ref;
    }

    private Incident getIncidentEntity(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident non trouvé avec l'id : " + id));
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email : " + email));
    }

    private void verifyViewAccess(Incident incident, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER) {
            return;
        }

        if (user.getRole() == Role.EMPLOYEE && !incident.getReporter().getId().equals(user.getId())) {
            throw new BusinessException("Vous n'êtes pas autorisé à consulter cet incident");
        }
    }

    private void verifyStatusUpdateAccess(Incident incident, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER) {
            return;
        }

        if (user.getRole() == Role.AGENT) {
            boolean isAssigned = incident.getAssignedTo() != null && incident.getAssignedTo().getId().equals(user.getId());
            if (isAssigned) {
                return;
            }
        }

        throw new BusinessException("Vous n'avez pas les autorisations nécessaires pour modifier le statut de cet incident");
    }

    public IncidentResponseDTO mapToResponseDTO(Incident incident) {
        List<AttachmentResponseDTO> attachments = incident.getAttachments() != null
                ? incident.getAttachments().stream().map(this::mapAttachmentToDTO).collect(Collectors.toList())
                : List.of();

        return IncidentResponseDTO.builder()
                .id(incident.getId())
                .reference(incident.getReference())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .location(incident.getLocation())
                .priority(incident.getPriority())
                .status(incident.getStatus())
                .categoryId(incident.getCategory() != null ? incident.getCategory().getId() : null)
                .categoryName(incident.getCategory() != null ? incident.getCategory().getName() : null)
                .serviceId(incident.getService() != null ? incident.getService().getId() : null)
                .serviceName(incident.getService() != null ? incident.getService().getName() : null)
                .reporterId(incident.getReporter() != null ? incident.getReporter().getId() : null)
                .reporterName(incident.getReporter() != null ? incident.getReporter().getFullName() : null)
                .reporterEmail(incident.getReporter() != null ? incident.getReporter().getEmail() : null)
                .assignedToId(incident.getAssignedTo() != null ? incident.getAssignedTo().getId() : null)
                .assignedToName(incident.getAssignedTo() != null ? incident.getAssignedTo().getFullName() : null)
                .assignedToEmail(incident.getAssignedTo() != null ? incident.getAssignedTo().getEmail() : null)
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .resolvedAt(incident.getResolvedAt())
                .closedAt(incident.getClosedAt())
                .attachments(attachments)
                .build();
    }

    public AttachmentResponseDTO mapAttachmentToDTO(com.smartflow.entity.Attachment attachment) {
        return AttachmentResponseDTO.builder()
                .id(attachment.getId())
                .originalName(attachment.getOriginalName())
                .storedName(attachment.getStoredName())
                .mimeType(attachment.getMimeType())
                .fileSize(attachment.getFileSize())
                .storagePath(attachment.getStoragePath())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    public IncidentHistoryResponseDTO mapHistoryToDTO(IncidentHistory history) {
        return IncidentHistoryResponseDTO.builder()
                .id(history.getId())
                .actionType(history.getActionType())
                .oldValue(history.getOldValue())
                .newValue(history.getNewValue())
                .comment(history.getComment())
                .operatorId(history.getOperator() != null ? history.getOperator().getId() : null)
                .operatorName(history.getOperator() != null ? history.getOperator().getFullName() : null)
                .operatorEmail(history.getOperator() != null ? history.getOperator().getEmail() : null)
                .createdAt(history.getCreatedAt())
                .build();
    }
}
