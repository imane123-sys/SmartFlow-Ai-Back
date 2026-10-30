package com.smartflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartflow.dto.ai.GeminiAnalysisResult;
import com.smartflow.entity.Incident;
import com.smartflow.entity.Priority;
import com.smartflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:mock-key}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-1.5-flash}")
    private String model;

    public GeminiAnalysisResult analyzeIncident(Incident incident) {
        if (isMockMode()) {
            return generateMockAnalysis(incident);
        }

        try {
            String prompt = buildPrompt(incident);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> part = Collections.singletonMap("text", prompt);
            Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(part));
            requestBody.put("contents", Collections.singletonList(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return parseGeminiResponse(response.getBody(), incident);
        } catch (HttpStatusCodeException ex) {
            log.error("Erreur HTTP lors de l'appel à l'API Gemini : {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException("Échec de la communication avec le service d'IA (" + ex.getStatusCode().value() + "). Veuillez réessayer ultérieurement.");
        } catch (ResourceAccessException ex) {
            log.error("Délai d'attente ou erreur réseau lors de l'appel Gemini", ex);
            throw new BusinessException("Le service d'IA ne répond pas dans le délai imparti. Mode dégradé disponible.");
        } catch (Exception ex) {
            log.error("Erreur inattendue lors de l'analyse IA", ex);
            throw new BusinessException("Impossible de traiter l'analyse IA de l'incident : " + ex.getMessage());
        }
    }

    private boolean isMockMode() {
        return apiKey == null || apiKey.isBlank() || "mock-key".equalsIgnoreCase(apiKey.trim());
    }

    private String buildPrompt(Incident incident) {
        String categoryName = incident.getCategory() != null ? incident.getCategory().getName() : "Non spécifiée";
        String serviceName = incident.getService() != null ? incident.getService().getName() : "Non spécifié";
        String priorityDeclared = incident.getPriority() != null ? incident.getPriority().name() : "MOYENNE";
        String location = incident.getLocation() != null ? incident.getLocation() : "Non précisée";

        return """
                Tu es un expert senior en gestion d'incidents techniques et informatiques en entreprise.
                Analyse l'incident suivant de manière objective, concise et structurée.

                Détails de l'incident :
                - Titre : %s
                - Description : %s
                - Lieu : %s
                - Catégorie : %s
                - Service concerné : %s
                - Priorité déclarée initialement : %s

                Consignes impératives :
                - Réponds STRICTEMENT au format JSON valide, sans balises Markdown ni texte introductif.
                - Structure exacte attendue :
                {
                  "summary": "Synthèse claire et professionnelle du problème en 2 à 3 phrases.",
                  "possibleCauses": ["Cause probable 1", "Cause probable 2", "Cause probable 3"],
                  "suggestedPriority": "BASSE | MOYENNE | HAUTE | CRITIQUE",
                  "priorityReason": "Justification de la priorité suggérée selon l'impact organisationnel ou technique.",
                  "recommendations": ["Recommandation préalable 1", "Recommandation préalable 2"],
                  "missingInfo": ["Précision manquante 1", "Précision manquante 2"]
                }
                """.formatted(
                incident.getTitle(),
                incident.getDescription(),
                location,
                categoryName,
                serviceName,
                priorityDeclared
        );
    }

    private GeminiAnalysisResult parseGeminiResponse(String rawResponse, Incident incident) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new BusinessException("La réponse de l'IA est vide.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new BusinessException("Aucun résultat d'analyse renvoyé par l'IA.");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");
            if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
                throw new BusinessException("Contenu d'analyse introuvable dans la réponse de l'IA.");
            }

            String textContent = parts.get(0).path("text").asText("");
            String cleanJson = extractJsonText(textContent);

            JsonNode analysisNode = objectMapper.readTree(cleanJson);
            String summary = analysisNode.path("summary").asText("Analyse technique générée");

            List<String> possibleCauses = readStringList(analysisNode.path("possibleCauses"));
            String priorityStr = analysisNode.path("suggestedPriority").asText("MOYENNE").toUpperCase();
            Priority suggestedPriority = parsePriority(priorityStr, incident.getPriority());
            String priorityReason = analysisNode.path("priorityReason").asText("Évaluation basée sur les informations fournies");
            List<String> recommendations = readStringList(analysisNode.path("recommendations"));
            List<String> missingInfo = readStringList(analysisNode.path("missingInfo"));

            return GeminiAnalysisResult.builder()
                    .summary(summary)
                    .possibleCauses(possibleCauses)
                    .suggestedPriority(suggestedPriority)
                    .priorityReason(priorityReason)
                    .recommendations(recommendations)
                    .missingInfo(missingInfo)
                    .rawResponse(rawResponse)
                    .build();
        } catch (Exception ex) {
            log.error("Erreur de désérialisation du JSON de l'IA : {}", rawResponse, ex);
            throw new BusinessException("Le format de données retourné par l'IA n'est pas valide : " + ex.getMessage());
        }
    }

    private String extractJsonText(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    list.add(item.asText().trim());
                }
            }
        }
        return list;
    }

    private Priority parsePriority(String priorityStr, Priority defaultPriority) {
        try {
            return Priority.valueOf(priorityStr);
        } catch (Exception e) {
            return defaultPriority != null ? defaultPriority : Priority.MOYENNE;
        }
    }

    private GeminiAnalysisResult generateMockAnalysis(Incident incident) {
        String categoryName = incident.getCategory() != null ? incident.getCategory().getName() : "Technique";
        Priority defaultPrio = incident.getPriority() != null ? incident.getPriority() : Priority.MOYENNE;

        List<String> causes = Arrays.asList(
                "Dysfonctionnement matériel ou défaut d'alimentation sur l'équipement",
                "Erreur de configuration ou perte de connectivité avec le service " + categoryName,
                "Défaillance logicielle nécessitant un redémarrage ou une réinitialisation"
        );

        List<String> recommendations = Arrays.asList(
                "Effectuer une vérification visuelle des branchements et voyants",
                "Tester l'accès depuis un second poste de travail pour isoler le problème",
                "Consulter les journaux d'événements du service concerné"
        );

        List<String> missingInfo = Arrays.asList(
                "Le message d'erreur exact affiché à l'écran",
                "L'heure précise de survenue de l'incident et la liste des utilisateurs affectés"
        );

        return GeminiAnalysisResult.builder()
                .summary("Analyse automatisée de l'incident '" + incident.getTitle() + "' dans la catégorie " + categoryName + ". L'impact identifié requiert une intervention ciblée.")
                .possibleCauses(causes)
                .suggestedPriority(defaultPrio)
                .priorityReason("Priorité évaluée en tenant compte de la criticité opérationnelle du service " + categoryName)
                .recommendations(recommendations)
                .missingInfo(missingInfo)
                .rawResponse("{\"mock\": true, \"generatedBy\": \"SmartFlow-Mock-Engine\"}")
                .build();
    }
}
