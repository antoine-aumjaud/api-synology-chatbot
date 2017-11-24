# api-syno-chatbot
API - Synology chatbot: Linked to Google API.AI

[![Build Status](https://travis-ci.org/antoine-aumjaud/api-synology-chatbot.svg?branch=master)](https://travis-ci.org/antoine-aumjaud/api-synology-chatbot)

This micro-service :  
- recieves webhook from Synology Chat,
- sends them to dialogflow to retrieve the user intent,
- calls the webservice configured for this intent,
- returns in the HTTP response, the response of this webservice

It can also push messages to Synology Chat.


## Execution and Test

This repository use gradle to build the app. You can use ./gradlew run to launch the application.
Once the service launched, you can test it with this url :
http://localhost:9080/hi
You can reload the configuration : 
http://localhost:9080/secure/reloadConfig?secure-key:xxx (or curl --header 'secure-key: xxx' "http://localhost:9080/secure/reloadConfig" )

## Configuration
The configuration is here : https://github.com/antoine-aumjaud/api-synology-chatbot/blob/master/src/dist/lib/conf/api-synology-chatbot.properties

```ini
secure-key=xxx
```
The first line of the configuration is the value of a header or an URL parameter that should be set to access to an URL which a /secure/* path.

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
#services
synology-chat.url=xxx
```
The URL of the incomming integration, for my DS it is : https://admin.aumjaud.fr/webapi/entry.cgi?api=SYNO.Chat.External&method=incoming&version=1&token="%s
the last %s is replaced by the token defined just before 

```ini
#api.ai - service
# https://console.api.ai/api-client/#/editAgent/d81ef3d4-4023-4457-a9e3-0779f4531615/
api-ai.url=https://api.api.ai/api/query?v=20170507
api-ai.client.others.token=xxx
```

The URL is the API.AI URL with the version of the API you want to use.
The second line is the default agent token took to send the user message. This token is defined in API.AI integration.
(There is an option to call an another agent for one specific chat channel too.)

```ini
#api.ai response -> services
api-ai.action.output=output
api-ai.action.family-shoes-size-set.url=xxx
api-ai.action.family-shoes-size-set.secure-key=xxx
```
These last configuration items are to manage the API.AI response. If the user intent (call "action" in API.AI) is "output" then the API.AI message is returned in the chat HTTP response. 
Otherwise, the application searches for the configuration keys api-ai.action.user_intent.url and api-ai.action.user_intent.secure-key. If it finds them, it calls the URL with the header secure-hey (it do a POST if action finished by -set or a GET if action finish by -get).

## Workflow
So workflow is:
DS Chat -> chabot API receive -> API.AI -> chatbot API -> call an action (WS) -> chatbot API -> DS Chat (in HTTP response)

You can use webhook from API.AI too and the API.AI would always returns output to display the result build on API.AI side.
