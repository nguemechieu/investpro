module investpro {
    requires java.desktop;

    requires com.fasterxml.jackson.core;
    requires org.jetbrains.annotations;
    requires Java.WebSocket;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires javafx.web;
    requires org.json;
    requires io.github.cdimascio.dotenv.java;
    requires bounce;


    requires org.hibernate.orm.core;
    requires weka.stable;
    requires javafx.swing;
    requires com.nimbusds.jose.jwt;
    requires com.google.gson;
    requires testcontainers;
    requires org.xerial.sqlitejdbc;
    requires netlib.java;


    requires com.jfoenix;
    requires jcip.annotations;

    requires io.ebean.annotation;


    requires org.hibernate.commons.annotations;
    requires jakarta.persistence;
    requires com.github.dockerjava.transport.zerodep;
    requires static lombok;
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
            javafx.swing;

    exports org.investpro;
}
