#BASE IMAGE
ARG BASE_IMAGE=eclipse-temurin:17-jdk-slim-bullseye
FROM ${BASE_IMAGE}

RUN mkdir -p /usr/share/drift-api/service && \
    mkdir -p /var/log/drift-api && \
    chmod a+rw /var/log/drift-api && \
    mkdir -p /etc/default/megh	&& \
    apt-get update -y && \
    apt-get --yes --allow-unauthenticated install curl && \
    apt-get --yes --allow-unauthenticated install telnet && \
    apt-get --yes --allow-unauthenticated install procps && \
    echo Asia/Calcutta > /etc/timezones

COPY api/target/api-*.jar /usr/share/drift-api/service/api.jar
COPY package/docker/api/entrypoint.sh /entrypoint.sh

EXPOSE 5500 5501 5503
ENTRYPOINT [ "sh", "/entrypoint.sh" ]
