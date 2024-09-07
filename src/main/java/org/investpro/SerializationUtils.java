package org.investpro;

import com.google.gson.Gson;
import weka.classifiers.Classifier;

import java.io.File;

public class SerializationUtils {
    public static String serialize(Object obj) {
        return new Gson().toJson(obj);
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        return new Gson().fromJson(json, clazz);
    }

    public static void main(String[] args) {
        String json = "{\"name\":\"Alice\",\"age\":30,\"address\":{\"street\":\"123 Main St\",\"city\":\"New York\",\"state\":\"NY\",\"zip\":\"10001\"}}";
        Person person = deserialize(json, Person.class);
        System.out.println(person);
    }


}

