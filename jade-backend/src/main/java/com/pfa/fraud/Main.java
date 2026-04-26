package com.pfa.fraud;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal du système multi-agents JADE de détection de fraude.
 *
 * <p>Cette classe est responsable de :
 * <ul>
 *     <li>Démarrer la plateforme JADE (main container)</li>
 *     <li>Instancier l'AgentCoordinateur, qui se chargera de créer
 *         les autres agents et de démarrer les services connexes
 *         (WebSocket, audit, ML, etc.)</li>
 *     <li>Gérer l'arrêt propre via shutdown hook</li>
 * </ul>
 *
 * <p>Variables d'environnement utilisées :
 * <ul>
 *     <li>{@code JADE_HOST} (défaut : {@code localhost})</li>
 *     <li>{@code JADE_PORT} (défaut : {@code 1099})</li>
 *     <li>{@code JADE_GUI}  (défaut : {@code false})</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /** Nom logique de l'agent coordinateur dans la plateforme JADE. */
    private static final String NOM_AGENT_COORDINATEUR = "Coordinateur";

    /** Classe de l'agent coordinateur (FQN attendu par JADE). */
    private static final String CLASSE_AGENT_COORDINATEUR =
            "com.pfa.fraud.agents.AgentCoordinateur";

    private Main() {
        // Empêche l'instanciation
    }

    /**
     * Démarre la plateforme JADE et l'agent coordinateur.
     *
     * @param args arguments éventuels passés au container JADE (ignorés ici)
     * @throws Exception en cas d'échec de démarrage
     */
    public static void main(final String[] args) throws Exception {
        LOG.info("=== Démarrage du système Multi-Agents PFA Fraud-Detection ===");

        final String host = getEnvOrDefault("JADE_HOST", "localhost");
        final String port = getEnvOrDefault("JADE_PORT", "1099");
        final boolean gui = Boolean.parseBoolean(getEnvOrDefault("JADE_GUI", "false"));

        final Runtime runtime = Runtime.instance();
        final Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, host);
        profile.setParameter(Profile.MAIN_PORT, port);
        profile.setParameter(Profile.GUI, String.valueOf(gui));
        profile.setParameter(Profile.PLATFORM_ID, "pfa-fraud-platform");

        final AgentContainer container = runtime.createMainContainer(profile);
        LOG.info("Container principal JADE créé sur {}:{}", host, port);

        final AgentController coordinateur = container.createNewAgent(
                NOM_AGENT_COORDINATEUR,
                CLASSE_AGENT_COORDINATEUR,
                new Object[]{}
        );
        coordinateur.start();
        LOG.info("Agent {} démarré ({}).", NOM_AGENT_COORDINATEUR, CLASSE_AGENT_COORDINATEUR);

        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Signal d'arrêt reçu : fermeture de la plateforme JADE...");
            try {
                container.kill();
                runtime.shutDown();
            } catch (Exception e) {
                LOG.error("Erreur lors de l'arrêt de JADE : {}", e.getMessage(), e);
            }
        }, "jade-shutdown-hook"));

        LOG.info("Système opérationnel. En attente de transactions...");
    }

    private static String getEnvOrDefault(final String key, final String def) {
        final String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
