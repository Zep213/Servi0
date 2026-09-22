# ---- build ----
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home servio
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER servio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
