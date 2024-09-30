module investpro {
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
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires weka.stable;
    requires javafx.swing;
    requires com.nimbusds.jose.jwt;
    requires com.google.gson;
    requires testcontainers;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires org.junit.jupiter.api;

    requires docker.java.transport.zerodep;
    requires com.jfoenix;

    requires jcip.annotations;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;


    exports org.investpro;

}