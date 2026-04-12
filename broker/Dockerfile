FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

COPY target/*.jar /app/app.jar

EXPOSE 6933 6934 6935 7171/udp 7443/udp

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

