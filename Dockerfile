FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 mdm && adduser -S -u 10001 -G mdm mdm
WORKDIR /opt/app
COPY --from=build --chown=10001:10001 /build/target/mdm-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Djava.io.tmpdir=/tmp","-jar","/opt/app/app.jar"]
