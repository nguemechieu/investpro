package org.investpro.investpro;

import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class Log {
    public static void info(String message) {
        System.out.println(message);
    }
    public static void error(String message) {
        System.err.println(message);
    }
    public static void warn(String message) {
        System.err.println(message);
    }

    public Log() {


    }

    public static void trace(String message) {
        System.err.println(message);
    }

    public static void i(String file, String created_new_file) {
        System.err.println("File " + file + " created "
                + " at " + new java.util.Date());
    }

    public static void write(String file, String reader) throws IOException {

        try {


            FileWriter writer = new FileWriter(reader);
            writer.write(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void error(int tag, String s) {
        Logger.getLogger(s, String.valueOf(tag));
    }
}
