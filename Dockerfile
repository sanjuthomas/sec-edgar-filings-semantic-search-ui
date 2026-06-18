FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd -r -u 1001 appuser

COPY --from=build /app/target/sec-edgar-filings-semantic-search-ui-*.jar app.jar

USER appuser

EXPOSE 8095

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
