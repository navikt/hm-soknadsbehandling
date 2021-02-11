FROM navikt/java:12

COPY build/libs/hm-soknadsbehandling-all.jar app.jar

COPY --from=redboxoss/scuttle:latest /scuttle /bin/scuttle
ENV ENVOY_ADMIN_API=http://127.0.0.1:15000
ENV ISTIO_QUIT_API=http://127.0.0.1:15020
ENTRYPOINT ["scuttle", "/dumb-init", "--", "/entrypoint.sh"]
