package com.pfa.fraud.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Client HTTP du microservice Python {@code ml-api}.
 *
 * <p>Expose deux opérations :
 * <ul>
 *     <li>{@link #predict(JsonNode)} — POST {@code /predict} retournant
 *         le score de fraude</li>
 *     <li>{@link #health()} — GET {@code /health}</li>
 * </ul>
 *
 * <p>Le client utilise Apache HttpClient 5 avec timeouts stricts
 * (connexion {@value #CONNECT_TIMEOUT_MS} ms, réponse
 * {@value #RESPONSE_TIMEOUT_MS} ms) afin de ne pas bloquer un agent JADE
 * trop longtemps si le service ML est indisponible.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class MLClientService {

    private static final Logger LOG = LoggerFactory.getLogger(MLClientService.class);

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int RESPONSE_TIMEOUT_MS = 5_000;

    private final String baseUrl;
    private final CloseableHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param baseUrl URL de base du microservice ml-api (ex: {@code http://ml-api:5000})
     */
    public MLClientService(final String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        final RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(RESPONSE_TIMEOUT_MS))
                .build();
        this.http = HttpClients.custom().setDefaultRequestConfig(cfg).build();
    }

    /**
     * Appelle {@code POST /predict} pour obtenir le score de fraude.
     *
     * @param transaction transaction au format JSON (incluant montant, marchand, etc.)
     * @return un {@link PredictionResult} contenant les scores
     * @throws IOException si la requête HTTP échoue
     */
    public PredictionResult predict(final JsonNode transaction) throws IOException {
        final HttpPost post = new HttpPost(baseUrl + "/predict");
        post.setEntity(new StringEntity(transaction.toString(), ContentType.APPLICATION_JSON));

        return http.execute(post, response -> {
            final int status = response.getCode();
            final String body = EntityUtils.toString(response.getEntity());
            if (status != 200) {
                LOG.warn("[MLClient] Statut {} : {}", status, body);
                throw new IOException("ml-api a répondu " + status + ": " + body);
            }
            final JsonNode json = mapper.readTree(body);
            return new PredictionResult(
                    json.path("fraud_probability").asDouble(0.0),
                    json.path("xgb_score").asDouble(0.0),
                    json.path("if_score").asDouble(0.0)
            );
        });
    }

    /**
     * Appelle {@code GET /health} et retourne {@code true} si le service ML
     * est sain et que les modèles sont chargés.
     */
    public boolean health() {
        try {
            final HttpGet get = new HttpGet(baseUrl + "/health");
            return http.execute(get, response -> {
                if (response.getCode() != 200) return false;
                final JsonNode json = mapper.readTree(EntityUtils.toString(response.getEntity()));
                return "ok".equalsIgnoreCase(json.path("status").asText())
                        && json.path("models_loaded").asBoolean(false);
            });
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Résultat immuable d'une prédiction.
     *
     * @param fraudProbability score d'ensemble dans [0, 1]
     * @param xgbScore         score brut XGBoost
     * @param ifScore          score brut Isolation Forest
     */
    public record PredictionResult(double fraudProbability, double xgbScore, double ifScore) {
    }
}
