package com.pfa.fraud.agents;

import com.pfa.fraud.comportements.SurveillanceBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent porte d'entrée des transactions du système.
 *
 * <p>Reçoit en continu (CyclicBehaviour) les transactions envoyées par
 * le simulateur ou par toute source externe via messages ACL. Pour chaque
 * transaction reçue, l'agent :
 * <ol>
 *     <li>Valide la conformité du JSON (champs obligatoires, types)</li>
 *     <li>Normalise les champs (montant, horodatage, identifiant carte)</li>
 *     <li>Découvre dynamiquement {@code AgentAnalyseML} via le DF</li>
 *     <li>Lui envoie un message {@code REQUEST} (ontologie
 *         {@value #ONTOLOGIE}) pour déclencher l'analyse ML</li>
 * </ol>
 *
 * <p>Cet agent ne prend aucune décision métier : il sert uniquement
 * d'interface d'ingestion.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentSurveillanceTransaction extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentSurveillanceTransaction.class);

    /** Ontologie commune utilisée par tous les agents du système. */
    public static final String ONTOLOGIE = "analyse-fraude-ontologie";

    /** Type de service exposé dans le DF par cet agent. */
    public static final String SERVICE = "surveillance-transactions";

    /** Type de service consommé (recherché via DF) : analyse ML. */
    public static final String SERVICE_ML = "analyse-ml";

    @Override
    protected void setup() {
        LOG.info("[Surveillance] setup() — agent démarré.");
        enregistrerService();
        addBehaviour(new SurveillanceBehaviour(this));
    }

    /**
     * Enregistre le service {@value #SERVICE} dans le Directory Facilitator.
     */
    private void enregistrerService() {
        final DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE);
        sd.setName(getLocalName());
        sd.addOntologies(ONTOLOGIE);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            LOG.info("[Surveillance] Service '{}' enregistré dans le DF.", SERVICE);
        } catch (FIPAException e) {
            LOG.error("[Surveillance] Échec DF : {}", e.getMessage(), e);
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[Surveillance] Échec désinscription DF : {}", e.getMessage());
        }
        LOG.info("[Surveillance] Arrêt.");
    }
}
