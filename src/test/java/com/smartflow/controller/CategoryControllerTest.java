package com.smartflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.category.CategoryRequestDTO;
import com.smartflow.dto.category.CategoryResponseDTO;
import com.smartflow.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @Test
    void getAllCategoriesShouldReturn200AndList() throws Exception {
        CategoryResponseDTO response = new CategoryResponseDTO(1L, "Network", "Desc", true, LocalDateTime.now());
        when(categoryService.getAllCategories(null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Network"));
    }

    @Test
    void createCategoryWithValidDataShouldReturn201() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO("Network", "Desc", true);
        CategoryResponseDTO response = new CategoryResponseDTO(1L, "Network", "Desc", true, LocalDateTime.now());
        when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Network"));
    }

    @Test
    void createCategoryWithBlankNameShouldReturn400() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO("", "Desc", true);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details").isArray());
    }
}
