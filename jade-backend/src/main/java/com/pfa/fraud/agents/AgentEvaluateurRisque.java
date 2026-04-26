package com.pfa.fraud.agents;

import com.pfa.fraud.comportements.EvaluationRisqueBehaviour;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent évaluateur de risque combinant score ML et règles métier.
 *
 * <p>Reçoit le résultat de {@link AgentAnalyseML} (message {@code INFORM}
 * contenant {@code fraud_probability}) et applique trois règles métier
 * additionnelles :
 *
 * <table border="1" summary="Règles métier appliquées">
 *   <tr><th>Code</th><th>Règle</th><th>Pondération</th></tr>
 *   <tr><td>R1</td>
 *       <td>Montant supérieur à {@value #SEUIL_MONTANT_ELEVE} €</td>
 *       <td>+ {@value #BONUS_MONTANT_ELEVE}</td></tr>
 *   <tr><td>R2</td>
 *       <td>Plus de {@value #SEUIL_VELOCITE} transactions en 1 h sur la même carte</td>
 *       <td>+ {@value #BONUS_VELOCITE}</td></tr>
 *   <tr><td>R3</td>
 *       <td>Heure de transaction entre 01h et 04h</td>
 *       <td>+ {@value #BONUS_HORAIRE}</td></tr>
 * </table>
 *
 * <p>Le score final est borné à 1.0 et envoyé sous forme de {@code PROPOSE}
 * à {@link AgentBanque} dans le cadre d'un protocole FIPA-ContractNet.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public class AgentEvaluateurRisque extends Agent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AgentEvaluateurRisque.class);

    /** Type de service publié dans le DF. */
    public static final String SERVICE = "evaluation-risque";

    /** Seuil (€) au-delà duquel le montant est considéré comme élevé. */
    public static final double SEUIL_MONTANT_ELEVE = 5_000.0;
    /** Bonus appliqué au score si le montant dépasse {@link #SEUIL_MONTANT_ELEVE}. */
    public static final double BONUS_MONTANT_ELEVE = 0.20;

    /** Seuil de vélocité (nombre de transactions/heure). */
    public static final int SEUIL_VELOCITE = 3;
    /** Bonus appliqué si la vélocité est dépassée. */
    public static final double BONUS_VELOCITE = 0.25;

    /** Heure de début de la plage horaire à risque (incluse). */
    public static final int HEURE_RISQUE_DEBUT = 1;
    /** Heure de fin de la plage horaire à risque (exclue). */
    public static final int HEURE_RISQUE_FIN = 4;
    /** Bonus appliqué si la transaction a lieu dans la plage à risque. */
    public static final double BONUS_HORAIRE = 0.15;

    @Override
    protected void setup() {
        LOG.info("[Risque] setup() — agent démarré.");
        enregistrerService();
        addBehaviour(new EvaluationRisqueBehaviour(this));
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
            LOG.info("[Risque] Service '{}' enregistré dans le DF.", SERVICE);
        } catch (FIPAException e) {
            LOG.error("[Risque] Échec DF : {}", e.getMessage(), e);
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            LOG.warn("[Risque] Échec désinscription DF : {}", e.getMessage());
        }
        LOG.info("[Risque] Arrêt.");
    }
}
