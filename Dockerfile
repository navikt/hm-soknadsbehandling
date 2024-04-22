FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY build/libs/hm-soknadsbehandling-all.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
CMD ["app.jar"]