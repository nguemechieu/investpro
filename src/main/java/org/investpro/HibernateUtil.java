package org.investpro;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import static org.investpro.Exchange.logger;

public class HibernateUtil {

    // The single instance of the SessionFactory (thread-safe)
    private static final SessionFactory sessionFactory;

    // Static block for initialization
    static {
        try {
            // Create Configuration instance
            Configuration configuration = new Configuration();

            // Configure Hibernate settings from hibernate.cfg.xml
            configuration.configure("META-INF/persistence.xml");

            // Apply settings and build the SessionFactory
            ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties()).build();

            // Build session factory
            sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        } catch (Exception ex) {
            logger.error("Failed to build session factory" + ex);
            throw new ExceptionInInitializerError("Initial SessionFactory creation failed: " + ex.getMessage());
        }
    }

    // Provide access to the SessionFactory
    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    // Close the SessionFactory, ideally called on application shutdown
    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
