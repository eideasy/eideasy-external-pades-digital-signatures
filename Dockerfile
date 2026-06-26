FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S -G app app

COPY --chown=app:app target/*.jar app.jar

EXPOSE 8084
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
