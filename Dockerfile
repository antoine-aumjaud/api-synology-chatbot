FROM openjdk:8-jre-alpine

LABEL maintainer "Antoine Aumjaud <antoine_dev@aumjaud.fr>"
VOLUME /home/app/conf
EXPOSE 9080

RUN useradd -ms /bin/bash app
USER app
ENV HOME /home/app
WORKDIR /home/app
COPY build/libs/*.jar executablejar.jar

CMD java -cp .:conf -jar executablejar.jar