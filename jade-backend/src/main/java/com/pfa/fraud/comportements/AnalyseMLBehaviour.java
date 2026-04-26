package com.pfa.fraud.comportements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pfa.fraud.agents.AgentSurveillanceTransaction;
import com.pfa.fraud.services.MLClientService;
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
 * Comportement de l'AgentAnalyseML : répond aux requêtes d'analyse ML.
 *
 * <p>Implémente la partie "responder" du protocole FIPA-Request : reçoit
 * une transaction ({@code REQUEST}), interroge le microservice ml-api,
 * et retourne :
 * <ul>
 *     <li>{@code INFORM} en cas de succès, contenant le score d'ensemble
 *         et les scores individuels (xgb, isolation forest)</li>
 *     <li>{@code FAILURE} en cas d'échec d'appel HTTP</li>
 * </ul>
 *
 * <p>En cas de succès, le résultat est aussi forwardé à
 * {@code AgentEvaluateurRisque} (découvert via DF) pour appliquer les
 * règles métier.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AnalyseMLBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AnalyseMLBehaviour.class);

    private static final String SERVICE_RISQUE = "evaluation-risque";

    private final MLClientService client;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param agent  agent porteur
     * @param client client HTTP vers ml-api
     */
    public AnalyseMLBehaviour(final Agent agent, final MLClientService client) {
        super(agent);
        this.client = client;
    }

    @Override
    public void action() {
        final MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchOntology(AgentSurveillanceTransaction.ONTOLOGIE));

        final ACLMessage msg = myAgent.receive(mt);
        if (msg == null) {
            block();
            return;
        }

        try {
            final JsonNode tx = mapper.readTree(msg.getContent());
            final MLClientService.PredictionResult pred = client.predict(tx);

            final ObjectNode resultat = mapper.createObjectNode();
            resultat.set("transaction", tx);
            resultat.put("fraud_probability", pred.fraudProbability());
            resultat.put("xgb_score", pred.xgbScore());
            resultat.put("if_score", pred.ifScore());

            // Réponse INFORM à l'émetteur (Surveillance)
            final ACLMessage inform = msg.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent(mapper.writeValueAsString(resultat));
            myAgent.send(inform);

            // Forward vers AgentEvaluateurRisque
            forwarderVersRisque(resultat);

            LOG.info("[AnalyseML] tx={} score={} (xgb={}, if={})",
                    tx.path("id").asText(), pred.fraudProbability(),
                    pred.xgbScore(), pred.ifScore());

        } catch (Exception e) {
            LOG.error("[AnalyseML] Échec prédiction : {}", e.getMessage());
            final ACLMessage failure = msg.createReply();
            failure.setPerformative(ACLMessage.FAILURE);
            failure.setContent("{\"erreur\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            myAgent.send(failure);
        }
    }

    private void forwarderVersRisque(final ObjectNode resultat) throws Exception {
        final AID risque = decouvrir(SERVICE_RISQUE);
        if (risque == null) {
            LOG.warn("[AnalyseML] AgentEvaluateurRisque introuvable dans le DF.");
            return;
        }
        final ACLMessage fwd = new ACLMessage(ACLMessage.INFORM);
        fwd.addReceiver(risque);
        fwd.setOntology(AgentSurveillanceTransaction.ONTOLOGIE);
        fwd.setContent(mapper.writeValueAsString(resultat));
        myAgent.send(fwd);
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
