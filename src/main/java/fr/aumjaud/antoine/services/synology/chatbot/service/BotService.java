package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import fr.aumjaud.antoine.services.synology.chatbot.model.ChatBotResponse;
import fr.aumjaud.antoine.services.common.http.HttpCode;
import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.HttpMessage;
import fr.aumjaud.antoine.services.common.http.HttpMessageBuilder;
import fr.aumjaud.antoine.services.common.http.HttpResponse;
import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;

public class BotService {

	private static final Logger logger = LoggerFactory.getLogger(BotService.class);
	private static final Gson GSON = new Gson();

	private HttpHelper httpHelper = new HttpHelper();
	
	private Properties properties;
	private List<String> validTokens;

	/**
	 * Set config
	 * @param properties the config to set
	 * @return true if config set successfully
	 */
	public void setConfig(Properties properties) {
		this.properties = properties;
		validTokens = Arrays.asList(properties.getProperty("chat-tokens").split(";"));
	}

	/**
	 * Receive message treatment
	 * @param userToken the token send by chat integration
	 * @param userName the user which sent the message
	 * @param message the message sent
	 * @return the response to send to the client
	 */
	public String receiveMessage(String userToken, String userName, String message) {
		// Check secure token
		if (!validTokens.contains(userToken)) {
			throw new NoAccessException("unknown token", "Unknown Tocken: " + userToken);
		}

		logger.debug("Message received form {}: {}", userName, message);

		// Parse message and build reponse
		String response;
		if (message.startsWith("echo")) {
			response = echoService();
		} else if (message.startsWith("pass ")) {
			response = passService(message);
		} else {
			response = chatBotService(message, userName);
		}
		logger.debug("Response: {}", response);
		return buildSynologyChatPayload(response);
	}

	/**
	 * Send a message to a user
	 * @param userName the name of the user
	 * @param message the message to send
	 * @return true if message sent
	 */
	public boolean sendMessage(String userName, String message) {
		// Check configuration
		String token = properties.getProperty("token." + userName);
		if (token == null)
			throw new WrongRequestException("unknown user", "user doesn't have a token set: " + userName);
		String targetUrl = properties.getProperty("synology-chat.url");
		if (targetUrl == null)
			throw new WrongRequestException("missing configuration", "synology-chat.url not defined in configuration");

		// Build target URL
		targetUrl = String.format(targetUrl, token);

		// Escape message
		if (message.contains("\")")) {
			message = message.replace("\"", "\\\"");
		}

		// Build payload (https://www.synology.com/en-us/knowledgebase/DSM/help/Chat/chat_integration)
		String payload = "payload=" + buildSynologyChatPayload(message); //not a json message
		HttpResponse httpResponse = httpHelper.postData(targetUrl, payload);
		if (httpResponse != null) {
			logger.debug("Message '{}' sent to user {}, response: {}", message, userName, httpResponse);
			return httpResponse.getHttpCode() == HttpCode.OK;
		} else {
			logger.error("Message '{}' NOT sent to user {}", message, userName);
			return false;
		}
	}

	/*
	 * PRIVATE
	 */
	
	/**
	 * Build payload in Synology Chat format
	 * @param message the message to send
	 * @return the paylaod
	 */
	private String buildSynologyChatPayload(String message) {
		return String.format("{\"text\": \"%s\"}", message);
	}
	/**
	 * Build payload for chatbot (API.AI format)
	 * @param message the message to send
	 * @param userName the user name of the sender
	 * @return the paylaod
	 */
	private String buildChatBotPayload(String message, String userName) {
		return String.format("{\"query\": [\"%s\"], \"timezone\": \"Europe/Paris\", \"lang\": \"fr\", \"sessionId\": \"%s\" }", message, userName);
	}


	/**
	 * Service which reply a static string
	 * @return the string to send to the chat
	 */
	private String echoService() {
		return "echo from bot";
	}

	/**
	 * Service which call the file-search API
	 * @param message the message sent in the chat by the user
	 * @return the string to send to the chat
	 */
	private String passService(String message) {
		String fileName = message.substring("pass ".length());
		HttpMessage httpFileMessage = new HttpMessageBuilder(properties.getProperty("file-search.url") + fileName)
			.setSecureKey(properties.getProperty("file-search.secure-key"))
			.build();
		HttpResponse httpFileResponse = httpHelper.getData(httpFileMessage);
		if (httpFileResponse.getHttpCode() == HttpCode.OK) {
			return httpFileResponse.getContent();
		} else if (httpFileResponse.getHttpCode() == HttpCode.NOT_FOUND) {
			return "File-API: Information not found in file " + fileName;
		} else {
			logger.warn("Can't get response form file-search API");
			return "File-API: error";
		}
	}

	/**
	 * Service which call the AI API and then the service specified by the bot
	 * @param message the message sent in the chat by the user
	 * @param userName the user name of the sender
	 * @return the string to send to the chat
	 */
	private String chatBotService(String message, String userName) {
		String response;
		HttpMessage httpChatBotMessage = new HttpMessageBuilder(properties.getProperty("api-ai.url"))
			.setJsonMessage(buildChatBotPayload(message, userName))
			.addHeader("Authorization", "Bearer " + properties.getProperty("api-ai.client.others.token"))
			.build();
		HttpResponse httpChatBotResponse = httpHelper.postData(httpChatBotMessage);
		if (httpChatBotResponse.getHttpCode() == HttpCode.OK) {
			String jsonResponse  = httpChatBotResponse.getContent();
			logger.debug("Reponse from API.AI: '{}'", jsonResponse);
			ChatBotResponse chatbotResponse = buildChatBotResponse(jsonResponse);
			String action = chatbotResponse.getResult().getAction();
			//If action not completed, or output forced
			if(action.contains(properties.getProperty("api-ai.action.output")) || chatbotResponse.getResult().isActionIncomplete()) {
				response =  chatbotResponse.getResult().getFulfillment().getSpeech();
			}
			else { //Action completed
				//Call the service specified by the bot
				HttpMessage httpActionMessage = new HttpMessageBuilder(properties.getProperty("api-ai.action." + action + ".url"))
					.setSecureKey(properties.getProperty("api-ai.action." + action + ".secure-key"))
					.setJsonMessage(chatbotResponse.getResult().getJsonParameters())
					.build();
				HttpResponse httpActionResponse = httpHelper.postData(httpActionMessage);
				if (httpActionResponse.getHttpCode() == HttpCode.OK) {
					response = httpActionResponse.getContent();
				} else {
					response = action + " error";
					logger.warn("{} API return an error {}: {}", action, httpActionResponse.getHttpCode(), httpActionResponse.getContent());
				}
			}
		} else {
			response = "ChatBot-API: error";
			logger.warn("AI-API return an error {}: {}", httpChatBotResponse.getHttpCode(), httpChatBotResponse.getContent());
		}
		return response;
	}

	/*package for test*/ ChatBotResponse buildChatBotResponse(String jsonResponse) {
		ChatBotResponse cbr = GSON.fromJson(jsonResponse, ChatBotResponse.class);
		if(cbr.getResult() != null && cbr.getResult().getParameters() != null) {
			cbr.getResult().setJsonParameters(GSON.toJson(cbr.getResult().getParameters()));
		}
		return cbr;
	}
}
