package com.pfa.fraud.simulation;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée du simulateur de transactions.
 *
 * <p>Démarre un <i>container JADE secondaire</i> qui rejoint la plateforme
 * principale (typiquement le service {@code jade-backend}) et lance un
 * {@link AgentSimulateur}. Celui-ci génère un flux configurable de
 * transactions et les pousse à l'AgentSurveillanceTransaction via le DF.
 *
 * <p>Configuration via variables d'environnement :
 * <ul>
 *     <li>{@code JADE_HOST}        — hôte de la plateforme (défaut : {@code jade-backend})</li>
 *     <li>{@code JADE_PORT}        — port JADE (défaut : {@code 1099})</li>
 *     <li>{@code SIM_RATE}         — transactions par seconde (défaut : {@code 2})</li>
 *     <li>{@code SIM_FRAUD_RATE}   — taux de fraude (défaut : {@code 0.05})</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public final class SimulateurTransactions {

    private static final Logger LOG = LoggerFactory.getLogger(SimulateurTransactions.class);

    private SimulateurTransactions() { }

    /**
     * @param args inutilisé (la configuration passe par les variables d'env)
     * @throws Exception si l'attachement au container principal échoue
     */
    public static void main(final String[] args) throws Exception {
        final String host = env("JADE_HOST", "jade-backend");
        final String port = env("JADE_PORT", "1099");
        final double rate = Double.parseDouble(env("SIM_RATE", "2"));
        final double fraudRate = Double.parseDouble(env("SIM_FRAUD_RATE", "0.05"));

        LOG.info("=== Simulateur PFA — connexion à {}:{} (rate={}/s, fraud={}%) ===",
                host, port, rate, fraudRate * 100.0);

        final Runtime runtime = Runtime.instance();
        final Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, host);
        profile.setParameter(Profile.MAIN_PORT, port);
        profile.setParameter(Profile.CONTAINER_NAME, "Simulation-Container");

        final AgentContainer container = runtime.createAgentContainer(profile);
        final AgentController ac = container.createNewAgent(
                "Simulateur",
                AgentSimulateur.class.getName(),
                new Object[]{rate, fraudRate}
        );
        ac.start();
        LOG.info("AgentSimulateur démarré.");

        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Arrêt du simulateur...");
            try {
                container.kill();
                runtime.shutDown();
            } catch (Exception ignored) { /* */ }
        }, "sim-shutdown"));
    }

    private static String env(final String key, final String def) {
        final String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
