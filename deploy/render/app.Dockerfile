FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 mdm && adduser -S -u 10001 -G mdm -s /bin/sh mdm \
    && render_group="$(awk -F: '$3 == 1000 {print $1; exit}' /etc/group)" \
    && if [ -z "$render_group" ]; then addgroup -S -g 1000 rendersecrets; render_group=rendersecrets; fi \
    && addgroup mdm "$render_group" \
    && mkdir -p /home/mdm/.ssh \
    && chown 10001:10001 /home/mdm/.ssh && chmod 0700 /home/mdm/.ssh
WORKDIR /opt/app
COPY --from=build --chown=10001:10001 /build/target/mdm-0.0.1-SNAPSHOT.jar app.jar
# Named USER retains supplementary group 1000 for Render's runtime secret mounts.
USER mdm
EXPOSE 10000
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Djava.io.tmpdir=/tmp","-jar","/opt/app/app.jar"]
