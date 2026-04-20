package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

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
	 * @param channelId the channel Id which send the message
	 * @param userToken the token send by chat integration
	 * @param userName the user which sent the message
	 * @param message the message sent
	 * @return the response to send to the client
	 */
	public String receiveMessage(String channelId, String userToken, String userName, String message) {
		// Check secure token
		if (!validTokens.contains(userToken)) {
			throw new NoAccessException("unknown token", "Unknown Tocken: " + userToken);
		}

		logger.debug("Message received on channelId {} form {}: {}", channelId, userName, message);

		// Parse message and build reponse
		String response;
		if (message.startsWith("echo")) {
			response = echoService();
		}
		else if (message.startsWith("get")) {
			response = downloadService();
		} 
		else {
			// TODO call AI with skills
			throw new NotImplementedException("message not supported", "Message not supported: " + message);
		}
		logger.debug("Response to Chat: {}", response);
		return buildSynologyChatPayload(response, null);
	}

	/**
	 * Send a message to a user
	 * @param userName the name of the user
	 * @param message the message to send
	 * @return true if message sent
	 */
	public boolean sendMessage(String userName, String message, String url) {
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
		String payload = "payload=" + buildSynologyChatPayload(message, url); //not a json message
		HttpResponse httpResponse = httpHelper.postData(targetUrl, payload);
		if (httpResponse != null) {
			logger.debug("Message '{}' sent to user {}, response: {}", message, userName, httpResponse);
			String content = httpResponse.getContent();
			boolean res = httpResponse.getHttpCode() == HttpCode.OK && !content.contains("error");
			if(!res) logger.error("Payload '{}' NOT sent to user {}, response: {}", payload, userName, content);
			return res;
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
	 * @param url the url to a file added with the message
	 * @return the payload
	 */
	private String buildSynologyChatPayload(String message, String url) {
		if (message != null) message = message.replace("\n", "\\n");
		
		List<String> payload = new ArrayList<>(); 
		if(message != null) payload.add(String.format("\"text\": \"%s\"", message));
		if(url != null)     payload.add(String.format("\"file_url\": \"%s\"", url));
		return String.format("{%s}", String.join(", ", payload));
	}


	/**
	 * Service which reply a static string
	 * @return the string to send to the chat
	 */
	private String echoService() {
		return "echo from bot service";
	}

	/**
	 * Service which download a file and send its to a share drive, then send the url to the chat
	 * @return the string to send to the chat
	 */
	private String downloadService() {

		// si url est sur youtube
		// lance la commande youtube-dl pour télécharger la vidéo
		// cette commande doit être lanc2e
		return "download service not implemented yet";
	}

}
