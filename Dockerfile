# syntax=docker/dockerfile:1


FROM ubuntu:latest As Builder

RUN apt-get update --y
RUN apt-get install openjdk
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /investpro

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN ./mvnw dependency:resolve

COPY src ./src

CMD ["java-jar", "InvestPro 1.0-SNAPSHOT"]