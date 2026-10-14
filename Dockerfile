# Frontend build
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Backend build (React assets baked into the jar)
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/
RUN mvn -B -DskipTests package \
    && mv target/cdp-*.jar /build/app.jar

# Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system cdp \
    && useradd --system --gid cdp --home-dir /app --shell /usr/sbin/nologin cdp \
    && mkdir -p /app/data \
    && chown -R cdp:cdp /app

COPY --from=backend /build/app.jar /app/app.jar

USER cdp
EXPOSE 8080
VOLUME ["/app/data"]

ENV SPRING_DATASOURCE_URL="jdbc:h2:file:./data/cdp;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=TRUE"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
