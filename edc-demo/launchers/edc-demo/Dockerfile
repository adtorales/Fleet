FROM eclipse-temurin:25-jre-alpine

RUN apk update && apk upgrade --no-cache

ARG APP_USER=docker
ARG APP_UID=10100

RUN addgroup --system "$APP_USER"

RUN adduser \
     --shell /sbin/nologin \
     --disabled-password \
     --gecos "" \
     --ingroup "$APP_USER" \
     --no-create-home \
     --uid "$APP_UID" \
     "$APP_USER"

USER "$APP_USER"
WORKDIR /app

COPY build/libs/edc-demo.jar /app/edc-runtime.jar
COPY src/main/resources/log4j2.xml /app/log4j2.xml
ARG CONFIG_FILE=config/edc-demo.properties.example
COPY ${CONFIG_FILE} /app/configuration.properties

EXPOSE 8180 8181 8182 8282

ENTRYPOINT ["java", "-Dlog4j.configurationFile=file:///app/log4j2.xml", "-Dedc.fs.config=/app/configuration.properties", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/edc-runtime.jar"]
