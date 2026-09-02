FROM a88323zq5m8jpcsqo5.xuanyuan.run/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY maven-settings.xml /root/.m2/settings.xml
RUN mvn -q dependency:go-offline

COPY src ./src
RUN cp src/main/resources/application.example.yml src/main/resources/application.yml
RUN mvn -q -DskipTests package

FROM a88323zq5m8jpcsqo5.xuanyuan.run/eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd -r -u 1001 app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs && chown app:app app.jar /app/logs

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar"]
