package com.smartflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.ai.GeminiAnalysisResult;
import com.smartflow.entity.Category;
import com.smartflow.entity.Incident;
import com.smartflow.entity.Priority;
import com.smartflow.entity.Service;
import com.smartflow.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GeminiService geminiService;

    private Incident testIncident;

    @BeforeEach
    void setUp() {
        Category category = Category.builder().id(1L).name("Réseau & Wi-Fi").build();
        Service service = Service.builder().id(1L).name("Support Informatique").build();

        testIncident = Incident.builder()
                .id(1L)
                .reference("INC-202610-0001")
                .title("Panne switch principal étage 2")
                .description("Le switch réseau ne répond plus, tout l'étage 2 est privé de réseau.")
                .location("Bâtiment A, Étage 2")
                .category(category)
                .service(service)
                .priority(Priority.HAUTE)
                .build();
    }

    @Test
    void analyzeIncident_WhenMockMode_ShouldReturnStructuredMockAnalysis() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "mock-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-1.5-flash");

        GeminiAnalysisResult result = geminiService.analyzeIncident(testIncident);

        assertNotNull(result);
        assertNotNull(result.getSummary());
        assertFalse(result.getPossibleCauses().isEmpty());
        assertEquals(Priority.HAUTE, result.getSuggestedPriority());
        assertNotNull(result.getPriorityReason());
        assertFalse(result.getRecommendations().isEmpty());
        assertFalse(result.getMissingInfo().isEmpty());
    }

    @Test
    void analyzeIncident_WhenRealApiSucceeds_ShouldParseJsonResponse() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-live-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-1.5-flash");

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"summary\\": \\"Panne matérielle du commutateur étage 2\\", \\"possibleCauses\\": [\\"Bloc d'alimentation HS\\", \\"Surtension électrique\\"], \\"suggestedPriority\\": \\"CRITIQUE\\", \\"priorityReason\\": \\"Impacte l'intégralité d'un étage opérationnel\\", \\"recommendations\\": [\\"Isoler l'alimentation\\", \\"Préparer un switch de secours\\"], \\"missingInfo\\": [\\"État des LEDs de façade\\"]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(geminiJson, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        GeminiAnalysisResult result = geminiService.analyzeIncident(testIncident);

        assertNotNull(result);
        assertEquals("Panne matérielle du commutateur étage 2", result.getSummary());
        assertEquals(2, result.getPossibleCauses().size());
        assertEquals("Bloc d'alimentation HS", result.getPossibleCauses().get(0));
        assertEquals(Priority.CRITIQUE, result.getSuggestedPriority());
        assertEquals("Impacte l'intégralité d'un étage opérationnel", result.getPriorityReason());
        assertEquals(2, result.getRecommendations().size());
        assertEquals(1, result.getMissingInfo().size());
    }

    @Test
    void analyzeIncident_WhenApiReturnsHttpError_ShouldThrowBusinessException() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-live-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-1.5-flash");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API Key"));

        BusinessException exception = assertThrows(BusinessException.class, () -> geminiService.analyzeIncident(testIncident));
        assertNotNull(exception.getMessage());
    }

    @Test
    void analyzeIncident_WhenApiTimesOut_ShouldThrowBusinessException() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-live-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-1.5-flash");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BusinessException exception = assertThrows(BusinessException.class, () -> geminiService.analyzeIncident(testIncident));
        assertNotNull(exception.getMessage());
    }
}
