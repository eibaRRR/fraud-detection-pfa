package com.pfa.fraud.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent générateur de transactions.
 *
 * <p>Rythme un {@link TickerBehaviour} pour produire {@code SIM_RATE}
 * transactions par seconde. Chaque transaction est soit légitime, soit
 * frauduleuse selon un des trois patterns :
 *
 * <ul>
 *     <li><b>Pattern A</b> — montant élevé (&gt; 4000€) à 2h du matin</li>
 *     <li><b>Pattern B</b> — rafale de 5 transactions en moins de 10 minutes
 *         sur la même carte</li>
 *     <li><b>Pattern C</b> — marchand inconnu + montant hors norme</li>
 * </ul>
 *
 * <p>Les transactions sont envoyées en {@code REQUEST} ACL à
 * l'AgentSurveillanceTransaction (découvert dynamiquement via le DF).
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentSimulateur extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentSimulateur.class);

    private static final String SERVICE_SURVEILLANCE = "surveillance-transactions";
    private static final String ONTOLOGIE = "analyse-fraude-ontologie";

    /** Liste de marchands "connus" pour les transactions légitimes. */
    private static final List<String> MARCHANDS_CONNUS = List.of(
            "Carrefour", "Amazon", "FNAC", "SNCF", "Uber", "Spotify",
            "EDF", "Orange", "AirFrance", "Decathlon", "BoulangerieDuCoin"
    );

    /** Liste de marchands "inconnus/suspects" utilisés par le pattern C. */
    private static final List<String> MARCHANDS_SUSPECTS = List.of(
            "ShadyCryptoExchange", "OffshoreCasino", "QuickCash24",
            "MysteryMart", "Phantom-Store-XYZ"
    );

    /** Banques simulées. */
    private static final List<String> BANQUES = List.of("BNP", "SG", "CA", "LCL");

    /** Cartes utilisées pour le pattern B (rafale). */
    private static final String[] CARTES_RAFALE = {"4242", "1337"};

    private double rate;
    private double fraudRate;
    private final ObjectMapper mapper = new ObjectMapper();
    private long compteurTotal = 0;
    private long compteurFraudes = 0;

    /** État du pattern B (compteur courant + carte courante). */
    private int rafaleRestante = 0;
    private String rafaleCarte = null;

    @Override
    protected void setup() {
        final Object[] args = getArguments();
        this.rate = (args != null && args.length > 0) ? (double) args[0] : 2.0;
        this.fraudRate = (args != null && args.length > 1) ? (double) args[1] : 0.05;

        final long periodeMs = Math.max(1L, Math.round(1000.0 / rate));
        LOG.info("[Simulateur] période={}ms, fraudRate={}", periodeMs, fraudRate);

        addBehaviour(new TickerBehaviour(this, periodeMs) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onTick() {
                emettreTransaction();
            }
        });
    }

    /** Génère et envoie une transaction (légitime ou frauduleuse). */
    private void emettreTransaction() {
        final ObjectNode tx = construireTransaction();
        final AID surveillance = decouvrirSurveillance();
        if (surveillance == null) {
            LOG.warn("[Simulateur] Surveillance introuvable dans le DF, retry...");
            return;
        }
        try {
            final ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(surveillance);
            msg.setOntology(ONTOLOGIE);
            msg.setConversationId("sim-" + tx.get("id").asText());
            msg.setContent(mapper.writeValueAsString(tx));
            send(msg);
            compteurTotal++;
            if (compteurTotal % 50 == 0) {
                LOG.info("[Simulateur] Émis {} transactions (dont {} fraudes)",
                        compteurTotal, compteurFraudes);
            }
        } catch (Exception e) {
            LOG.error("[Simulateur] Échec émission : {}", e.getMessage());
        }
    }

    /**
     * Construit une transaction. Si la rafale du pattern B est en cours,
     * elle est prioritaire ; sinon on tire un dé pour décider légitime/fraude.
     */
    ObjectNode construireTransaction() {
        if (rafaleRestante > 0 && rafaleCarte != null) {
            rafaleRestante--;
            return patternB_rafale(rafaleCarte);
        }
        final double dice = ThreadLocalRandom.current().nextDouble();
        if (dice < fraudRate) {
            return tirerPatternFraude();
        }
        return transactionLegitime();
    }

    private ObjectNode tirerPatternFraude() {
        compteurFraudes++;
        final int p = ThreadLocalRandom.current().nextInt(3);
        switch (p) {
            case 0: return patternA_montantNuit();
            case 1: {
                rafaleCarte = CARTES_RAFALE[ThreadLocalRandom.current().nextInt(CARTES_RAFALE.length)];
                rafaleRestante = 4; // la première est cette transaction
                return patternB_rafale(rafaleCarte);
            }
            default: return patternC_marchandSuspect();
        }
    }

    /** Transaction "normale" : montant raisonnable, marchand connu, heure courante. */
    private ObjectNode transactionLegitime() {
        final ObjectNode tx = baseTransaction();
        tx.put("montant", arrondi2(ThreadLocalRandom.current().nextDouble(2.0, 250.0)));
        tx.put("marchand", random(MARCHANDS_CONNUS));
        tx.put("horodatage", OffsetDateTime.now(ZoneOffset.UTC).toString());
        tx.put("_pattern", "LEGITIME");
        return tx;
    }

    /** Pattern A — montant > 4000€ à 2h du matin. */
    private ObjectNode patternA_montantNuit() {
        final ObjectNode tx = baseTransaction();
        tx.put("montant", arrondi2(ThreadLocalRandom.current().nextDouble(4000.0, 9500.0)));
        tx.put("marchand", random(MARCHANDS_CONNUS));
        tx.put("horodatage", horaireNuit(2));
        tx.put("_pattern", "FRAUDE_A_MONTANT_NUIT");
        return tx;
    }

    /** Pattern B — rafale rapide sur une carte ciblée. */
    private ObjectNode patternB_rafale(final String carte) {
        final ObjectNode tx = baseTransaction();
        tx.put("montant", arrondi2(ThreadLocalRandom.current().nextDouble(50.0, 800.0)));
        tx.put("marchand", random(MARCHANDS_CONNUS));
        tx.put("carte_fin", carte);
        tx.put("horodatage", OffsetDateTime.now(ZoneOffset.UTC).toString());
        tx.put("_pattern", "FRAUDE_B_RAFALE");
        return tx;
    }

    /** Pattern C — marchand suspect + montant hors norme. */
    private ObjectNode patternC_marchandSuspect() {
        final ObjectNode tx = baseTransaction();
        tx.put("montant", arrondi2(ThreadLocalRandom.current().nextDouble(1500.0, 6000.0)));
        tx.put("marchand", random(MARCHANDS_SUSPECTS));
        tx.put("horodatage", OffsetDateTime.now(ZoneOffset.UTC).toString());
        tx.put("_pattern", "FRAUDE_C_MARCHAND_SUSPECT");
        return tx;
    }

    /** Squelette commun à toutes les transactions. */
    private ObjectNode baseTransaction() {
        final ObjectNode tx = mapper.createObjectNode();
        tx.put("id", UUID.randomUUID().toString());
        tx.put("carte_fin", String.format("%04d", ThreadLocalRandom.current().nextInt(10_000)));
        tx.put("banque_id", random(BANQUES));
        return tx;
    }

    /** Construit un ISO-8601 UTC pour une heure ciblée du jour courant. */
    private String horaireNuit(final int heure) {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .withHour(heure)
                .withMinute(ThreadLocalRandom.current().nextInt(60))
                .withSecond(ThreadLocalRandom.current().nextInt(60))
                .withNano(0)
                .toInstant()
                .toString();
    }

    /** Cherche AgentSurveillanceTransaction dans le DF (premier hit). */
    private AID decouvrirSurveillance() {
        try {
            final DFAgentDescription gabarit = new DFAgentDescription();
            final ServiceDescription sd = new ServiceDescription();
            sd.setType(SERVICE_SURVEILLANCE);
            gabarit.addServices(sd);
            final DFAgentDescription[] res = DFService.search(this, gabarit);
            return (res != null && res.length > 0) ? res[0].getName() : null;
        } catch (FIPAException e) {
            return null;
        }
    }

    private static <T> T random(final List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private static double arrondi2(final double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Override
    protected void takeDown() {
        LOG.info("[Simulateur] Arrêt — total={}, fraudes={} (Instant={})",
                compteurTotal, compteurFraudes, Instant.now());
    }
}
