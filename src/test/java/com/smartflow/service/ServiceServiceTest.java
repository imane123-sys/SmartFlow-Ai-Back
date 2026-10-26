package com.smartflow.service;

import com.smartflow.dto.service.ServiceRequestDTO;
import com.smartflow.dto.service.ServiceResponseDTO;
import com.smartflow.entity.Service;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceService serviceService;

    private Service sampleService;

    @BeforeEach
    void setUp() {
        sampleService = new Service("IT Support", "Technical support team", true);
        sampleService.setId(1L);
        sampleService.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void createServiceShouldReturnCreatedService() {
        ServiceRequestDTO request = new ServiceRequestDTO("IT Support", "Technical support team", true);
        when(serviceRepository.existsByNameIgnoreCase("IT Support")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenReturn(sampleService);

        ServiceResponseDTO response = serviceService.createService(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("IT Support");
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void createServiceWithDuplicateNameShouldThrowBusinessException() {
        ServiceRequestDTO request = new ServiceRequestDTO("IT Support", "Description", true);
        when(serviceRepository.existsByNameIgnoreCase("IT Support")).thenReturn(true);

        assertThatThrownBy(() -> serviceService.createService(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getServiceByIdWhenNotFoundShouldThrowResourceNotFoundException() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.getServiceById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllServicesActiveOnlyShouldCallFindByActiveTrue() {
        when(serviceRepository.findByActiveTrue()).thenReturn(List.of(sampleService));

        List<ServiceResponseDTO> result = serviceService.getAllServices(true);

        assertThat(result).hasSize(1);
        verify(serviceRepository).findByActiveTrue();
    }

    @Test
    void toggleServiceStatusShouldInvertActiveFlag() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceResponseDTO result = serviceService.toggleServiceStatus(1L);

        assertThat(result.getActive()).isFalse();
        verify(serviceRepository).save(sampleService);
    }
}
