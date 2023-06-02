package org.investpro;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapLikeType;

import java.util.Map;

public class JsonConverter {
    public static Map<String, Object> toMap(final Object YourDTO) {
        final ObjectMapper objectMapper = new ObjectMapper();
        final MapLikeType mapLikeType = objectMapper.getTypeFactory().constructMapLikeType(Map.class, String.class, Object.class);
        return objectMapper.convertValue(null, mapLikeType);
    }
}