#BASE IMAGE
ARG BASE_IMAGE=eclipse-temurin:17-jdk-slim-bullseye
FROM ${BASE_IMAGE}

RUN mkdir -p /usr/share/drift-worker/service && \
    mkdir -p /var/log/drift-worker && \
    mkdir -p /src/main/resources/scripts && \
    chmod a+rw /var/log/drift-worker && \
    mkdir -p /etc/default/megh	&& \
    apt-get update -y && \
    apt-get --yes --allow-unauthenticated install curl && \
    apt-get --yes --allow-unauthenticated install telnet && \
    apt-get --yes --allow-unauthenticated install procps && \
    echo Asia/Calcutta > /etc/timezones

COPY worker/target/worker-*.jar /usr/share/drift-worker/service/worker.jar
COPY package/docker/worker/entrypoint.sh /entrypoint.sh

# Default values for JVM settings
ENV JVM_XMS="8g"
ENV JVM_XMX="8g"
EXPOSE 5600 5601 5603
ENTRYPOINT [ "sh", "/entrypoint.sh" ]