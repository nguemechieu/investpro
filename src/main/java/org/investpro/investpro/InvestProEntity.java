package org.investpro.investpro;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class InvestProEntity implements Serializable {
    // Getters and setters
    private Long id;
    private String name;
    private String description;

    public InvestProEntity() {
    }

    public InvestProEntity(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

}
