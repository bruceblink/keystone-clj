FROM clojure:temurin-21-tools-deps AS build
WORKDIR /app
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
