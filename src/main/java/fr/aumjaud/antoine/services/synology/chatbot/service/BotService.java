package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		} else {
			String agentToken = properties.getProperty("api-ai.client." + channelId + ".token");
			if(agentToken == null) {
				agentToken = properties.getProperty("api-ai.client.others.token");
			}
			response = chatBotService(agentToken, message, userName);
		}
		logger.debug("Response to Chat: {}", response);
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
			return httpResponse.getHttpCode() == HttpCode.OK 
				&& !httpResponse.getContent().contains("error");
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
		if (message != null) message = message.replace("\n", "\\n");
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
		return "echo from bot service";
	}

	/**
	 * Service which call the AI API and then the service specified by the bot
	 * @param agentToken the token to identify the agent
	 * @param message the message sent in the chat by the user
	 * @param userName the user name of the sender
	 * @return the string to send to the chat
	 */
	private String chatBotService(String agentToken, String message, String userName) {
		String response;
		HttpMessage httpChatBotMessage = new HttpMessageBuilder(properties.getProperty("api-ai.url"))
				.setJsonMessage(buildChatBotPayload(message, userName))
				.addHeader("Authorization", "Bearer " + agentToken).build();
		HttpResponse httpChatBotResponse = httpHelper.postData(httpChatBotMessage);
		if (httpChatBotResponse.getHttpCode() == HttpCode.OK) {
			String jsonResponse = httpChatBotResponse.getContent();
			logger.debug("Response from API.AI: '{}'", jsonResponse);
			ChatBotResponse chatbotResponse = buildChatBotResponse(jsonResponse);
			String action = chatbotResponse.getResult().getAction();
			String botResponse = chatbotResponse.getResult().getFulfillment().getSpeech();
			//If action not completed, or output forced
			if (action.contains(properties.getProperty("api-ai.action.output"))
					|| chatbotResponse.getResult().isActionIncomplete()) {
				response = botResponse;
			} else { //Action completed
						//Call the service specified by the bot
				logger.debug("Call service: {}", action);
				String url = buildUrlWithValuedParameters(properties.getProperty("api-ai.action." + action + ".url"), chatbotResponse);
				String outputType = chatbotResponse.getResult().getParameters().get("outputType");
				HttpMessage httpActionMessage = new HttpMessageBuilder(url) //
						.addHeader("Accept", "text/plain")
						.setSecureKey(properties.getProperty("api-ai.action." + action + ".secure-key"))
						.setJsonMessage(chatbotResponse.getResult().getJsonAllParameters()).build();
				HttpResponse httpActionResponse = (action.endsWith("-get")) //
						? httpHelper.getData(httpActionMessage)
						: httpHelper.postData(httpActionMessage);
				if (httpActionResponse.getHttpCode() == HttpCode.OK) {
					String serviceResponse = httpActionResponse.getContent();
					logger.debug("Response from service {}: '{}'", action, serviceResponse);
					
					//manage response
					if(outputType == null) outputType = "service-message";
					switch(outputType) {
						case "none": 
							response = "";
							break;
						case "service-message": 
							response = (serviceResponse.length() == 0) ? // if no response, take API response
								botResponse : serviceResponse;
							break;
						case "bot-message": 
							response = botResponse;
							break;
						case "bot-text-template": 
							response = fillTextTemplate(botResponse, serviceResponse);
							break;
						case "bot-json-template": 
							response = fillJsonTemplate(botResponse, serviceResponse);
							break;
						default:
							response = "ChatBot-API error (output management)";
							logger.error("Not managed outputType '{}' for action: {}", outputType, action);
							break;
						}
				} else {
					response = "Service " + action + " error";
					logger.warn("{} API has returned an error {}: {}", action, httpActionResponse.getHttpCode(),
							httpActionResponse.getContent());
				}
			}
		} else {
			response = "ChatBot-API error (API.AI error)";
			logger.warn("AI-API has returned an error {}: {}", httpChatBotResponse.getHttpCode(),
					httpChatBotResponse.getContent());
		}
		return response;
	}

	/*package for test*/ ChatBotResponse buildChatBotResponse(String jsonResponse) {
		ChatBotResponse cbr = GSON.fromJson(jsonResponse, ChatBotResponse.class);
		if (cbr.getResult() != null) {
			cbr.getResult().setJsonAllParameters(GSON.toJson(cbr.getResult().getAllParameters()));
		}
		return cbr;
	}

	private String buildUrlWithValuedParameters(String url, ChatBotResponse chatbotResponse) {
		if (chatbotResponse.getResult() != null && chatbotResponse.getResult().getAllParameters() != null) {
			for (Map.Entry<String, String> entry : chatbotResponse.getResult().getAllParameters().entrySet()) {
				if (url.contains(":" + entry.getKey())) {
					try {
						String value = URLEncoder.encode(entry.getValue(), "UTF-8");
						url = url.replace(":" + entry.getKey(), value);
					} catch(UnsupportedEncodingException e) {
						logger.error("Can't encode parameter, {}", e.getMessage(), e);
					}
				}
			}
		}
		return url;
	}

	/* package for test*/ String fillTextTemplate(String template, String value) {
		return String.format(template, value);
	}
	
	private Pattern patternTemplate = Pattern.compile("\\$\\{(\\w+)\\}");
	/* package for test*/ String fillJsonTemplate(String template, String jsonValue) {
		String ret = template;
		Matcher templateMatcher = patternTemplate.matcher(template);
		while (templateMatcher.find()) {
			String token = templateMatcher.group(1);
			Pattern valueTemplate = Pattern.compile("\"" + token + "\"\\s*:\\s*\"(.*)\"");
			Matcher valueMatcher = valueTemplate.matcher(jsonValue);
			if(valueMatcher.find()) {
				ret = ret.replace("${" + token + "}", valueMatcher.group(1)); 
			}
		}
		//TODO prefer use jsonpath 
		return ret;
	}
}
