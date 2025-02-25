module investpro {

    requires org.jetbrains.annotations;
    requires Java.WebSocket;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires javafx.web;
    requires org.json;

    requires bounce;


    requires org.hibernate.orm.core;

    requires javafx.swing;

    requires com.google.gson;
    requires testcontainers;


    requires com.jfoenix;


    requires io.ebean.annotation;


    requires org.hibernate.commons.annotations;
    requires jakarta.persistence;
    requires com.github.dockerjava.transport.zerodep;

    requires com.github.dockerjava.api;
    requires static lombok;
    requires weka.stable;
    requires com.nimbusds.jose.jwt;
    requires mysql.connector.j;
    requires jakarta.transaction;
    requires java.compiler;
    requires com.fasterxml.jackson.dataformat.csv;
    requires com.fasterxml.jackson.databind;
    requires org.apache.pdfbox;
    requires ch.qos.logback.classic;
    requires java.management;
    requires com.fasterxml.uuid;
    requires org.slf4j;

    opens org.investpro to


            org.hibernate.orm.core,

            netlib.java,
            com.fasterxml.jackson.databind,
            com.fasterxml.jackson.datatype.jsr310,
            com.google.gson,
            org.json,
            io.github.cdimascio.dotenv.java,
            bounce,
            jakarta.persistence,
            weka.stable,
            javafx.swing
            ;


    exports org.investpro;
    exports org.investpro.exchanges;
    opens org.investpro.exchanges to bounce, com.fasterxml.jackson.databind, com.fasterxml.jackson.datatype.jsr310, com.google.gson, io.github.cdimascio.dotenv.java, jakarta.persistence, javafx.swing, netlib.java, org.hibernate.orm.core, org.json, weka.stable;
    exports org.investpro.ui;
    opens org.investpro.ui to bounce, com.fasterxml.jackson.databind, com.fasterxml.jackson.datatype.jsr310, com.google.gson, io.github.cdimascio.dotenv.java, jakarta.persistence, javafx.swing, netlib.java, org.hibernate.orm.core, org.json, weka.stable;
    exports org.investpro.ai;
    opens org.investpro.ai to bounce, com.fasterxml.jackson.databind, com.fasterxml.jackson.datatype.jsr310, com.google.gson, io.github.cdimascio.dotenv.java, jakarta.persistence, javafx.swing, netlib.java, org.hibernate.orm.core, org.json, weka.stable;
}
