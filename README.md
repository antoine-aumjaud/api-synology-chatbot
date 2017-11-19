# api-syno-chatbot
API - Synology chatbot: Linked to Google API.AI

[![Build Status](https://travis-ci.org/antoine-aumjaud/api-synology-chatbot.svg?branch=master)](https://travis-ci.org/antoine-aumjaud/api-synology-chatbot)

This micro-service :  
- recieves webhook from Synology Chat,
- sends them to dialogflow to retrieve the user intent,
- calls the webservice configured for this intent,
- returns in the HTTP response, the response of this webservice

It can also push messages to Synology Chat.
