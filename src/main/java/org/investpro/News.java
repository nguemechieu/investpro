package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class News {
    private static final Logger logger = LoggerFactory.getLogger(News.class);
    String impact;
    Date date;
    String forecast;
    String previous;
    private String country;
    private int offset;
    private String title;

    public News(String title, String impact, String country, Date date, String forecast, String previous) {
        this.title = title;
        this.impact = impact;
        this.date = date;
        this.forecast = forecast;
        this.previous = previous;
        this.country = country;


        logger.debug(
                STR."News: \{this.title} \{this.impact} \{this.country} \{this.date} \{this.forecast} \{this.previous}"
        );
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getForecast() {
        return forecast;
    }

    public void setForecast(String forecast) {

        this.forecast = forecast;
    }

    public String getPrevious() {
        return previous;
    }

    public void setPrevious(String previous) {
        this.previous = previous;
    }

    @Override
    public String toString() {
        return STR."News{impact='\{impact}\{'\''}, country='\{country}\{'\''}, offset=\{offset}, title='\{title}\{'\''}, date=\{date}, forecast='\{forecast}\{'\''}, previous='\{previous}\{'\''}\{'}'}";
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int i) {
        this.offset = i;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
