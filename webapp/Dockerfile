FROM maven:3-eclipse-temurin-21 AS build
COPY . /home/maven/src
WORKDIR /home/maven/src

COPY repo_maven_cert.pem /usr/local/share/ca-certificates/repo_maven_cert.crt
RUN update-ca-certificates
RUN keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt -alias repo_maven_cert -file /usr/local/share/ca-certificates/repo_maven_cert.crt
RUN curl -I https://repo.maven.apache.org/maven2

RUN mvn package

FROM eclipse-temurin:21 AS mcorg_app
EXPOSE "8080:8080"
RUN mkdir /app
COPY --from=build /home/maven/src/target/*-with-dependencies.jar /app/mcorg.jar
ENTRYPOINT ["java","-jar","/app/mcorg.jar"]