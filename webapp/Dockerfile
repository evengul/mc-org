FROM maven:3-eclipse-temurin-21 AS build

# Cache maven dependencies
WORKDIR /home/maven/src
COPY pom.xml .
COPY mc-domain/pom.xml mc-domain/pom.xml
COPY mc-engine/pom.xml mc-engine/pom.xml
COPY mc-nbt/pom.xml mc-nbt/pom.xml
COPY mc-pipeline/pom.xml mc-pipeline/pom.xml
COPY mc-data/pom.xml mc-data/pom.xml
COPY mc-web/pom.xml mc-web/pom.xml
 # Download dependencies for better layer caching
RUN mvn dependency:go-offline

COPY mc-domain/src mc-domain/src
COPY mc-engine/src mc-engine/src
COPY mc-nbt/src mc-nbt/src
COPY mc-pipeline/src mc-pipeline/src
COPY mc-data/src mc-data/src
COPY mc-web/src mc-web/src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS mcorg_app

EXPOSE "8080:8080"

RUN mkdir /app

COPY --from=build /home/maven/src/mc-web/target/*-with-dependencies.jar /app/mcorg.jar

ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-jar", "/app/mcorg.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/test/ping || exit 1
