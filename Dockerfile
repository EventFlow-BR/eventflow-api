FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /build/target/eventflow-api-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
