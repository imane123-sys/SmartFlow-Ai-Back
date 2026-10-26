package com.smartflow.controller;

import com.smartflow.dto.service.ServiceRequestDTO;
import com.smartflow.dto.service.ServiceResponseDTO;
import com.smartflow.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponseDTO>> getAllServices(
            @RequestParam(required = false) Boolean activeOnly) {
        return ResponseEntity.ok(serviceService.getAllServices(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.getServiceById(id));
    }

    @PostMapping
    public ResponseEntity<ServiceResponseDTO> createService(@Valid @RequestBody ServiceRequestDTO request) {
        ServiceResponseDTO createdService = serviceService.createService(request);
        return new ResponseEntity<>(createdService, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequestDTO request) {
        return ResponseEntity.ok(serviceService.updateService(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceResponseDTO> toggleServiceStatus(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.toggleServiceStatus(id));
    }
}
