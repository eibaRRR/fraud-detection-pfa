package com.pfa.fraud.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur WebSocket Tyrus exposant l'endpoint {@code /alertes}.
 *
 * <p>Le dashboard React se connecte à {@code ws://&lt;host&gt;:8080/alertes}
 * pour recevoir en temps réel les alertes de fraude (action {@code BLOQUER}
 * ou {@code SIGNALER}) ainsi que les décisions {@code APPROUVER} de routine.
 *
 * <p>La méthode statique {@link #broadcast(String)} permet à n'importe quel
 * agent (typiquement {@code AgentBanque} via {@code AlerteService}) de
 * pousser un payload JSON à toutes les sessions ouvertes.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
@ServerEndpoint("/alertes")
public class AlerteWebSocketServer {

    private static final Logger LOG = LoggerFactory.getLogger(AlerteWebSocketServer.class);

    /** Sessions WebSocket actives (thread-safe). */
    private static final Set<Session> SESSIONS = ConcurrentHashMap.newKeySet();

    private int port;
    private Server tyrusServer;

    /** Constructeur sans argument requis par Tyrus pour instancier l'endpoint
     *  par session WebSocket. */
    public AlerteWebSocketServer() {
        this.port = 0;
    }

    /**
     * @param port port d'écoute (ex. 8080)
     */
    public AlerteWebSocketServer(final int port) {
        this.port = port;
    }

    /**
     * Démarre le serveur Tyrus en arrière-plan.
     *
     * @throws Exception si l'initialisation Tyrus échoue
     */
    public void start() throws Exception {
        tyrusServer = new Server("0.0.0.0", port, "/", null, AlerteWebSocketServer.class);
        tyrusServer.start();
        LOG.info("[WebSocket] Serveur démarré sur ws://0.0.0.0:{}/alertes", port);
    }

    /** Arrête le serveur Tyrus si actif. */
    public void stop() {
        if (tyrusServer != null) {
            tyrusServer.stop();
            LOG.info("[WebSocket] Serveur arrêté.");
        }
    }

    /**
     * Diffuse un message JSON à toutes les sessions actives.
     *
     * @param json message à pousser au format JSON
     */
    public static void broadcast(final String json) {
        for (Session s : SESSIONS) {
            try {
                if (s.isOpen()) {
                    s.getBasicRemote().sendText(json);
                }
            } catch (IOException e) {
                LOG.warn("[WebSocket] Échec envoi à {} : {}", s.getId(), e.getMessage());
            }
        }
    }

    /** Nombre de clients connectés (utile pour les KPI/diagnostic). */
    public static int sessionsActives() {
        return SESSIONS.size();
    }

    @OnOpen
    public void onOpen(final Session session) {
        SESSIONS.add(session);
        LOG.info("[WebSocket] Connexion ouverte : {} (total={})", session.getId(), SESSIONS.size());
    }

    @OnClose
    public void onClose(final Session session) {
        SESSIONS.remove(session);
        LOG.info("[WebSocket] Connexion fermée : {} (total={})", session.getId(), SESSIONS.size());
    }

    @OnMessage
    public void onMessage(final String message, final Session session) {
        // Le dashboard est essentiellement read-only ;
        // on logge les éventuels pings/heartbeats clients.
        LOG.debug("[WebSocket] Reçu de {} : {}", session.getId(), message);
    }

    @OnError
    public void onError(final Session session, final Throwable error) {
        LOG.warn("[WebSocket] Erreur sur {} : {}", session != null ? session.getId() : "?",
                error.getMessage());
    }
}
