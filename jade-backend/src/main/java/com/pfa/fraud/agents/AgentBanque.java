package com.pfa.fraud.agents;

import com.pfa.fraud.comportements.DecisionBanqueBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent représentant une institution bancaire prenant la décision finale.
 *
 * <p>Une instance peut être lancée par banque simulée (BNP, SG, CA, ...).
 * L'agent répond aux propositions reçues via le protocole FIPA-ContractNet :
 *
 * <ul>
 *     <li>{@code score &gt;= } {@value #SEUIL_BLOQUER}
 *         → {@code ACCEPT_PROPOSAL} avec action <b>BLOQUER</b></li>
 *     <li>{@code score &gt;= } {@value #SEUIL_SIGNALER}
 *         → {@code ACCEPT_PROPOSAL} avec action <b>SIGNALER</b>
 *         (vérification humaine)</li>
 *     <li>sinon → {@code REJECT_PROPOSAL} avec action <b>APPROUVER</b></li>
 * </ul>
 *
 * <p>Chaque décision est :
 * <ul>
 *     <li>persistée en base de données dans la table {@code decisions_agents}</li>
 *     <li>transmise à {@link AgentAudit} pour traçabilité</li>
 *     <li>poussée en temps réel via WebSocket vers le dashboard</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentBanque extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentBanque.class);

    /** Type de service publié dans le DF. */
    public static final String SERVICE = "banque";

    /** Seuil de score à partir duquel la transaction est bloquée. */
    public static final double SEUIL_BLOQUER = 0.35;
    /** Seuil de score à partir duquel la transaction est signalée. */
    public static final double SEUIL_SIGNALER = 0.20;

    /** Code d'action : transaction bloquée. */
    public static final String ACTION_BLOQUER = "BLOQUER";
    /** Code d'action : transaction signalée pour vérification humaine. */
    public static final String ACTION_SIGNALER = "SIGNALER";
    /** Code d'action : transaction approuvée. */
    public static final String ACTION_APPROUVER = "APPROUVER";

    @Override
    protected void setup() {
        LOG.info("[Banque:{}] setup() — agent démarré.", getLocalName());
        enregistrerService();
        addBehaviour(new DecisionBanqueBehaviour(this));
    }

    private void enregistrerService() {
        final DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE);
        sd.setName(getLocalName());
        sd.addOntologies(AgentSurveillanceTransaction.ONTOLOGIE);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            LOG.info("[Banque:{}] Service '{}' enregistré dans le DF.", getLocalName(), SERVICE);
        } catch (FIPAException e) {
            LOG.error("[Banque:{}] Échec DF : {}", getLocalName(), e.getMessage(), e);
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[Banque:{}] Échec désinscription DF : {}", getLocalName(), e.getMessage());
        }
        LOG.info("[Banque:{}] Arrêt.", getLocalName());
    }
}
