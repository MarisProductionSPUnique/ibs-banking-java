FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B package -DskipTests dependency:copy-dependencies -DincludeArtifactIds=ojdbc11
FROM tailscale/tailscale:v1.104.7 AS tailscale
FROM eclipse-temurin:17-jre
COPY --from=tailscale /usr/local/bin/tailscale /usr/local/bin/tailscale
COPY --from=tailscale /usr/local/bin/tailscaled /usr/local/bin/tailscaled
WORKDIR /app
COPY --from=build /app/target/banking-1.0.0.jar app.jar
COPY --from=build /app/target/classes/com/ibs/OracleProbe.class /app/probe/com/ibs/OracleProbe.class
COPY --from=build /app/target/dependency/ojdbc11*.jar /app/probe/
EXPOSE 8080
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
COPY oracle-relay.py /app/oracle-relay.py
COPY start.sh /app/start.sh
ENTRYPOINT ["/bin/sh","/app/start.sh"]
