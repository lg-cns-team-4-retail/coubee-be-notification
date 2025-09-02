FROM ubuntu:22.04
RUN apt-get update && apt-get install -y openjdk-17-jre-headless
MAINTAINER hoobastank0807@gmail.com
VOLUME /tmp
EXPOSE 8080
COPY build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
