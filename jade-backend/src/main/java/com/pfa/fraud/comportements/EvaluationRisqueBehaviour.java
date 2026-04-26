package com.pfa.fraud.comportements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pfa.fraud.agents.AgentEvaluateurRisque;
import com.pfa.fraud.agents.AgentSurveillanceTransaction;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Comportement de l'AgentEvaluateurRisque.
 *
 * <p>Reçoit un {@code INFORM} contenant {@code fraud_probability} de la
 * part d'AgentAnalyseML, applique 3 règles métier (montant, vélocité,
 * horaire), borne le score à [0, 1], et envoie une {@code PROPOSE}
 * (FIPA-ContractNet) à AgentBanque (découvert via DF, et privilégiant la
 * banque dont l'identifiant est mentionné dans la transaction).
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class EvaluationRisqueBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EvaluationRisqueBehaviour.class);

    /** Fenêtre temporelle (ms) utilisée pour la règle de vélocité (1h). */
    private static final long FENETRE_VELOCITE_MS = 60L * 60L * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Historique des horodatages de transactions par carte (pour la vélocité). */
    private final Map<String, Deque<Long>> historiqueParCarte = new HashMap<>();

    /**
     * @param agent agent porteur
     */
    public EvaluationRisqueBehaviour(final Agent agent) {
        super(agent);
    }

    @Override
    public void action() {
        final MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology(AgentSurveillanceTransaction.ONTOLOGIE));

        final ACLMessage msg = myAgent.receive(mt);
        if (msg == null) {
            block();
            return;
        }

        try {
            final JsonNode payload = mapper.readTree(msg.getContent());
            final JsonNode tx = payload.path("transaction");
            final double mlProba = payload.path("fraud_probability").asDouble(0.0);

            final EvaluationResultat eval = evaluer(tx, mlProba);

            final ObjectNode propose = mapper.createObjectNode();
            propose.set("transaction", tx);
            propose.put("score_ml", mlProba);
            propose.put("score_final", eval.scoreFinal);
            propose.set("regles_declenchees", eval.reglesDeclenchees);
            propose.put("raisonnement", eval.raisonnement);

            envoyerPropositionBanque(tx, propose);

            LOG.info("[Risque] tx={} ml={} -> final={} règles={}",
                    tx.path("id").asText(), mlProba, eval.scoreFinal, eval.reglesDeclenchees);

        } catch (Exception e) {
            LOG.error("[Risque] Échec évaluation : {}", e.getMessage(), e);
        }
    }

    /**
     * Applique les 3 règles métier au-dessus du score ML.
     */
    EvaluationResultat evaluer(final JsonNode tx, final double mlProba) {
        final ArrayNode regles = mapper.createArrayNode();
        final StringBuilder raison = new StringBuilder("ML=").append(mlProba);
        double bonus = 0.0;

        // R1 — Montant
        final double montant = tx.path("montant").asDouble(0.0);
        if (montant > AgentEvaluateurRisque.SEUIL_MONTANT_ELEVE) {
            bonus += AgentEvaluateurRisque.BONUS_MONTANT_ELEVE;
            regles.add("R1_MONTANT_ELEVE");
            raison.append(" | R1(montant>").append(AgentEvaluateurRisque.SEUIL_MONTANT_ELEVE)
                    .append(")+").append(AgentEvaluateurRisque.BONUS_MONTANT_ELEVE);
        }

        // R2 — Vélocité
        final String carte = tx.path("carte_fin").asText("????");
        final long now = horodatageMs(tx);
        final Deque<Long> hist = historiqueParCarte.computeIfAbsent(carte, k -> new ArrayDeque<>());
        // purge les entrées trop anciennes
        while (!hist.isEmpty() && (now - hist.peekFirst()) > FENETRE_VELOCITE_MS) {
            hist.pollFirst();
        }
        hist.addLast(now);
        if (hist.size() > AgentEvaluateurRisque.SEUIL_VELOCITE) {
            bonus += AgentEvaluateurRisque.BONUS_VELOCITE;
            regles.add("R2_VELOCITE");
            raison.append(" | R2(velocite=").append(hist.size()).append("/h)+")
                    .append(AgentEvaluateurRisque.BONUS_VELOCITE);
        }

        // R3 — Horaire
        final int heure = heureDe(tx);
        if (heure >= AgentEvaluateurRisque.HEURE_RISQUE_DEBUT
                && heure < AgentEvaluateurRisque.HEURE_RISQUE_FIN) {
            bonus += AgentEvaluateurRisque.BONUS_HORAIRE;
            regles.add("R3_HORAIRE_NUIT");
            raison.append(" | R3(heure=").append(heure).append("h)+")
                    .append(AgentEvaluateurRisque.BONUS_HORAIRE);
        }

        final double scoreFinal = Math.min(1.0, mlProba + bonus);
        raison.append(" => final=").append(String.format("%.4f", scoreFinal));
        return new EvaluationResultat(scoreFinal, regles, raison.toString());
    }

    private long horodatageMs(final JsonNode tx) {
        final String h = tx.path("horodatage").asText(null);
        if (h == null) return System.currentTimeMillis();
        try {
            return Instant.parse(h).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private int heureDe(final JsonNode tx) {
        final String h = tx.path("horodatage").asText(null);
        try {
            if (h != null) {
                return OffsetDateTime.parse(h).withOffsetSameInstant(ZoneOffset.UTC).getHour();
            }
        } catch (Exception ignored) { /* fallback */ }
        return OffsetDateTime.now(ZoneOffset.UTC).getHour();
    }

    /**
     * Sélectionne une banque cible (préfère celle dont l'id matche
     * la transaction, sinon la première dispo) et lui envoie un PROPOSE.
     */
    private void envoyerPropositionBanque(final JsonNode tx, final ObjectNode proposition) throws Exception {
        final String banqueAttendue = tx.path("banque_id").asText(null);
        final AID cible = decouvrirBanque(banqueAttendue);
        if (cible == null) {
            LOG.warn("[Risque] Aucune banque trouvée dans le DF.");
            return;
        }
        final ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
        propose.addReceiver(cible);
        propose.setOntology(AgentSurveillanceTransaction.ONTOLOGIE);
        propose.setProtocol("fipa-contract-net");
        propose.setConversationId("risque-" + tx.path("id").asText());
        propose.setReplyWith("prop-" + System.currentTimeMillis());
        propose.setContent(mapper.writeValueAsString(proposition));
        myAgent.send(propose);
    }

    private AID decouvrirBanque(final String banqueAttendue) {
        try {
            final DFAgentDescription gabarit = new DFAgentDescription();
            final ServiceDescription sd = new ServiceDescription();
            sd.setType("banque");
            gabarit.addServices(sd);
            final DFAgentDescription[] res = DFService.search(myAgent, gabarit);
            if (res == null || res.length == 0) return null;
            if (banqueAttendue != null) {
                for (DFAgentDescription d : res) {
                    if (d.getName().getLocalName().toLowerCase().contains(banqueAttendue.toLowerCase())) {
                        return d.getName();
                    }
                }
            }
            return res[0].getName();
        } catch (FIPAException e) {
            return null;
        }
    }

    /** Conteneur immuable du résultat d'évaluation. */
    static final class EvaluationResultat {
        final double scoreFinal;
        final ArrayNode reglesDeclenchees;
        final String raisonnement;

        EvaluationResultat(final double scoreFinal, final ArrayNode regles, final String raisonnement) {
            this.scoreFinal = scoreFinal;
            this.reglesDeclenchees = regles;
            this.raisonnement = raisonnement;
        }
    }
}
