package com.smartflow.service;

import com.smartflow.dto.service.ServiceRequestDTO;
import com.smartflow.dto.service.ServiceResponseDTO;
import com.smartflow.entity.Service;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.ServiceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getAllServices(Boolean activeOnly) {
        List<Service> services;
        if (Boolean.TRUE.equals(activeOnly)) {
            services = serviceRepository.findByActiveTrue();
        } else {
            services = serviceRepository.findAll();
        }
        return services.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceResponseDTO getServiceById(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        return mapToResponseDTO(service);
    }

    @Transactional
    public ServiceResponseDTO createService(ServiceRequestDTO request) {
        if (serviceRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new BusinessException("A service with name '" + request.getName() + "' already exists");
        }

        Service service = new Service(
                request.getName().trim(),
                request.getDescription(),
                request.getActive() != null ? request.getActive() : true
        );

        Service savedService = serviceRepository.save(service);
        return mapToResponseDTO(savedService);
    }

    @Transactional
    public ServiceResponseDTO updateService(Long id, ServiceRequestDTO request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

        if (serviceRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new BusinessException("A service with name '" + request.getName() + "' already exists");
        }

        service.setName(request.getName().trim());
        service.setDescription(request.getDescription());
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }

        Service updatedService = serviceRepository.save(service);
        return mapToResponseDTO(updatedService);
    }

    @Transactional
    public ServiceResponseDTO toggleServiceStatus(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

        service.setActive(!Boolean.TRUE.equals(service.getActive()));
        Service updatedService = serviceRepository.save(service);
        return mapToResponseDTO(updatedService);
    }

    private ServiceResponseDTO mapToResponseDTO(Service service) {
        return new ServiceResponseDTO(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getActive(),
                service.getCreatedAt()
        );
    }
}
