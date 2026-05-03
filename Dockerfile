FROM docker.io/eclipse-temurin:25-jre

LABEL maintainer "Antoine Aumjaud <antoine_dev@aumjaud.fr>"

EXPOSE 9080

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl tar \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.5.1.tgz -o /tmp/docker.tgz \
 && tar -xzf /tmp/docker.tgz -C /tmp \
 && mv /tmp/docker/docker /usr/local/bin/docker \
 && chmod +x /usr/local/bin/docker \
 && rm -rf /tmp/docker /tmp/docker.tgz 
 
RUN groupadd --system app \
 && useradd --system --gid app --create-home --home-dir /home/app app

WORKDIR /home/app
RUN mkdir -p /home/app/logs /home/app/api-synology-chatbot/lib/conf \
 && chown -R app:app /home/app

ADD --chown=app:app build/distributions/api-synology-chatbot.tar .

VOLUME /home/app/api-synology-chatbot/lib/conf
VOLUME /home/app/logs

USER app

CMD ["./api-synology-chatbot/bin/api-synology-chatbot"]
