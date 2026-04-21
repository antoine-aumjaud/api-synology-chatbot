# api-syno-chatbot
API - Synology chatbot: Linked to a LLM Agent

[![Build Status](https://github.com/antoine-aumjaud/api-synology-chatbot/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/antoine-aumjaud/api-synology-chatbot/actions/workflows/build.yml)

This micro-service :  
- recieves webhook from Synology Chat,
- [removed] sends them to dialogflow to retrieve the user intent,
- [removed] calls the webservice configured for this intent,
- [todo] add IA with skills 
- returns in the HTTP response, the response of this webservice

It can also push messages to Synology Chat.


## Execution and Test

This repository use gradle to build the app. You can use ./gradlew run to launch the application.
The project is configured to run with Java 25.

Once the service launched, you can test it with this url :
http://localhost:9080/hi  
You can reload the configuration : 
http://localhost:9080/secure/reloadConfig?secure-key:xxx (or curl --header 'secure-key: xxx' "http://localhost:9080/secure/reloadConfig" )

## Configuration

The configuration is here: https://github.com/antoine-aumjaud/api-synology-chatbot/blob/master/src/dist/lib/conf/api-synology-chatbot.properties

```ini
secure-key=xxx
```
The first line of the configuration is the value of a header or an URL parameter that should be set to access to an URL which a /secure/* path.

```ini
github.webhook.secret=xxx
```
Shared secret used by the `/send-github/:user` endpoint to validate `X-Hub-Signature-256` (HMAC SHA256).

```ini
#message chat->api
chat-tokens=xxx;
```
Is the list of tokens (seperated by ';') authorized to call the /secure/receive/ URL.
This token is retrieved on the synology chat when you add an outgoing integration.

```ini
#message api->chat
token.antoine=xxx
```
It is the token of a channel the when a message is send to /secure/send-message/antoine
This token is retrieved on the synology chat when you add an incoming integration.


```ini
#synology chat url
synology-chat.url=xxx
```
The URL of the incomming integration, for my DS it is : https://admin.aumjaud.fr/webapi/entry.cgi?api=SYNO.Chat.External&method=incoming&version=1&token="%s"
the last %s is replaced by the token defined just before 


## Workflow
So workflow is:
DS Chat -> chabot API receive -> Agent with Skill  -> DS Chat (in HTTP response)

