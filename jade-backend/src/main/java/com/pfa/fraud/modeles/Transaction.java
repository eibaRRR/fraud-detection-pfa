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
 * Entité JPA représentant une transaction bancaire à analyser.
 *
 * <p>Mappée sur la table {@code transactions}. Sert à la fois de
 * référentiel pour les agents et de source de vérité pour le dashboard.
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
@Entity
@Table(name = "transactions")
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Statut : analyse en cours, pas encore tranchée par une banque. */
    public static final String STATUT_EN_ATTENTE = "EN_ATTENTE";
    /** Statut : transaction validée. */
    public static final String STATUT_APPROUVE   = "APPROUVE";
    /** Statut : transaction signalée pour vérification humaine. */
    public static final String STATUT_SIGNALE    = "SIGNALE";
    /** Statut : transaction bloquée. */
    public static final String STATUT_BLOQUE     = "BLOQUE";

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "horodatage", nullable = false)
    private OffsetDateTime horodatage;

    @Column(name = "montant", nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(name = "marchand", length = 255)
    private String marchand;

    @Column(name = "carte_fin", length = 4)
    private String carteFin;

    @Column(name = "banque_id", length = 50)
    private String banqueId;

    @Column(name = "label_fraude")
    private Boolean labelFraude = Boolean.FALSE;

    @Column(name = "probabilite", precision = 5, scale = 4)
    private BigDecimal probabilite;

    @Column(name = "statut", length = 20)
    private String statut = STATUT_EN_ATTENTE;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (horodatage == null) horodatage = OffsetDateTime.now();
        if (statut == null) statut = STATUT_EN_ATTENTE;
    }

    public UUID getId() { return id; }
    public void setId(final UUID id) { this.id = id; }

    public OffsetDateTime getHorodatage() { return horodatage; }
    public void setHorodatage(final OffsetDateTime h) { this.horodatage = h; }

    public BigDecimal getMontant() { return montant; }
    public void setMontant(final BigDecimal m) { this.montant = m; }

    public String getMarchand() { return marchand; }
    public void setMarchand(final String m) { this.marchand = m; }

    public String getCarteFin() { return carteFin; }
    public void setCarteFin(final String c) { this.carteFin = c; }

    public String getBanqueId() { return banqueId; }
    public void setBanqueId(final String b) { this.banqueId = b; }

    public Boolean getLabelFraude() { return labelFraude; }
    public void setLabelFraude(final Boolean l) { this.labelFraude = l; }

    public BigDecimal getProbabilite() { return probabilite; }
    public void setProbabilite(final BigDecimal p) { this.probabilite = p; }

    public String getStatut() { return statut; }
    public void setStatut(final String s) { this.statut = s; }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", montant=" + montant + ", statut=" + statut + "}";
    }
}
