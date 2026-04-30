FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /investpro

COPY . .

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /investpro

COPY --from=build /investpro/target/investpro-1.0.0-SNAPSHOT.jar ./investpro.jar

ENV DB_URL=jdbc:mysql://mysql-host:3306/db
ENV DB_USER=root
ENV DB_PASSWORD=root

CMD ["java", "--enable-preview", "-jar", "investpro.jar"]
