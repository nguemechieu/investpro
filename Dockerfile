# syntax=docker/dockerfile:1

FROM ubuntu:latest
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /investpro
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN  ./mvnw dependency:resolve
COPY src ./src
EXPOSE 8081
CMD ["java-jar", "InvestPro 1.0-SNAPSHOT"]
