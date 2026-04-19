FROM eclipse-temurin:25-jre

LABEL maintainer "Antoine Aumjaud <antoine_dev@aumjaud.fr>"

EXPOSE 9080

WORKDIR /home/app
ADD build/distributions/api-synology-chatbot.tar .
VOLUME ./api-synology-chatbot/lib/conf
VOLUME ./logs

CMD    ./api-synology-chatbot/bin/api-synology-chatbot
