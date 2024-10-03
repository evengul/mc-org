FROM maven:3-openjdk-11 AS build
COPY . /home/maven/src
WORKDIR /home/maven/src
RUN mvn package

FROM openjdk:11 as mcorg_app
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /home/maven/src/target/*-with-dependencies.jar /app/mcorg.jar
ENTRYPOINT ["java","-jar","/app/mcorg.jar"]