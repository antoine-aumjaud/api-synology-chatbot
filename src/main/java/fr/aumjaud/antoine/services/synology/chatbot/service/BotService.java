package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.aumjaud.antoine.services.common.http.HttpCode;
import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.HttpResponse;
import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;

public class BotService {

	private static final Logger logger = LoggerFactory.getLogger(BotService.class);

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
		String response = null;
		if (message.startsWith("echo")) {
			return "echo from bot";
		} else if (message.startsWith("pass ")) {
			HttpResponse httpResponse = httpHelper.getData(
					properties.getProperty("file-search.url") + message.substring("pass ".length()),
					properties.getProperty("file-search.secure-key"));
			if (httpResponse != null && httpResponse.getHttpCode() == HttpCode.OK) {
				response = httpResponse.getContent();
			} else {
				logger.warn("Can't get response form file-search API");
			}
		}
		logger.debug("Response: {}", response);

		return (response != null) ? buildChatPayload(response) : null;
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
		String payload = "payload=" + buildChatPayload(message);

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
	private String buildChatPayload(String message) {
		return String.format("{\"text\": \"%s\"}", message);
	}

}
