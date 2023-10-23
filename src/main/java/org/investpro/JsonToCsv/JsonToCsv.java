package org.investpro.JsonToCsv;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class JsonToCsv {

    public JsonToCsv() {
    }

    private static final Logger logger = LoggerFactory.getLogger(JsonToCsv.class);

    public void convertJsonToCsv(@NotNull String data, @NotNull JSONObject data0) throws IOException {
        logger.info("Converting JSON to CSV");


        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();
        CsvMapper mapper = new CsvMapper();
        mapper.readerWithSchemaFor(data0.getClass()).with(csvSchema).readValues(data0.toString());
        JsonParser p =
                mapper.getFactory().createParser(new File(data));
        Class<JsonNode> dat =
                JsonNode.class;
        MappingIterator<JsonNode> iterator = mapper.readValues(p, dat);
        ArrayList<String> headers = new ArrayList<>();
        while (iterator.hasNextValue()) {
            headers.add(iterator.nextValue().toString());
            logger.info(iterator.nextValue().toString());
        }
        logger.info(headers.toString());


    }


}
