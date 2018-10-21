package fr.aumjaud.antoine.services.synology.chatbot.requesthandler;

import java.security.InvalidKeyException;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import fr.aumjaud.antoine.services.synology.chatbot.model.ChatBotMessage;
import fr.aumjaud.antoine.services.synology.chatbot.service.BotService;
import fr.aumjaud.antoine.services.synology.chatbot.service.TravisService;
import spark.Request;
import spark.Response;

public class BotResource {
	private static final Gson GSON = new Gson();

	private BotService botService = new BotService();
	private TravisService travisService = new TravisService();

	/**
	 * Set config
	 * @param properties the config to set
	 * @return true if config set successfully
	 */
	public void setConfig(Properties properties) {
		travisService.setConfig(properties);
		botService.setConfig(properties);
	}

	/**
	 * Receive a message from chat input
	 */
	public String receiveMessage(Request request, Response response) {

		// Check parameters
		String userToken = request.queryParams("token");
		if (userToken == null) {
			throw new WrongRequestException("missing token", "No user token in query");
		}
		String userName = request.queryParams("username");
		if (userName == null) {
			throw new WrongRequestException("missing username", "No username defined");
		}
		String channelId = request.queryParams("channel_id");
		if (channelId == null) {
			throw new WrongRequestException("missing channelId", "No channel Id defined");
		}

		// Read message
		String message = request.queryParams("text");
		if (message == null) {
			throw new WrongRequestException("missing text", "No message sent");
		}

		// Call service
		return botService.receiveMessage(channelId, userToken, userName, message);
	}

	/**
	 * Send a message from Travis
	 */
	public String sendTravisPayload(Request request, Response response) throws InvalidKeyException {
		// Check params
		String payload = request.queryParams("payload");
		if (payload == null)
			throw new WrongRequestException("payload is null", "Payload to send is not present");

		String signatureB64 = request.headers("Signature");
		if (signatureB64 == null)
			throw new WrongRequestException("signature is null", "No signature send with payload");

		// Get controlled message
		String message = travisService.getMessage(payload, signatureB64);

		return sendMessage(request, response, message, null);
	}

	/**
	 * Send the message in the request body
	 */
	public String sendMessage(Request request, Response response) {
		ChatBotMessage chatBotMessage = getChatBotMessage(request);
		if (chatBotMessage == null)
			throw new WrongRequestException("message has not a json format", "Message to send has a wrong format");
		String message = chatBotMessage.getMessage();
		String url = chatBotMessage.getUrl();
		if (message == null || message.length() == 0)
			throw new WrongRequestException("message is null", "Message to send is not present");

		return sendMessage(request, response, message, url);
	}

	/*
	 * PRIVATE
	 */

	/**
	 * Send a request extracted message 
	 */
	private String sendMessage(Request request, Response response, String message, String url) {

		// Check parameters
		String userName = request.params("user");
		if (userName == null)
			throw new WrongRequestException("user is null", "User is not present");

		// Call service
		boolean messageSent = botService.sendMessage(userName, message, url);

		// Build response
		if (messageSent) {
			return "{\"status\"=\"sent\"}";
		} else {
			return "{\"status\"=\"error\"}";
		}
	}

	/**
	 * Get message form request
	 * @param request
	 * @return the message
	 */
	private ChatBotMessage getChatBotMessage(Request request) throws WrongRequestException {
		try {
			return GSON.fromJson(request.body(), ChatBotMessage.class);
		} 
		catch(JsonSyntaxException e) {
			throw new WrongRequestException("message has not a json format", "Message to send has a wrong format: " + request.body());
		}
	}
}
