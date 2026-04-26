# ----- Stage build -----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
COPY pom.xml ./
RUN mvn -B -q -Dmaven.resolver.transport=wagon dependency:go-offline
COPY src ./src
RUN mvn -B -q -Dmaven.resolver.transport=wagon clean package -DskipTests

# ----- Stage runtime -----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/simulation.jar /app/simulation.jar

ENV JADE_HOST=jade-backend \
    JADE_PORT=1099 \
    SIM_RATE=2 \
    SIM_FRAUD_RATE=0.05

ENTRYPOINT ["java", "-jar", "/app/simulation.jar"]
