package com.smartflow.service;

import com.smartflow.dto.category.CategoryRequestDTO;
import com.smartflow.dto.category.CategoryResponseDTO;
import com.smartflow.entity.Category;
import com.smartflow.exception.BusinessException;
import com.smartflow.exception.ResourceNotFoundException;
import com.smartflow.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = new Category("Network", "Network and connectivity issues", true);
        sampleCategory.setId(1L);
        sampleCategory.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void createCategoryShouldReturnCreatedCategory() {
        CategoryRequestDTO request = new CategoryRequestDTO("Network", "Network and connectivity issues", true);
        when(categoryRepository.existsByNameIgnoreCase("Network")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(sampleCategory);

        CategoryResponseDTO response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Network");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategoryWithDuplicateNameShouldThrowBusinessException() {
        CategoryRequestDTO request = new CategoryRequestDTO("Network", "Description", true);
        when(categoryRepository.existsByNameIgnoreCase("Network")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getCategoryByIdWhenNotFoundShouldThrowResourceNotFoundException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllCategoriesActiveOnlyShouldCallFindByActiveTrue() {
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(sampleCategory));

        List<CategoryResponseDTO> result = categoryService.getAllCategories(true);

        assertThat(result).hasSize(1);
        verify(categoryRepository).findByActiveTrue();
    }

    @Test
    void toggleCategoryStatusShouldInvertActiveFlag() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDTO result = categoryService.toggleCategoryStatus(1L);

        assertThat(result.getActive()).isFalse();
        verify(categoryRepository).save(sampleCategory);
    }
}
