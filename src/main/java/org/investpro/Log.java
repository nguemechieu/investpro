package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

public class Log {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Log.class);

    public Log() {
        System.out.println("Log");
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(Log.class);
        logger.info("Log");


    }

    public static void info(String s, String message) {
        System.out.println(message);
        System.out.println(s);

        logger.info(s, message);


    }

    public static void error(String message) {
        System.err.println(message);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(message);
        logger.error(message);
    }

    public static void warn(String message) {
        System.err.println(message);
        org.slf4j.Logger logger = LoggerFactory.getLogger(message);
        logger.warn(message);
    }

    public static void trace(String message) {
        System.err.println(message);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(message);
        logger.trace(message);
    }

    public static void i(String file, String created_new_file) {
        System.err.println("File " + file + " created "
                + " at " + new java.util.Date());
        System.err.println(created_new_file);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(file);
        logger.info(file, created_new_file);
    }


    public static void error(int tag, String s) {
        Logger.getLogger(s, String.valueOf(tag));
        System.err.println(s);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(s);
        logger.error(s, tag);
    }

    public static void e(String tag, @NotNull String noCoinInfoAvailable) {
        Logger.getLogger(tag, noCoinInfoAvailable.getClass().getName());
        System.err.println(noCoinInfoAvailable);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(tag);
        logger.error(tag, noCoinInfoAvailable);
    }

    public void debug(String s) {


        System.err.println(s);
        org.slf4j.Logger logger =
                LoggerFactory.getLogger(s);
        logger.debug(s, s.getClass().getSimpleName());
    }
}
