package com.pfa.fraud.comportements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Comportement cyclique de l'AgentSurveillanceTransaction.
 *
 * <p>Écoute en permanence les messages {@code REQUEST} portant l'ontologie
 * {@link AgentSurveillanceTransaction#ONTOLOGIE}. Pour chaque transaction
 * reçue :
 * <ol>
 *     <li>Valide et normalise le JSON entrant</li>
 *     <li>Découvre {@link AgentSurveillanceTransaction#SERVICE_ML}
 *         dynamiquement via le DF</li>
 *     <li>Forwarde la transaction normalisée à AgentAnalyseML</li>
 *     <li>Notifie l'agent d'audit (copie informelle)</li>
 * </ol>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class SurveillanceBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SurveillanceBehaviour.class);

    /** Champs minimums attendus dans le JSON de transaction. */
    private static final String[] CHAMPS_OBLIGATOIRES = {"montant", "marchand", "carte_fin"};

    private final ObjectMapper mapper = new ObjectMapper();
    private final AlerteService alerteService = new AlerteService();

    /**
     * @param agent agent porteur du comportement
     */
    public SurveillanceBehaviour(final Agent agent) {
        super(agent);
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
            final JsonNode brute = mapper.readTree(msg.getContent());
            final ObjectNode normalisee = normaliser(brute);
            alerteService.persisterTransaction(normalisee);
            forwarderVersML(normalisee, msg);
            notifierAudit(msg, normalisee);
        } catch (Exception e) {
            LOG.error("[Surveillance] Transaction invalide : {}", e.getMessage());
            repondreRefus(msg, e.getMessage());
        }
    }

    /**
     * Valide la présence des champs obligatoires et complète les champs manquants
     * (id, horodatage). Renvoie une copie normalisée.
     */
    private ObjectNode normaliser(final JsonNode brute) {
        if (brute == null || !brute.isObject()) {
            throw new IllegalArgumentException("contenu non JSON-objet");
        }
        for (String champ : CHAMPS_OBLIGATOIRES) {
            if (!brute.has(champ) || brute.get(champ).isNull()) {
                throw new IllegalArgumentException("champ manquant : " + champ);
            }
        }
        final ObjectNode out = brute.deepCopy();
        if (!out.has("id")) {
            out.put("id", UUID.randomUUID().toString());
        }
        if (!out.has("horodatage")) {
            out.put("horodatage", Instant.now().toString());
        }
        if (!out.has("banque_id")) {
            out.put("banque_id", "BNP");
        }
        return out;
    }

    /**
     * Cherche AgentAnalyseML dans le DF puis lui envoie la transaction.
     */
    private void forwarderVersML(final ObjectNode tx, final ACLMessage source) throws Exception {
        final AID cible = decouvrir(AgentSurveillanceTransaction.SERVICE_ML);
        if (cible == null) {
            LOG.warn("[Surveillance] AgentAnalyseML introuvable dans le DF.");
            return;
        }
        final ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(cible);
        req.setOntology(AgentSurveillanceTransaction.ONTOLOGIE);
        req.setConversationId("conv-" + tx.get("id").asText());
        req.setReplyWith("ml-" + System.currentTimeMillis());
        req.setContent(mapper.writeValueAsString(tx));
        myAgent.send(req);
        LOG.info("[Surveillance] Transaction {} forwardée à {} (montant={}).",
                tx.get("id").asText(), cible.getLocalName(), tx.get("montant").asText());
    }

    /**
     * Cherche un agent dans le DF par type de service, retourne le premier trouvé.
     */
    private AID decouvrir(final String typeService) {
        try {
            final DFAgentDescription gabarit = new DFAgentDescription();
            final ServiceDescription sd = new ServiceDescription();
            sd.setType(typeService);
            gabarit.addServices(sd);
            final DFAgentDescription[] res = DFService.search(myAgent, gabarit);
            return (res != null && res.length > 0) ? res[0].getName() : null;
        } catch (FIPAException e) {
            LOG.warn("[Surveillance] Échec DF.search('{}') : {}", typeService, e.getMessage());
            return null;
        }
    }

    /** Envoie une copie pour audit. */
    private void notifierAudit(final ACLMessage origine, final ObjectNode tx) {
        final AID auditeur = decouvrir("audit");
        if (auditeur == null) return;
        try {
            final ACLMessage copie = new ACLMessage(ACLMessage.INFORM);
            copie.addReceiver(auditeur);
            copie.setOntology("audit-fraude-ontologie");
            copie.setContent(mapper.writeValueAsString(tx));
            myAgent.send(copie);
        } catch (Exception ignored) { /* audit best-effort */ }
    }

    private void repondreRefus(final ACLMessage source, final String motif) {
        final ACLMessage refus = source.createReply();
        refus.setPerformative(ACLMessage.REFUSE);
        refus.setContent("{\"erreur\":\"" + motif.replace("\"", "'") + "\"}");
        myAgent.send(refus);
    }
}
