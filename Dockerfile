# Use the official OpenJDK image as the base image
FROM openjdk:20-rc-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files into the container
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN mvn clean package

# Expose the port your Java application is running on
EXPOSE 8080

# Command to run your Java application
CMD ["java", "-jar", "target/investpro.jar"]

# Set environment variables for database connection
ENV DB_HOST=db_host
ENV DB_PORT=db_port
ENV DB_NAME=db_name
ENV DB_USER=db_user
ENV DB_PASS=db_password