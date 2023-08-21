# Use the official OpenJDK 20 as base image
FROM openjdk:latest

# Set the working directory in the container
WORKDIR /app

# Copy the compiled Java application JAR file into the container
COPY target/investpro-1.0-SNAPSHOT.jar  /app/investpro-1.0-SNAPSHOT.jar

# Expose the port that your Java application listens on (change to the actual port)
EXPOSE 8080

# Set environment variables for MySQL connection
ENV DB_URL=jdbc:mysql://mysql-host:3306/db_name
ENV DB_USER=root
ENV DB_PASSWORD=your_password

# Start the Java application when the container starts
CMD ["java", "-jar", "investpro-1.0-SNAPSHOT.jar"]