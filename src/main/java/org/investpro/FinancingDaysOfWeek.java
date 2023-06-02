package org.investpro;

public class FinancingDaysOfWeek {
    public String dayOfWeek;
    public int daysCharged;

    public FinancingDaysOfWeek() {
        dayOfWeek = "";
        daysCharged = 0;

    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public int getDaysCharged() {
        return daysCharged;
    }
}
