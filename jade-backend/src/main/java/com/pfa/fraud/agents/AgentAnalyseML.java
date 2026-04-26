package com.pfa.fraud.agents;

import com.pfa.fraud.comportements.AnalyseMLBehaviour;
import com.pfa.fraud.services.MLClientService;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent expert en Machine Learning.
 *
 * <p>Joue le rôle de répondeur dans un protocole FIPA-Request : il reçoit
 * une transaction de la part de {@link AgentSurveillanceTransaction},
 * appelle le microservice Flask ml-api via {@link MLClientService},
 * puis renvoie un message {@code INFORM} contenant :
 * <ul>
 *     <li>{@code fraud_probability} — score d'ensemble (XGBoost + IsolationForest)</li>
 *     <li>{@code xgb_score} — probabilité retournée par XGBoost</li>
 *     <li>{@code if_score} — score brut d'Isolation Forest</li>
 * </ul>
 *
 * <p>En cas d'erreur réseau ou de non-disponibilité du microservice ML,
 * un message {@code FAILURE} est renvoyé avec le détail de l'erreur.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentAnalyseML extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentAnalyseML.class);

    /** Type de service publié dans le DF. */
    public static final String SERVICE = "analyse-ml";

    /** URL du microservice ML, surchargée par la variable d'env {@code ML_API_URL}. */
    private static final String ML_API_URL_DEFAUT = "http://localhost:5000";

    private MLClientService mlClient;

    @Override
    protected void setup() {
        LOG.info("[AnalyseML] setup() — agent démarré.");

        final String url = System.getenv().getOrDefault("ML_API_URL", ML_API_URL_DEFAUT);
        this.mlClient = new MLClientService(url);
        LOG.info("[AnalyseML] Client ML configuré sur {}", url);

        enregistrerService();
        addBehaviour(new AnalyseMLBehaviour(this, mlClient));
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
            LOG.info("[AnalyseML] Service '{}' enregistré dans le DF.", SERVICE);
        } catch (FIPAException e) {
            LOG.error("[AnalyseML] Échec DF : {}", e.getMessage(), e);
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[AnalyseML] Échec désinscription DF : {}", e.getMessage());
        }
        LOG.info("[AnalyseML] Arrêt.");
    }
}
