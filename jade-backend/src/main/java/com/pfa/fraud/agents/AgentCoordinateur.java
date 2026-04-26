package com.pfa.fraud.agents;

import com.pfa.fraud.websocket.AlerteWebSocketServer;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent chef d'orchestre du système multi-agents de détection de fraude.
 *
 * <p>Responsabilités :
 * <ul>
 *     <li>Création et démarrage des autres agents (Surveillance, ML,
 *         Risque, Banque, Audit) dans le container courant</li>
 *     <li>Enregistrement du service {@code coordination-fraude} dans
 *         le Directory Facilitator (DF) JADE pour permettre la
 *         découverte dynamique</li>
 *     <li>Démarrage du serveur WebSocket exposant les alertes au
 *         dashboard React</li>
 *     <li>Surveillance périodique de la santé des agents (TickerBehaviour
 *         déclenché toutes les {@value #INTERVALLE_SANTE_MS} ms)</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentCoordinateur extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentCoordinateur.class);

    /** Intervalle (ms) entre deux contrôles de santé du système. */
    private static final long INTERVALLE_SANTE_MS = 30_000L;

    /** Type de service publié par l'agent coordinateur dans le DF. */
    public static final String SERVICE_COORDINATION = "coordination-fraude";

    /** Port d'écoute du serveur WebSocket d'alertes. */
    private static final int WEBSOCKET_PORT = 8080;

    /** Liste des agents périphériques à instancier (nom, classe). */
    private static final String[][] AGENTS_A_DEMARRER = {
            {"Surveillance", "com.pfa.fraud.agents.AgentSurveillanceTransaction"},
            {"AnalyseML",    "com.pfa.fraud.agents.AgentAnalyseML"},
            {"Risque",       "com.pfa.fraud.agents.AgentEvaluateurRisque"},
            {"Banque-BNP",   "com.pfa.fraud.agents.AgentBanque"},
            {"Audit",        "com.pfa.fraud.agents.AgentAudit"}
    };

    private AlerteWebSocketServer wsServer;

    /**
     * Initialisation de l'agent : enregistrement DF, démarrage des agents
     * périphériques, lancement du serveur WebSocket et du ticker de santé.
     */
    @Override
    protected void setup() {
        LOG.info("[Coordinateur] setup() — démarrage de l'orchestration...");

        enregistrerService();
        demarrerAgentsPeripheriques();
        demarrerWebSocketServer();

        addBehaviour(new TickerBehaviour(this, INTERVALLE_SANTE_MS) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onTick() {
                verifierSanteAgents();
            }
        });

        LOG.info("[Coordinateur] Orchestration prête.");
    }

    /**
     * Enregistre l'agent comme fournisseur du service de coordination
     * dans le Directory Facilitator JADE.
     */
    private void enregistrerService() {
        final DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_COORDINATION);
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            LOG.info("[Coordinateur] Service '{}' enregistré dans le DF.", SERVICE_COORDINATION);
        } catch (FIPAException e) {
            LOG.error("[Coordinateur] Échec d'enregistrement DF : {}", e.getMessage(), e);
        }
    }

    /**
     * Crée et démarre les agents Surveillance, ML, Risque, Banque, Audit
     * dans le container courant.
     */
    private void demarrerAgentsPeripheriques() {
        final ContainerController container = getContainerController();
        for (String[] info : AGENTS_A_DEMARRER) {
            final String nom = info[0];
            final String classe = info[1];
            try {
                final AgentController ac = container.createNewAgent(nom, classe, new Object[]{});
                ac.start();
                LOG.info("[Coordinateur] Agent '{}' démarré ({}).", nom, classe);
            } catch (Exception e) {
                LOG.error("[Coordinateur] Impossible de démarrer '{}': {}", nom, e.getMessage(), e);
            }
        }
    }

    /**
     * Démarre le serveur WebSocket Tyrus pour pousser les alertes
     * vers le dashboard.
     */
    private void demarrerWebSocketServer() {
        try {
            this.wsServer = new AlerteWebSocketServer(WEBSOCKET_PORT);
            this.wsServer.start();
            LOG.info("[Coordinateur] WebSocket /alertes démarré sur port {}.", WEBSOCKET_PORT);
        } catch (Exception e) {
            LOG.error("[Coordinateur] Échec démarrage WebSocket : {}", e.getMessage(), e);
        }
    }

    /**
     * Interroge le DF pour vérifier que les services attendus
     * (analyse-ml, evaluation-risque, banque, audit) sont bien actifs.
     */
    private void verifierSanteAgents() {
        try {
            final DFAgentDescription gabarit = new DFAgentDescription();
            final DFAgentDescription[] resultats = DFService.search(this, gabarit);
            LOG.info("[Coordinateur] Santé : {} agents enregistrés au DF.",
                    (resultats == null ? 0 : resultats.length));
        } catch (FIPAException e) {
            LOG.warn("[Coordinateur] Échec lecture DF : {}", e.getMessage());
        }
    }

    /**
     * Désinscription DF et arrêt du serveur WebSocket à la fermeture.
     */
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[Coordinateur] Échec désinscription DF : {}", e.getMessage());
        }
        if (wsServer != null) {
            wsServer.stop();
        }
        LOG.info("[Coordinateur] Arrêt complet.");
    }
}
