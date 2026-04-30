package org.investpro.core.chat;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
@Getter
@Setter
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
        logger.debug("News created {}", title);

    }

    @Override
    public String toString() {
        return "News{impact='%s', country='%s', offset=%d, title='%s', date=%s, forecast='%s', previous='%s'}".formatted(impact, country, offset, title, date, forecast, previous);
    }

}
