package org.investpro;

import javafx.util.StringConverter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


public class InstantAxisFormatter extends StringConverter<Number> {
    private final DateTimeFormatter dateTimeFormat;

    @Contract("_ -> new")
    public static @NotNull InstantAxisFormatter of(DateTimeFormatter format) {
        return new InstantAxisFormatter(format);
    }

    public InstantAxisFormatter(DateTimeFormatter format) {
        dateTimeFormat = format == null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME : format;
    }

    @Override
    public String toString(@NotNull Number number) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(number.intValue()),
                ZoneId.systemDefault()).format(dateTimeFormat);
    }

    @Override
    public Number fromString(String string) {
        return Integer.valueOf(string);
    }

}
