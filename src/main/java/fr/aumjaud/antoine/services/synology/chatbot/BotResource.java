package fr.aumjaud.antoine.services.synology.chatbot;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.PostResponse;
import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.SecurityHelper;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import spark.Request;
import spark.Response;

public class BotResource {

	private static Logger logger = LoggerFactory.getLogger(BotResource.class);

	private SecurityHelper securityHelper = new SecurityHelper();
	private HttpHelper httpHelper = new HttpHelper();
	private Properties properties;
	private List<String> validTokens;

	/**
	 * Set config 
	 * @param properties the config to set
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
			throw new WrongRequestException("missing token", "No tocken in query");
		}
		String userName = request.queryParams("username");
		if (userName == null) {
			throw new WrongRequestException("missing username", "");
		}

		// Check secure token
		if (!validTokens.contains(token)){
			throw new NoAccessException("unknown token", "Unknown Tocken: " + token);
		}

	
		//read message
		String message = request.queryParams("text");

		// token=xxx&channel_id=3&channel_name=&user_id=2&username=antoine
		// &post_id=12884901899&timestamp=1495375841841&text=test&trigger_word=test
//TODO conf token

		// Build response
		String payload = String.format("payload={\"text\": \"%s\"}", message);

		return "ok";
	}

	public String sendMessage(Request request, Response response) {
		
		// Check parameters
		String user = request.params("user");
		if (user == null)
			throw new WrongRequestException("user is null", "User is not present");
		String message = request.body();
		if (message == null)
			throw new WrongRequestException("message is null", "Message to send is not present");

		// Check configuration
		String token = properties.getProperty("token." + user);
		if (token == null)
			throw new WrongRequestException("unknown user", "user doesn't have a token set: " + user);
		String targetUrl = properties.getProperty("chat.url");
		if (targetUrl == null)
			throw new WrongRequestException("missing configuration", "chat.url not defined in configuration");

		// Build target URL
		targetUrl = String.format(targetUrl, token);

		// Controle parameters values
		if (message.contains("\")"))
			message = message.replace("\"", "\\\"");

		// Build payload
		String payload = String.format("payload={\"text\": \"%s\"}", message);

		PostResponse postResponse = httpHelper.postData(targetUrl, payload);
		if (postResponse != null) {
			logger.debug("Message {} sent to user {}, response: {}", message, user, postResponse);
			return "sent";
		} else {
			logger.error("Message {} NOT sent to user {}", message, user);
			return "error";
		}
	}
}
