package fr.aumjaud.antoine.services.synology.chatbot;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.PostResponse;
import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import fr.aumjaud.antoine.services.synology.chatbot.model.TravisPayload;
import spark.Request;
import spark.Response;

public class BotResource {

	private static Logger logger = LoggerFactory.getLogger(BotResource.class);

	private HttpHelper httpHelper = new HttpHelper();
	private Gson gson;

	private Properties properties;
	private List<String> validTokens;

	public BotResource() {
		GsonBuilder builder = new GsonBuilder();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
		gson = builder.create();
	}

	/**
	 * Set config
	 * 
	 * @param properties
	 *            the config to set
	 * @return true if config set successfully
	 */
	public boolean setConfig(Properties properties) {
		this.properties = properties;
		validTokens = Arrays.asList(properties.getProperty("chat-tokens").split(";"));
		return true;
	}

	public String receiveMessage(Request request, Response response) {

		// Check parameters
		String token = request.queryParams("token");
		if (token == null) {
			throw new WrongRequestException("missing token", "No token in query");
		}
		String userName = request.queryParams("username");
		if (userName == null) {
			throw new WrongRequestException("missing username", "No username defined");
		}

		// Check secure token
		if (!validTokens.contains(token)) {
			throw new NoAccessException("unknown token", "Unknown Tocken: " + token);
		}

		// Read message
		String message = request.queryParams("text");
		if (message == null) {
			throw new WrongRequestException("missing text", "No message sent");
		}

		logger.debug("Message received form {}: {}", userName, message);
		if (message.contains("key"))
			message = "ok";

		// Build response
		logger.debug("Response: {}", message);
		String payload = String.format("payload={\"text\": \"%s\"}", message);

		return payload;
	}

	///////////////////////////////////////////////////////////////////////////////////////
	public String sendTravisPayload(Request request, Response response) {
		String payload = request.queryParams("payload");
		if (payload == null)
			throw new WrongRequestException("payload is null", "Payload to send is not present");

		//Parse payload
		TravisPayload travisPayload = getTravisPayload(payload);
		
		//Transform to message
		String message = getTravisMessage(travisPayload);

		return sendMessage(request, response, message);
	}

	String getTravisMessage(TravisPayload travisPayload) {
		if (travisPayload.getRepository() ==null||travisPayload.getRepository().getName() == null || travisPayload.getAuthorName() == null
				|| travisPayload.getStatusMessage() == null || travisPayload.getMessage() == null
				|| travisPayload.getRepository().getUrl() == null)
			throw new WrongRequestException("payload is not well formed", "Payload has null value");

		String textMessage = travisPayload.getStatus() == 0 //
				? "Build success of %1$s" //
				: "Build <%5$s|%3$s> of %1$s: [%2$s] %4$s";
		return String.format(textMessage, travisPayload.getRepository().getName(),
				travisPayload.getAuthorName(), travisPayload.getStatusMessage(), travisPayload.getMessage(),
				travisPayload.getRepository().getUrl());
	}

	TravisPayload getTravisPayload(String message) {
		return gson.fromJson(message, TravisPayload.class);
	}

	///////////////////////////////////////////////////////////////////////////////////////
	public String sendBody(Request request, Response response) {
		String message = request.body();
		if (message == null)
			throw new WrongRequestException("message is null", "Message to send is not present");

		return sendMessage(request, response, message);
	}

	///////////////////////////////////////////////////////////////////////////////////////
	private String sendMessage(Request request, Response response, String message) {

		// Check parameters
		String user = request.params("user");
		if (user == null)
			throw new WrongRequestException("user is null", "User is not present");

		// Check configuration
		String token = properties.getProperty("token." + user);
		if (token == null)
			throw new WrongRequestException("unknown user", "user doesn't have a token set: " + user);
		String targetUrl = properties.getProperty("chat.url");
		if (targetUrl == null)
			throw new WrongRequestException("missing configuration", "chat.url not defined in configuration");

		// Build target URL
		targetUrl = String.format(targetUrl, token);

		// Escape message
		if (message.contains("\")")) {
			message = message.replace("\"", "\\\"");
		}

		// Build payload
		// (https://www.synology.com/en-us/knowledgebase/DSM/help/Chat/chat_integration)
		String payload = String.format("payload={\"text\": \"%s\"}", message);

		PostResponse postResponse = httpHelper.postData(targetUrl, payload);
		if (postResponse != null) {
			logger.debug("Message {} sent to user {}, response: {}", message, user, postResponse);
			return "{\"status\"=\"sent\"}";
		} else {
			logger.error("Message {} NOT sent to user {}", message, user);
			return "{\"status\"=\"error\"}";
		}
	}

}
