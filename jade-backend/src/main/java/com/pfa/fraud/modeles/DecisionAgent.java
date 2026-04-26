package com.pfa.fraud.modeles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entité JPA représentant la décision prise par un agent sur une alerte.
 *
 * <p>Cette table sert de journal historique : chaque agent qui prend une
 * décision (typiquement {@code AgentBanque}) enregistre ici son
 * raisonnement, le code de décision, et un score de confiance.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
@Entity
@Table(name = "decisions_agents")
public class DecisionAgent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "alerte_id")
    private UUID alerteId;

    @Column(name = "agent_id", length = 100, nullable = false)
    private String agentId;

    @Column(name = "decision", length = 50, nullable = false)
    private String decision;

    @Column(name = "raisonnement", columnDefinition = "text")
    private String raisonnement;

    @Column(name = "confiance", precision = 5, scale = 4)
    private BigDecimal confiance;

    @Column(name = "cree_le")
    private OffsetDateTime creeLe;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (creeLe == null) creeLe = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(final UUID id) { this.id = id; }

    public UUID getAlerteId() { return alerteId; }
    public void setAlerteId(final UUID a) { this.alerteId = a; }

    public String getAgentId() { return agentId; }
    public void setAgentId(final String a) { this.agentId = a; }

    public String getDecision() { return decision; }
    public void setDecision(final String d) { this.decision = d; }

    public String getRaisonnement() { return raisonnement; }
    public void setRaisonnement(final String r) { this.raisonnement = r; }

    public BigDecimal getConfiance() { return confiance; }
    public void setConfiance(final BigDecimal c) { this.confiance = c; }

    public OffsetDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(final OffsetDateTime c) { this.creeLe = c; }

    @Override
    public String toString() {
        return "DecisionAgent{agent=" + agentId + ", decision=" + decision + "}";
    }
}
