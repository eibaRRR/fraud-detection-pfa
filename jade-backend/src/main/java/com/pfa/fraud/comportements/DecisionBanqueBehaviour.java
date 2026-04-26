package com.pfa.fraud.comportements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pfa.fraud.agents.AgentAudit;
import com.pfa.fraud.agents.AgentBanque;
import com.pfa.fraud.agents.AgentSurveillanceTransaction;
import com.pfa.fraud.services.AlerteService;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comportement de décision finale de l'AgentBanque.
 *
 * <p>Reçoit un message {@code PROPOSE} d'AgentEvaluateurRisque dans le
 * cadre du protocole {@code fipa-contract-net}, applique les seuils :
 * <ul>
 *     <li>{@code score &gt;= } {@link AgentBanque#SEUIL_BLOQUER}
 *         → {@code ACCEPT_PROPOSAL} + action <b>BLOQUER</b></li>
 *     <li>{@code score &gt;= } {@link AgentBanque#SEUIL_SIGNALER}
 *         → {@code ACCEPT_PROPOSAL} + action <b>SIGNALER</b></li>
 *     <li>sinon → {@code REJECT_PROPOSAL} + action <b>APPROUVER</b></li>
 * </ul>
 *
 * <p>Pour chaque décision, le comportement :
 * <ul>
 *     <li>persiste l'alerte et la décision via {@link AlerteService}</li>
 *     <li>envoie une copie pour audit</li>
 *     <li>diffuse l'alerte par WebSocket vers le dashboard</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class DecisionBanqueBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DecisionBanqueBehaviour.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AlerteService alerteService = new AlerteService();

    /**
     * @param agent agent AgentBanque porteur du comportement
     */
    public DecisionBanqueBehaviour(final Agent agent) {
        super(agent);
    }

    @Override
    public void action() {
        final MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                MessageTemplate.MatchOntology(AgentSurveillanceTransaction.ONTOLOGIE));

        final ACLMessage msg = myAgent.receive(mt);
        if (msg == null) {
            block();
            return;
        }

        try {
            final JsonNode payload = mapper.readTree(msg.getContent());
            final double scoreFinal = payload.path("score_final").asDouble(0.0);
            final JsonNode tx = payload.path("transaction");
            final String txId = tx.path("id").asText("inconnu");

            final String action = decider(scoreFinal);
            final int performatifReponse = action.equals(AgentBanque.ACTION_APPROUVER)
                    ? ACLMessage.REJECT_PROPOSAL
                    : ACLMessage.ACCEPT_PROPOSAL;

            // Construction de la réponse FIPA
            final ObjectNode reponse = mapper.createObjectNode();
            reponse.put("transaction_id", txId);
            reponse.put("score", scoreFinal);
            reponse.put("action", action);
            reponse.put("banque", myAgent.getLocalName());
            reponse.put("raisonnement", payload.path("raisonnement").asText(""));
            reponse.set("regles_declenchees", payload.path("regles_declenchees"));
            // Infos transaction utiles pour le dashboard (montant, marchand, etc.)
            reponse.put("montant", tx.path("montant").asDouble(0.0));
            reponse.put("marchand", tx.path("marchand").asText(""));
            reponse.put("carte_fin", tx.path("carte_fin").asText(""));
            reponse.put("horodatage_tx", tx.path("horodatage").asText(""));

            final ACLMessage reply = msg.createReply();
            reply.setPerformative(performatifReponse);
            reply.setContent(mapper.writeValueAsString(reponse));
            myAgent.send(reply);

            // Persistance + audit + WebSocket
            alerteService.persisterDecision(txId, myAgent.getLocalName(),
                    action, scoreFinal, payload.path("raisonnement").asText(""),
                    payload.path("regles_declenchees"));

            alerteService.diffuserWebSocket(reponse);

            notifierAudit(reponse);

            LOG.info("[Banque:{}] tx={} score={} -> action={} ({})",
                    myAgent.getLocalName(), txId, scoreFinal, action,
                    ACLMessage.getPerformative(performatifReponse));

        } catch (Exception e) {
            LOG.error("[Banque:{}] Échec décision : {}", myAgent.getLocalName(), e.getMessage(), e);
        }
    }

    /**
     * Applique les seuils {@link AgentBanque#SEUIL_BLOQUER} et
     * {@link AgentBanque#SEUIL_SIGNALER}.
     *
     * @param score score final issu de l'évaluation
     * @return le code d'action ({@code BLOQUER}, {@code SIGNALER} ou {@code APPROUVER})
     */
    String decider(final double score) {
        if (score >= AgentBanque.SEUIL_BLOQUER) {
            return AgentBanque.ACTION_BLOQUER;
        }
        if (score >= AgentBanque.SEUIL_SIGNALER) {
            return AgentBanque.ACTION_SIGNALER;
        }
        return AgentBanque.ACTION_APPROUVER;
    }

    private void notifierAudit(final ObjectNode reponse) {
        final AID auditeur = decouvrir(AgentAudit.SERVICE);
        if (auditeur == null) return;
        try {
            final ACLMessage copie = new ACLMessage(ACLMessage.INFORM);
            copie.addReceiver(auditeur);
            copie.setOntology(AgentAudit.ONTOLOGIE_AUDIT);
            copie.setContent(mapper.writeValueAsString(reponse));
            myAgent.send(copie);
        } catch (Exception ignored) { /* best-effort */ }
    }

    private AID decouvrir(final String typeService) {
        try {
            final DFAgentDescription gabarit = new DFAgentDescription();
            final ServiceDescription sd = new ServiceDescription();
            sd.setType(typeService);
            gabarit.addServices(sd);
            final DFAgentDescription[] res = DFService.search(myAgent, gabarit);
            return (res != null && res.length > 0) ? res[0].getName() : null;
        } catch (FIPAException e) {
            return null;
        }
    }
}
