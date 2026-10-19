FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Download this PUBLIC certificate from your own Aiven service before building.
COPY deploy/aiven/ca.pem /build/aiven-ca.pem
# This store contains only a public CA certificate. changeit is not an account secret.
RUN keytool -importcert -noprompt -alias aiven-ca -file /build/aiven-ca.pem \
    -keystore /build/aiven-truststore.p12 -storetype PKCS12 -storepass changeit
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 mdm && adduser -S -u 10001 -G mdm -s /bin/sh mdm \
    && render_group="$(awk -F: '$3 == 1000 {print $1; exit}' /etc/group)" \
    && if [ -z "$render_group" ]; then addgroup -S -g 1000 rendersecrets; render_group=rendersecrets; fi \
    && addgroup mdm "$render_group"
WORKDIR /opt/app
COPY --from=build --chown=10001:10001 /build/target/mdm-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build --chown=10001:10001 /build/aiven-truststore.p12 aiven-truststore.p12
USER mdm
EXPOSE 10000
ENTRYPOINT ["java","-Xms64m","-Xmx256m","-XX:+ExitOnOutOfMemoryError","-Djava.io.tmpdir=/tmp","-jar","/opt/app/app.jar"]
