package com.pfa.fraud.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pfa.fraud.agents.AgentBanque;
import com.pfa.fraud.config.DatabaseConfig;
import com.pfa.fraud.modeles.Alerte;
import com.pfa.fraud.modeles.DecisionAgent;
import com.pfa.fraud.modeles.Transaction;
import com.pfa.fraud.websocket.AlerteWebSocketServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service applicatif partagé par les agents pour :
 * <ul>
 *     <li>Persister la décision finale et l'alerte associée
 *         (tables {@code alertes} et {@code decisions_agents})</li>
 *     <li>Diffuser le résultat aux clients WebSocket connectés</li>
 * </ul>
 *
 * <p>La persistance et le broadcast sont best-effort : une panne DB ne
 * doit jamais empêcher la diffusion temps réel, et inversement.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AlerteService {

    private static final Logger LOG = LoggerFactory.getLogger(AlerteService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Persiste l'alerte (si action != APPROUVER) et la décision agent.
     *
     * @param transactionId    UUID de la transaction
     * @param agentId          nom local de l'agent ayant pris la décision
     * @param action           code d'action ({@code BLOQUER}, {@code SIGNALER}, {@code APPROUVER})
     * @param scoreFinal       score de risque final
     * @param raisonnement     justification textuelle
     * @param reglesDeclenchees JSON des règles déclenchées
     */
    public void persisterDecision(final String transactionId,
                                   final String agentId,
                                   final String action,
                                   final double scoreFinal,
                                   final String raisonnement,
                                   final JsonNode reglesDeclenchees) {
        EntityManager em = null;
        EntityTransaction tx = null;
        try {
            em = DatabaseConfig.em();
            tx = em.getTransaction();
            tx.begin();

            UUID alerteId = null;
            if (!AgentBanque.ACTION_APPROUVER.equals(action)) {
                final Alerte a = new Alerte();
                a.setTransactionId(safeUuid(transactionId));
                a.setAgentDeclencheur(agentId);
                a.setScoreRisque(BigDecimal.valueOf(scoreFinal));
                a.setReglesDeclenchees(reglesDeclenchees != null ? reglesDeclenchees.toString() : "[]");
                a.setActionEffectuee(action);
                em.persist(a);
                alerteId = a.getId();
            }

            final DecisionAgent d = new DecisionAgent();
            d.setAlerteId(alerteId);
            d.setAgentId(agentId);
            d.setDecision(action);
            d.setRaisonnement(raisonnement);
            d.setConfiance(BigDecimal.valueOf(scoreFinal));
            d.setCreeLe(OffsetDateTime.now());
            em.persist(d);

            tx.commit();
        } catch (Exception e) {
            LOG.error("[AlerteService] Échec persistance : {}", e.getMessage(), e);
            if (tx != null && tx.isActive()) {
                try { tx.rollback(); } catch (Exception ignored) { /* */ }
            }
        } finally {
            if (em != null && em.isOpen()) em.close();
        }
    }

    /**
     * Diffuse l'alerte au format JSON via le WebSocket.
     *
     * @param payload contenu JSON à diffuser
     */
    public void diffuserWebSocket(final ObjectNode payload) {
        try {
            final ObjectNode enveloppe = MAPPER.createObjectNode();
            enveloppe.put("type", "ALERTE");
            enveloppe.put("horodatage", OffsetDateTime.now().toString());
            enveloppe.set("payload", payload);
            AlerteWebSocketServer.broadcast(MAPPER.writeValueAsString(enveloppe));
        } catch (Exception e) {
            LOG.warn("[AlerteService] Échec broadcast WS : {}", e.getMessage());
        }
    }

    /**
     * Persiste (ou met à jour) une transaction reçue par AgentSurveillance.
     * Idempotent : si la transaction existe déjà (même UUID), elle est ignorée.
     *
     * @param tx JSON normalisé (id, montant, marchand, carte_fin, banque_id, horodatage)
     */
    public void persisterTransaction(final JsonNode tx) {
        if (tx == null) return;
        final UUID id = safeUuid(tx.path("id").asText(null));
        if (id == null) return;
        EntityManager em = null;
        EntityTransaction etx = null;
        try {
            em = DatabaseConfig.em();
            if (em.find(Transaction.class, id) != null) return; // déjà présent
            etx = em.getTransaction();
            etx.begin();
            final Transaction t = new Transaction();
            t.setId(id);
            t.setMontant(new BigDecimal(tx.path("montant").asText("0")));
            t.setMarchand(tx.path("marchand").asText(null));
            t.setCarteFin(tx.path("carte_fin").asText(null));
            t.setBanqueId(tx.path("banque_id").asText("BNP"));
            final String hd = tx.path("horodatage").asText(null);
            if (hd != null) {
                try { t.setHorodatage(OffsetDateTime.parse(hd)); }
                catch (Exception ignored) { /* fallback @PrePersist */ }
            }
            em.persist(t);
            etx.commit();
        } catch (Exception e) {
            LOG.warn("[AlerteService] Échec persistance transaction {} : {}", id, e.getMessage());
            if (etx != null && etx.isActive()) {
                try { etx.rollback(); } catch (Exception ignored) { /* */ }
            }
        } finally {
            if (em != null && em.isOpen()) em.close();
        }
    }

    private UUID safeUuid(final String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
