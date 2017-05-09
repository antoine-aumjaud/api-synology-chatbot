FROM openjdk:8-jre-alpine

LABEL maintainer "Antoine Aumjaud <antoine_dev@aumjaud.fr>"

USER app
ENV HOME /home/app
WORKDIR /home/app

VOLUME /home/app/conf
EXPOSE 8080

COPY build/libs/*.jar executablejar.jar

CMD java -cp .:conf -jar executablejar.jar