# #############################################################################
# Copyright (c) 2025 Metaform Systems, Inc.
#
# This program and the accompanying materials are made available under the
# terms of the Apache License, Version 2.0 which is available at
# https://www.apache.org/licenses/LICENSE-2.0.
#
# SPDX-License-Identifier: Apache-2.0
# #############################################################################

FROM eclipse-temurin:25-jre-alpine

RUN apk update && apk upgrade --no-cache

ARG APP_USER=docker
ARG APP_UID=10100

RUN addgroup --system "$APP_USER" && \
    adduser \
      --shell /sbin/nologin \
      --disabled-password \
      --gecos "" \
      --ingroup "$APP_USER" \
      --no-create-home \
      --uid "$APP_UID" \
      "$APP_USER"

USER "$APP_UID"
WORKDIR /app

COPY build/libs/registry-server.jar /app/registry-server.jar
COPY config/configuration.properties /app/configuration.properties

EXPOSE 8181

ENTRYPOINT ["java", "-Dedc.fs.config=/app/configuration.properties", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/registry-server.jar"]
