package org.investpro;

import javafx.scene.Node;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;


public abstract class ServiceProvider extends Parent {
    private static final Logger logger = LoggerFactory.getLogger(ServiceProvider.class);
    public ServiceProvider() {
        super();
        logger.info("ServiceProvider created");
    }

    public static ServiceProvider getDefault() {

        // load our plugin
        ServiceLoader<ServiceProvider> serviceLoader =
                ServiceLoader.load(ServiceProvider.class);

        //checking if load was successful
        for (ServiceProvider provider : serviceLoader) {
            return provider;
        }
        throw new Error("Something is wrong with registering the addon");
    }

    public static ServiceProvider getInstance() {
        return getDefault();
    }

    public abstract String getMessage();


    @Override
    public Node getStyleableNode() {
        return super.getStyleableNode();
    }
}