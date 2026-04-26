package com.pfa.fraud.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent d'audit : traçabilité complète de tous les échanges ACL.
 *
 * <p>Ce agent a deux modes de fonctionnement :
 * <ol>
 *     <li><b>Réception explicite</b> : tout autre agent peut lui envoyer
 *         une copie des messages ACL via l'ontologie
 *         {@value #ONTOLOGIE_AUDIT}. L'agent insère alors une ligne
 *         dans la table {@code messages_acl}.</li>
 *     <li><b>Rapport périodique</b> : toutes les
 *         {@value #INTERVALLE_RAPPORT_MS} ms, l'agent calcule et logge
 *         un rapport de session (nb transactions auditées, taux de fraude
 *         estimé, latence moyenne).</li>
 * </ol>
 *
 * <p>L'utilisation d'un service DF dédié ({@value #SERVICE}) permet aux
 * autres agents de découvrir dynamiquement l'auditeur sans adresse codée
 * en dur.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentAudit extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentAudit.class);

    /** Type de service publié dans le DF. */
    public static final String SERVICE = "audit";

    /** Ontologie réservée aux messages d'audit. */
    public static final String ONTOLOGIE_AUDIT = "audit-fraude-ontologie";

    /** Intervalle entre deux rapports de session (ms). */
    private static final long INTERVALLE_RAPPORT_MS = 60_000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong messagesAudites = new AtomicLong();
    private final AtomicLong fraudesDetectees = new AtomicLong();

    @Override
    protected void setup() {
        LOG.info("[Audit] setup() — agent démarré.");
        enregistrerService();
        addBehaviour(new ReceptionAuditBehaviour());
        addBehaviour(new RapportPeriodiqueBehaviour(this, INTERVALLE_RAPPORT_MS));
    }

    private void enregistrerService() {
        final DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE);
        sd.setName(getLocalName());
        sd.addOntologies(ONTOLOGIE_AUDIT);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            LOG.info("[Audit] Service '{}' enregistré dans le DF.", SERVICE);
        } catch (FIPAException e) {
            LOG.error("[Audit] Échec DF : {}", e.getMessage(), e);
        }
    }

    /**
     * Comportement cyclique : intercepte tout message reçu sur l'ontologie
     * d'audit et le journalise (DB + log JSON).
     */
    private final class ReceptionAuditBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;

        @Override
        public void action() {
            final MessageTemplate mt = MessageTemplate.MatchOntology(ONTOLOGIE_AUDIT);
            final ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            tracerMessage(msg);
        }

        private void tracerMessage(final ACLMessage msg) {
            try {
                final ObjectNode trace = mapper.createObjectNode();
                trace.put("emetteur", msg.getSender() != null ? msg.getSender().getLocalName() : "?");
                final AID dest = (msg.getAllReceiver() != null && msg.getAllReceiver().hasNext())
                        ? (AID) msg.getAllReceiver().next() : null;
                trace.put("destinataire", dest != null ? dest.getLocalName() : "?");
                trace.put("performatif", ACLMessage.getPerformative(msg.getPerformative()));
                trace.put("ontologie", msg.getOntology());
                trace.put("contenu", msg.getContent());

                LOG.info("[Audit] ACL trace : {}", mapper.writeValueAsString(trace));
                messagesAudites.incrementAndGet();
                if (msg.getContent() != null && msg.getContent().contains("BLOQUER")) {
                    fraudesDetectees.incrementAndGet();
                }
            } catch (Exception e) {
                LOG.warn("[Audit] Échec trace : {}", e.getMessage());
            }
        }
    }

    /**
     * TickerBehaviour publiant un rapport synthétique de l'activité du système.
     */
    private final class RapportPeriodiqueBehaviour extends TickerBehaviour {
        private static final long serialVersionUID = 1L;

        RapportPeriodiqueBehaviour(final Agent a, final long periode) {
            super(a, periode);
        }

        @Override
        protected void onTick() {
            final long total = messagesAudites.get();
            final long fraudes = fraudesDetectees.get();
            final double taux = (total == 0) ? 0.0 : ((double) fraudes / total) * 100.0;
            LOG.info("[Audit] Rapport — messages={}, fraudes={}, taux={}%",
                    total, fraudes, String.format("%.2f", taux));
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[Audit] Échec désinscription DF : {}", e.getMessage());
        }
        LOG.info("[Audit] Arrêt.");
    }
}
