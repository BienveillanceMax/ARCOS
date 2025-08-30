package Memory.LongTermMemory.Qdrant;


import Memory.LongTermMemory.Models.*;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import Personality.Values.Entities.DimensionSchwartz;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Client pour communiquer avec Qdrant via son API REST.
 * Gère les opérations CRUD et la recherche vectorielle.
 */
public class QdrantClient
{

    private static final Logger logger = LoggerFactory.getLogger(QdrantClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * Constructeur avec configuration par défaut.
     */
    public QdrantClient(String host, int port) {
        this.baseUrl = String.format("http://%s:%d", host, port);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();

        logger.info("Qdrant client initialisé sur {}", baseUrl);
    }

    /**
     * Crée une collection avec les paramètres spécifiés.
     */
    public boolean createCollection(String collectionName, int vectorSize) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Configuration des vecteurs
            ObjectNode vectorsConfig = objectMapper.createObjectNode();
            vectorsConfig.put("size", vectorSize);
            vectorsConfig.put("distance", "Cosine");
            requestBody.set("vectors", vectorsConfig);

            // Configuration des index
            ObjectNode optimizersConfig = objectMapper.createObjectNode();
            optimizersConfig.put("default_segment_number", 2);
            requestBody.set("optimizers_config", optimizersConfig);

            String json = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName)
                    .put(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Collection '{}' créée avec succès", collectionName);
                    return true;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("Erreur lors de la création de la collection '{}': {} - {}",
                            collectionName, response.code(), responseBody);
                    return false;
                }
            }

        } catch (Exception e) {
            logger.error("Exception lors de la création de la collection '{}'", collectionName, e);
            return false;
        }
    }

    /**
     * Vérifie si une collection existe.
     */

    public boolean collectionExists(String collectionName) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de l'existence de la collection '{}'", collectionName, e);
            return false;
        }
    }


    /**
     * Insère ou met à jour une entrée dans la collection spécifiée.
     */
    public boolean upsertPoint(String collectionName, QdrantEntry entry) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode points = objectMapper.createArrayNode();

            ObjectNode point = objectMapper.createObjectNode();
            point.put("id", entry.getId());

            // Conversion de l'embedding en ArrayNode
            ArrayNode vectorArray = objectMapper.createArrayNode();
            for (float value : entry.getEmbedding()) {
                vectorArray.add(value);
            }
            point.set("vector", vectorArray);

            // Ajout des métadonnées (payload)
            point.set("payload", entry.getPayload());

            points.add(point);
            requestBody.set("points", points);

            String json = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points")
                    .put(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.debug("Point inséré avec succès dans '{}': {}", collectionName, entry.getId());
                    return true;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("Erreur lors de l'insertion du point dans '{}': {} - {}",
                            collectionName, response.code(), responseBody);
                    return false;
                }
            }

        } catch (Exception e) {
            logger.error("Exception lors de l'insertion du point dans '{}'", collectionName, e);
            return false;
        }
    }

    /**
     * Effectue une recherche vectorielle dans la collection spécifiée.
     */
    public <T> List<SearchResult<T>> search(String collectionName, float[] queryVector, int topK, Function<JsonNode, SearchResult<T>> resultParser) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Conversion du vecteur de requête
            ArrayNode vectorArray = objectMapper.createArrayNode();
            for (float value : queryVector) {
                vectorArray.add(value);
            }
            requestBody.set("vector", vectorArray);
            requestBody.put("limit", topK);
            requestBody.put("with_payload", true);
            requestBody.put("with_vector", false);

            String json = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points/search")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonNode responseJson = objectMapper.readTree(responseBody);
                    return parseSearchResults(responseJson, resultParser);
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("Erreur lors de la recherche dans '{}': {} - {}",
                            collectionName, response.code(), responseBody);
                    return new ArrayList<>();
                }
            }

        } catch (Exception e) {
            logger.error("Exception lors de la recherche dans '{}'", collectionName, e);
            return new ArrayList<>();
        }
    }

    public SearchResult<MemoryEntry> parseMemoryEntry(JsonNode resultNode) {
        double score = resultNode.get("score").asDouble();
        String id = resultNode.get("id").asText();
        JsonNode payloadNode = resultNode.get("payload");

        String content = payloadNode.get("content").asText();
        String subjectStr = payloadNode.get("subject").asText();
        double satisfaction = payloadNode.get("satisfaction").asDouble();
        String timestampStr = payloadNode.get("timestamp").asText();

        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);

        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setContent(content);
        entry.setSubject(Subject.fromString(subjectStr));
        entry.setSatisfaction(satisfaction);
        entry.setTimestamp(timestamp);
        return new SearchResult<>(entry, score);
    }

    public SearchResult<DesireEntry> parseDesireEntry(JsonNode resultNode) {
        double score = resultNode.get("score").asDouble();
        String id = resultNode.get("id").asText();
        JsonNode payloadNode = resultNode.get("payload");

        String label = payloadNode.get("label").asText();
        String description = payloadNode.get("description").asText();
        String reasoning = payloadNode.get("reasoning").asText();
        double intensity = payloadNode.get("intensity").asDouble();
        String opinionId = payloadNode.get("opinionId").asText();

        String createdAtStr = payloadNode.get("createdAt").asText();
        String lastUpdatedStr = payloadNode.get("lastUpdated").asText();

        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, TIMESTAMP_FORMATTER);
        LocalDateTime lastUpdated = LocalDateTime.parse(lastUpdatedStr, TIMESTAMP_FORMATTER);

        DesireEntry entry = new DesireEntry();
        entry.setId(id);
        entry.setLabel(label);
        entry.setDescription(description);
        entry.setReasoning(reasoning);
        entry.setIntensity(intensity);
        entry.setOpinionId(opinionId);
        entry.setCreatedAt(createdAt);
        entry.setLastUpdated(lastUpdated);

        return new SearchResult<>(entry,  score);
    }

    public SearchResult<OpinionEntry> parseOpinionEntry(JsonNode resultNode) {
        double score = resultNode.get("score").asDouble();
        String id = resultNode.get("id").asText();
        JsonNode payloadNode = resultNode.get("payload");

        String subject = payloadNode.get("subject").asText();
        String summary = payloadNode.get("summary").asText();
        String narrative = payloadNode.get("narrative").asText();
        double intensity = payloadNode.get("intensity").asDouble();
        double confidence = payloadNode.get("confidence").asDouble();
        double stability = payloadNode.get("stability").asDouble();
        String createdAtStr = payloadNode.get("createdAt").asText();
        String updatedAtStr = payloadNode.get("updatedAt").asText();

        // Parsing des timestamps
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, TIMESTAMP_FORMATTER);
        LocalDateTime updatedAt = LocalDateTime.parse(updatedAtStr, TIMESTAMP_FORMATTER);

        // Parsing de la liste des mémoires associées (optionnel)
        List<String> associatedMemories = new ArrayList<>();
        JsonNode associatedMemoriesNode = payloadNode.get("associatedMemories");
        if (associatedMemoriesNode != null && associatedMemoriesNode.isArray()) {
            for (JsonNode memoryIdNode : associatedMemoriesNode) {
                associatedMemories.add(memoryIdNode.asText());
            }
        }

        // Parsing de la dimension Schwartz (optionnel)
        DimensionSchwartz mainDimension = null;
        JsonNode mainDimensionNode = payloadNode.get("mainDimension");
        if (mainDimensionNode != null && !mainDimensionNode.isNull()) {
            try {
                mainDimension = DimensionSchwartz.valueOf(mainDimensionNode.asText());
            } catch (IllegalArgumentException e) {
                // Si la valeur n'est pas valide, on laisse null
                System.out.println("Dimension Schwartz invalide: " + mainDimensionNode.asText());
            }
        }

        OpinionEntry entry = new OpinionEntry();
        entry.setId(id);
        entry.setSubject(subject);
        entry.setSummary(summary);
        entry.setNarrative(narrative);
        entry.setPolarity(intensity);
        entry.setConfidence(confidence);
        entry.setStability(stability);
        entry.setAssociatedMemories(associatedMemories);
        entry.setCreatedAt(createdAt);
        entry.setUpdatedAt(updatedAt);
        entry.setMainDimension(mainDimension);

        return new SearchResult<>(entry, score);
    }

    /**
     * Parse les résultats de recherche depuis la réponse JSON de Qdrant.
     */
    private <T> List<SearchResult<T>> parseSearchResults(JsonNode responseJson, Function<JsonNode, SearchResult<T>> resultParser) throws IOException {
        List<SearchResult<T>> results = new ArrayList<>();

        JsonNode resultArray = responseJson.get("result");
        if (resultArray != null && resultArray.isArray()) {
            for (JsonNode resultNode : resultArray) {
                results.add(resultParser.apply(resultNode));
            }
        }
        return results;
    }

    /**
     * Récupère une entrée par son ID.
     */
    public <T> T getPoint(String collectionName, String pointId, Function<JsonNode, T> parser) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points/" + pointId)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonNode responseJson = objectMapper.readTree(responseBody);

                    JsonNode resultNode = responseJson.get("result");
                    if (resultNode != null) {
                        return parser.apply(resultNode);
                    }
                } else {
                    logger.error("Erreur lors de la récupération du point '{}' dans '{}': {}",
                            pointId, collectionName, response.code());
                }
            }
        } catch (Exception e) {
            logger.error("Exception lors de la récupération du point '{}' dans '{}'", pointId, collectionName, e);
        }

        return null;
    }

    /**
     * Supprime un point par son ID.
     */
    public boolean deletePoint(String collectionName, String pointId) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode pointsArray = objectMapper.createArrayNode();
            pointsArray.add(pointId);
            requestBody.set("points", pointsArray);

            String json = objectMapper.writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points/delete")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.debug("Point '{}' supprimé de '{}'", pointId, collectionName);
                    return true;
                } else {
                    logger.error("Erreur lors de la suppression du point '{}' dans '{}': {}",
                            pointId, collectionName, response.code());
                    return false;
                }
            }

        } catch (Exception e) {
            logger.error("Exception lors de la suppression du point '{}' dans '{}'", pointId, collectionName, e);
            return false;
        }
    }

    /**
     * Ferme le client HTTP.
     */
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        logger.info("Client Qdrant fermé");
    }
}