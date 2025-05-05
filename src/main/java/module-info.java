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
    requires org.apache.pdfbox;
    requires doxia.sink.api;
    requires proto.google.common.protos;

    opens org.investpro.investpro to javafx.graphics, javafx.fxml, com.fasterxml.jackson.databind;
    exports org.investpro.grpc;

    // Optional: export for compile-time reference
    exports org.investpro.investpro.model;
    exports org.investpro.investpro to javafx.graphics;
    exports org.investpro.investpro.ui.chart to javafx.graphics;
    opens org.investpro.investpro.ui.chart to javafx.fxml, javafx.graphics;
    exports org.investpro.investpro.ui.chart.overlay to javafx.graphics;
    opens org.investpro.investpro.ui.chart.overlay to javafx.fxml, javafx.graphics;
    opens org.investpro.investpro.model to com.fasterxml.jackson.databind, jakarta.persistence, javafx.fxml, javafx.graphics, org.hibernate.orm.core;
    exports org.investpro.investpro.ui to javafx.graphics;
    opens org.investpro.investpro.ui to com.fasterxml.jackson.databind, javafx.fxml, javafx.graphics;
}