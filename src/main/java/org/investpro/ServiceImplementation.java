package org.investpro;

import java.security.Provider;

public class ServiceImplementation extends Provider {
    protected ServiceImplementation(String name, String versionStr, String info) {
        super(name, versionStr, info);
    }

    public String getMessage() {
        return "Hello World";
    }


}