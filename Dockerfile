FROM clojure:temurin-21-tools-deps AS build
WORKDIR /app
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ARG PROXY_CA_B64
ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}
RUN if [ -n "$PROXY_CA_B64" ]; then \
      printf '%s' "$PROXY_CA_B64" | base64 -d > /usr/local/share/ca-certificates/environment-proxy.crt && \
      update-ca-certificates && \
      keytool -importcert -noprompt -trustcacerts -alias environment-proxy \
        -file /usr/local/share/ca-certificates/environment-proxy.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit; \
    fi
RUN if [ -n "${HTTP_PROXY}${HTTPS_PROXY}" ]; then \
      proxy_url="${HTTPS_PROXY:-$HTTP_PROXY}"; \
      proxy_host=$(printf '%s' "$proxy_url" | sed -E 's#^[a-zA-Z]+://([^:/]+).*#\1#'); \
      proxy_port=$(printf '%s' "$proxy_url" | sed -E 's#^[a-zA-Z]+://[^:/]+:([0-9]+).*#\1#'); \
      proxy_port="${proxy_port:-8080}"; \
      mkdir -p /root/.m2; \
      printf '%s\n' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
        '  <proxies><proxy><id>build-environment</id><active>true</active><protocol>http</protocol>' \
        "    <host>${proxy_host}</host><port>${proxy_port}</port>" \
        '    <nonProxyHosts>localhost|127.*|[::1]</nonProxyHosts></proxy></proxies>' \
        '</settings>' > /root/.m2/settings.xml; \
    fi
COPY deps.edn build.clj ./
COPY src ./src
COPY resources ./resources
RUN clojure -T:build uber

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /app/target/keystone-clj.jar /app/keystone-clj.jar
USER app
EXPOSE 18080
ENV PORT=18080
ENTRYPOINT ["java","-jar","/app/keystone-clj.jar"]
