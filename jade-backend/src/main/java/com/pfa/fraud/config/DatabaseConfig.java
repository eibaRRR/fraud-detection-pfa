package com.pfa.fraud.config;

import com.pfa.fraud.modeles.Alerte;
import com.pfa.fraud.modeles.DecisionAgent;
import com.pfa.fraud.modeles.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration centralisée Hibernate + HikariCP pour PostgreSQL.
 *
 * <p>Charge la configuration depuis les variables d'environnement
 * (avec valeurs par défaut adaptées à {@code docker-compose}) et
 * fournit un {@link EntityManagerFactory} singleton.
 *
 * <p>Variables d'environnement supportées :
 * <ul>
 *     <li>{@code POSTGRES_HOST} (défaut : {@code postgres})</li>
 *     <li>{@code POSTGRES_PORT} (défaut : {@code 5432})</li>
 *     <li>{@code POSTGRES_DB}   (défaut : {@code fraud_detection})</li>
 *     <li>{@code POSTGRES_USER} (défaut : {@code pfa_user})</li>
 *     <li>{@code POSTGRES_PASSWORD} (défaut : {@code changeme})</li>
 *     <li>{@code HIBERNATE_DDL_AUTO} (défaut : {@code update})</li>
 * </ul>
 *
 * @author  Équipe PFA Fraud-Detection
 * @version 1.0.0
 */
public final class DatabaseConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);

    private static volatile EntityManagerFactory emf;

    private DatabaseConfig() { }

    /**
     * Retourne (ou construit à la première invocation) le
     * {@link EntityManagerFactory} unique de l'application.
     *
     * @return EntityManagerFactory partagé
     */
    public static synchronized EntityManagerFactory emf() {
        if (emf == null) {
            emf = build();
        }
        return emf;
    }

    /** Crée un nouvel {@link EntityManager} (à fermer par l'appelant). */
    public static EntityManager em() {
        return emf().createEntityManager();
    }

    private static EntityManagerFactory build() {
        final String host = env("POSTGRES_HOST", "postgres");
        final String port = env("POSTGRES_PORT", "5432");
        final String db   = env("POSTGRES_DB",   "fraud_detection");
        final String user = env("POSTGRES_USER", "pfa_user");
        final String pwd  = env("POSTGRES_PASSWORD", "changeme");
        final String ddl  = env("HIBERNATE_DDL_AUTO", "update");

        final String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        final Configuration cfg = new Configuration();
        cfg.setProperty("hibernate.connection.url", url);
        cfg.setProperty("hibernate.connection.username", user);
        cfg.setProperty("hibernate.connection.password", pwd);
        cfg.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        cfg.setProperty("hibernate.hbm2ddl.auto", ddl);
        cfg.setProperty("hibernate.show_sql", "false");
        cfg.setProperty("hibernate.format_sql", "false");

        // Pool HikariCP
        cfg.setProperty("hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        cfg.setProperty("hibernate.hikari.minimumIdle", "2");
        cfg.setProperty("hibernate.hikari.maximumPoolSize", "10");
        cfg.setProperty("hibernate.hikari.idleTimeout", "30000");
        cfg.setProperty("hibernate.hikari.connectionTimeout", "5000");

        cfg.addAnnotatedClass(Transaction.class);
        cfg.addAnnotatedClass(Alerte.class);
        cfg.addAnnotatedClass(DecisionAgent.class);

        try {
            final EntityManagerFactory built = cfg.buildSessionFactory().unwrap(EntityManagerFactory.class);
            LOG.info("[DB] Hibernate initialisé sur {} ({}@{}).", url, user, host);
            return built;
        } catch (Exception e) {
            LOG.error("[DB] Échec init Hibernate : {}", e.getMessage(), e);
            throw new IllegalStateException("Init DB impossible", e);
        }
    }

    private static String env(final String key, final String def) {
        final String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /** Ferme proprement l'EntityManagerFactory (à appeler à l'arrêt). */
    public static synchronized void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
            emf = null;
        }
    }
}
