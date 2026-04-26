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
 * Entité JPA représentant une alerte générée par le système.
 *
 * <p>Une alerte est créée dès qu'une décision est différente
 * d'{@code APPROUVER} (donc {@code SIGNALER} ou {@code BLOQUER}).
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
@Entity
@Table(name = "alertes")
public class Alerte implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "agent_declencheur", length = 100)
    private String agentDeclencheur;

    @Column(name = "score_risque", precision = 5, scale = 4)
    private BigDecimal scoreRisque;

    /** Liste JSON (tableau) des règles déclenchées, stockée en JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regles_declenchees", columnDefinition = "jsonb")
    private String reglesDeclenchees;

    @Column(name = "action_effectuee", length = 20)
    private String actionEffectuee;

    @Column(name = "resolue_le")
    private OffsetDateTime resolueLe;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public void setId(final UUID id) { this.id = id; }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(final UUID t) { this.transactionId = t; }

    public String getAgentDeclencheur() { return agentDeclencheur; }
    public void setAgentDeclencheur(final String a) { this.agentDeclencheur = a; }

    public BigDecimal getScoreRisque() { return scoreRisque; }
    public void setScoreRisque(final BigDecimal s) { this.scoreRisque = s; }

    public String getReglesDeclenchees() { return reglesDeclenchees; }
    public void setReglesDeclenchees(final String r) { this.reglesDeclenchees = r; }

    public String getActionEffectuee() { return actionEffectuee; }
    public void setActionEffectuee(final String a) { this.actionEffectuee = a; }

    public OffsetDateTime getResolueLe() { return resolueLe; }
    public void setResolueLe(final OffsetDateTime r) { this.resolueLe = r; }

    @Override
    public String toString() {
        return "Alerte{id=" + id + ", action=" + actionEffectuee + ", score=" + scoreRisque + "}";
    }
}
