package org.investpro.investpro;

import org.jetbrains.annotations.NotNull;

public class Locales implements Comparable<Locales> {

    String language, country;


    public Locales(@NotNull String language, @NotNull String country) {
        super();
        this.country = country.toUpperCase();
        this.language = language.toLowerCase();
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(@NotNull String language) {
        this.language = language.toLowerCase();
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(@NotNull String country) {
        this.country = country.toUpperCase();
    }

    @Override
    public int compareTo(@NotNull Locales o) {
        return 0;
    }
}
