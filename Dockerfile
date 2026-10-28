ARG MODULE

FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /build
COPY pom.xml .
COPY microservice-config/pom.xml microservice-config/
COPY microservice-eureka/pom.xml microservice-eureka/
COPY microservice-gateway/pom.xml microservice-gateway/
COPY microservice-student/pom.xml microservice-student/
COPY microservice-course/pom.xml microservice-course/
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline
COPY . .
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
