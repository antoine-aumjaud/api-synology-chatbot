package fr.aumjaud.antoine.services.synology.chatbot;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import spark.Request;
import spark.Response;

public class BotResource {

	private static Logger logger = LoggerFactory.getLogger(BotResource.class);

	private Properties properties;
	private Gson gson;

	public BotResource(Properties properties) {
		this.properties = properties;

		GsonBuilder builder = new GsonBuilder();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);

		gson = builder.create();
	}

	public String receiveMessage(Request request, Response response) {
		logger.info(request.body());

		
		return "ok";
	}

	public String sendMessage(Request request, Response response) {
		return sendMessage(request.params("user"), request.body()) ? "send" : "error";
	}

	private boolean sendMessage(String user, String message) {
		// Check parameters
		if (user == null)
			throw new IllegalArgumentException("user is null");
		if (message == null)
			throw new IllegalArgumentException("message is null");

		// Check configuration
		String token = properties.getProperty("token." + user);
		if (token == null)
			throw new IllegalStateException("user doesn't have a token set: " + user);
		String configUrl = properties.getProperty("chat.url");
		if (configUrl == null)
			throw new IllegalStateException("chat.url not defined in configuration");

		// Controle parameters values
		if (message.contains("\")"))
			message = message.replace("\"", "\\\"");

		// Build payload
		String payload = String.format("payload={\"text\": \"%s\"}", message);

		// POST message to an URL
		byte[] postData = payload.getBytes(StandardCharsets.UTF_8);
		try {
			URL url = new URL(String.format(configUrl, token));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setUseCaches(false);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
			conn.setRequestMethod("POST");
			try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
				dos.write(postData);
				logger.debug("Message {} sent to user {}, response: ", message, user, conn.getResponseMessage());
				return true;
			} catch (IOException e) {
				logger.error("Can't write message", e);
			}
		} catch (ProtocolException e) {
			logger.error("Can't POST message", e);
		} catch (IOException e) {
			logger.error("Error while write message", e);
		}
		logger.error("Message {} NOT sent to user {}", message, user);
		return false;

	}

}
