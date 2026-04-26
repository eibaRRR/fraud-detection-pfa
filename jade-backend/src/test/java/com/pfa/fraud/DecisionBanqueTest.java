package com.pfa.fraud;

import com.pfa.fraud.agents.AgentBanque;
import com.pfa.fraud.comportements.DecisionBanqueBehaviour;
import jade.core.Agent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires de la logique de décision de l'AgentBanque.
 *
 * <p>On vérifie que la fonction {@code decider(score)} respecte
 * strictement les seuils définis dans {@link AgentBanque}.
 */
class DecisionBanqueTest {

    private DecisionBanqueBehaviour newBehaviour() {
        final Agent fakeAgent = Mockito.mock(Agent.class);
        Mockito.when(fakeAgent.getLocalName()).thenReturn("Banque-Test");
        return new DecisionBanqueBehaviour(fakeAgent);
    }

    @Test
    void scoreFaibleApprouve() {
        assertEquals(AgentBanque.ACTION_APPROUVER, newBehaviour().decider(0.10));
    }

    @ParameterizedTest
    @CsvSource({
            "0.50, SIGNALER",
            "0.60, SIGNALER",
            "0.74, SIGNALER",
            "0.75, BLOQUER",
            "0.85, BLOQUER",
            "1.00, BLOQUER",
            "0.49, APPROUVER",
            "0.00, APPROUVER"
    })
    void seuilsRespectes(final double score, final String attendu) {
        assertEquals(attendu, newBehaviour().decider(score));
    }
}
