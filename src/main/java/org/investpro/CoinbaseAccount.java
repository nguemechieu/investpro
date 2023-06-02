package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class CoinbaseAccount extends Account {
    private String email;
    private String updatedAt;
    private String createdAt;

    public CoinbaseAccount(@NotNull JSONObject jsonObject) {

        super();

        if (jsonObject.has("id")) {
            setId(jsonObject.getString("id"));
        }
        if (jsonObject.has("name")) {
            setName(jsonObject.getString("name"));
        }
        if (jsonObject.has("email")) {
            setEmail(jsonObject.getString("email"));
        }
        if (jsonObject.has("currency")) {
            setCurrency(jsonObject.getString("currency"));
        }
        if (jsonObject.has("balance")) {
            setBalance(Double.parseDouble(jsonObject.getString("balance")));
        }
        if (jsonObject.has("created_at")) {
            setCreatedAt(jsonObject.getString("created_at"));
        }
        if (jsonObject.has("updated_at")) {
            setUpdatedAt(jsonObject.getString("updated_at"));

        }
    }

    public String getEmail() {
        return email;
    }

    private void setEmail(String email) {
        this.email = email;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    private void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    private void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
