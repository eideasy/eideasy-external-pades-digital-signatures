FROM openjdk:11
WORKDIR /app

COPY ./target/pdf-1.0.0-SNAPSHOT.jar ./pdf-1.0.0-SNAPSHOT.jar
CMD [ "java", "-jar", "pdf-1.0.0-SNAPSHOT.jar" ]