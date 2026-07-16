FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV HTTP_PORT=8081
EXPOSE 8081

COPY --from=build /workspace/target/*.jar /app/cpay-backend.jar

ENTRYPOINT ["java", "-jar", "/app/cpay-backend.jar"]
