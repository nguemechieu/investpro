package org.investpro.investpro;

import lombok.Getter;
import lombok.NoArgsConstructor;
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

    private String content;
    private String publishedDate;

    public News(String title, String impact, String country, Date date, String forecast, String previous) {
        this.title = title;
        this.impact = impact;
        this.date = date;
        this.forecast = forecast;
        this.previous = previous;
        this.country = country;
        this.offset = calculateOffset(date);

        logger.debug("News created: {}", this);

    }

    public News() {

    }

    @Override
    public String toString() {
        return "News{" +
                "impact='" + impact + '\'' +
                ", date=" + date +
                ", forecast='" + forecast + '\'' +
                ", previous='" + previous + '\'' +
                ", country='" + country + '\'' +
                ", offset=" + offset +
                ", title='" + title + '\'' +
                '}';
    }

    private int calculateOffset(Date date) {
        long currentMilliseconds = new Date().getTime();
        long newsMilliseconds = date.getTime();
        long offsetMilliseconds = currentMilliseconds - newsMilliseconds;
        return (int) (offsetMilliseconds / 1000); // Convert milliseconds to seconds
    }

    public String getContent() {
        return "Title: " + title + "\nImpact: " + impact + "\nDate: " + date + "\nForecast: " + forecast + "\nPrevious: " + previous + "\nCountry: " + country + "\nOffset: " + offset;
    }
}
