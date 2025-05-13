FROM maven:3-eclipse-temurin-21 AS build

# Cache maven dependencies
WORKDIR /home/maven/src
COPY pom.xml .
COPY .mvn/ .mvn/
 # Download dependencies for better layer caching
RUN mvn dependency:go-offline

COPY src ./src/
RUN mvn clean install -DskipTests

FROM eclipse-temurin:21 AS mcorg_app
EXPOSE "8080:8080"
RUN mkdir /app
COPY --from=build /home/maven/src/target/*-with-dependencies.jar /app/mcorg.jar
ENTRYPOINT ["java","-jar","/app/mcorg.jar"]