FROM maven:3-eclipse-temurin-21 AS build

# Cache maven dependencies
WORKDIR /home/maven/src
COPY pom.xml .
 # Download dependencies for better layer caching
RUN mvn dependency:go-offline

COPY src ./src/
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS mcorg_app

EXPOSE "8080:8080"

RUN mkdir /app

COPY --from=build /home/maven/src/target/*-with-dependencies.jar /app/mcorg.jar

ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-jar", "/app/mcorg.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/test/ping || exit 1