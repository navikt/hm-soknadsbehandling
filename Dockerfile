FROM gcr.io/distroless/java17-debian11
WORKDIR /app
COPY build/libs/hm-soknadsbehandling.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD ["app.jar"]