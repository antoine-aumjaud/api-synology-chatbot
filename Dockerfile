FROM eclipse-temurin:25-jre

RUN apt-get update \
	&& apt-get install -y --no-install-recommends ca-certificates curl tar \
	&& curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.5.1.tgz -o /tmp/docker.tgz \
	&& tar -xzf /tmp/docker.tgz -C /tmp \
	&& mv /tmp/docker/docker /usr/local/bin/docker \
	&& chmod +x /usr/local/bin/docker \
	&& rm -rf /tmp/docker /tmp/docker.tgz \
	&& rm -rf /var/lib/apt/lists/*

LABEL maintainer "Antoine Aumjaud <antoine_dev@aumjaud.fr>"

EXPOSE 9080


WORKDIR /home/app
ADD build/distributions/api-synology-chatbot.tar .
VOLUME ./api-synology-chatbot/lib/conf
VOLUME ./logs

CMD    ./api-synology-chatbot/bin/api-synology-chatbot
