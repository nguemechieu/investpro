# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy

WORKDIR /investpro
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
USER daemon
   
RUN  ./mvnw dependency:resolve
COPY src ./src
CMD ["java-jar", "InvestPro 1.0-SNAPSHOT"]
