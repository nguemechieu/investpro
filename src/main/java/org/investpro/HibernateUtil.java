package org.investpro;

import lombok.Getter;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import static org.investpro.Exchange.logger;

@Getter
public class HibernateUtil {

    // Provide access to the SessionFactory
    // The single instance of the SessionFactory (thread-safe
    SessionFactory sessionFactory;

    public HibernateUtil() {
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
            logger.error("Failed to build session factory{}", String.valueOf(ex));
            throw new ExceptionInInitializerError("Initial SessionFactory creation failed: " + ex.getMessage());
        }
    }

    // Static block for initialization


}
