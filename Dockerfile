FROM openjdk:8-jdk-alpine
VOLUME /tmp
WORKDIR /investpro
ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS
COPY target/InvestPro-1.0-SNAPSHOT investpro.jar
EXPOSE 3000
ENTRYPOINT exec java $JAVA_OPTS -jar investpro.jar
# For Spring-Boot project, use the entrypoint below to reduce Tomcat startup time.
#ENTRYPOINT exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar investpro.jar
