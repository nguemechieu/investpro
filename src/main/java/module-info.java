module invespro {
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.google.protobuf;
    requires com.nimbusds.jose.jwt;
    requires io.grpc;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires javafx.swing;
    requires javafx.web;
    requires static lombok;
    requires mysql.connector.j;
    requires org.hibernate.orm.core;
    requires org.jetbrains.annotations;
    requires org.json;
    requires org.slf4j;
    requires weka.stable;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;
    requires io.grpc.stub;
    requires io.grpc.protobuf;
    requires com.google.common;

    opens org.investpro.investpro.model to org.hibernate.orm.core, jakarta.persistence;
    opens org.investpro.investpro to javafx.graphics, javafx.fxml;

    // Enable Hibernate + ByteBuddy reflective access

    // Optional: export for compile-time reference
    exports org.investpro.investpro.model;
    exports org.investpro.investpro to javafx.graphics;
}