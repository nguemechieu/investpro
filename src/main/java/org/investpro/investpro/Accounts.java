package org.investpro.investpro;

public class Accounts  {
    private String tags;
    private String name;


    public Accounts() {
        super();
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addTag(String name) {
        if (this.tags == null) {
            this.tags = "";
        } else this.tags = this.tags + "," + name;
    }
}
