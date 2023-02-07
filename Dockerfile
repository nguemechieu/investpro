# syntax=docker/dockerfile:1
From ubuntu:latest as builder
RUN apt-get update -y
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /investpro
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN  ./mvnw dependency:resolve
COPY src ./src
CMD ["java-jar", "InvestPro 1.0-SNAPSHOT"]
