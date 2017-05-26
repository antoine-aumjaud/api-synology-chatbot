package fr.aumjaud.antoine.services.synology.chatbot.requesthandler;

import java.security.InvalidKeyException;
import java.util.Properties;

import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import fr.aumjaud.antoine.services.synology.chatbot.service.BotService;
import fr.aumjaud.antoine.services.synology.chatbot.service.TravisService;
import spark.Request;
import spark.Response;

public class BotResource {

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
	 * Receive a message
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

		// Read message
		String message = request.queryParams("text");
		if (message == null) {
			throw new WrongRequestException("missing text", "No message sent");
		}

		// Call service
		String ret = botService.receiveMessage(userToken, userName, message);

		// Build response
		return ret != null ? String.format("payload={\"text\": \"%s\"}", ret) : null;
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

		return sendMessage(request, response, message);
	}

	/**
	 * Send a message in the request body
	 */
	public String sendBody(Request request, Response response) {
		String message = request.body();
		if (message == null)
			throw new WrongRequestException("message is null", "Message to send is not present");

		return sendMessage(request, response, message);
	}
	
	
	/*
	 * PRIVATE
	 */

	/**
	 * Send an request extracted message 
	 */
	private String sendMessage(Request request, Response response, String message) {

		// Check parameters
		String userName = request.params("user");
		if (userName == null)
			throw new WrongRequestException("user is null", "User is not present");

		// Call service
		boolean messageSent = botService.sendMessage(userName, message);

		// Build response
		if (messageSent) {
			return "{\"status\"=\"sent\"}";
		} else {
			return "{\"status\"=\"error\"}";
		}
	}

}
