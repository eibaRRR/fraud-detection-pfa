# ----- Stage build -----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
COPY pom.xml ./
RUN mvn -B -q -Dmaven.resolver.transport=wagon dependency:go-offline
COPY src ./src
RUN mvn -B -q -Dmaven.resolver.transport=wagon clean package -Dmaven.test.skip=true

# ----- Stage runtime -----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/jade-backend.jar /app/jade-backend.jar

EXPOSE 1099 8080

ENV JADE_HOST=0.0.0.0 \
    JADE_PORT=1099 \
    JADE_GUI=false \
    LOG_LEVEL=INFO

ENTRYPOINT ["java", "-jar", "/app/jade-backend.jar"]
