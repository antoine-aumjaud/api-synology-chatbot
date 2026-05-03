package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
	private ExecutorService executorService = Executors.newFixedThreadPool(5);

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
		else if (channelId.equals(properties.getProperty("mp3.channel-id"))) {
			response = downloadService(message, userName);
		} 
		else {
			// TODO call AI with skills
			throw new UnsupportedOperationException("Message not supported: " + message);
		}
		logger.info("Response to Chat: {}", response);
		String payload = buildSynologyChatPayload(response, null);
		logger.debug("Payload to Chat: {}", payload);
		return payload;
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
	protected String echoService() {
		return "echo from bot service";
	}


	/**
	 * Service which download a file and send its to a share drive, then send the url to the chat
	 * @param url the URL to download
	 * @param userName the user requesting the download
	 * @return the string to send to the chat
	 */
	protected String downloadService(String url, String userName) {
		// Validate URL
		if (url == null || url.trim().isEmpty()) {
			return "ERROR: No URL provided. Usage: get <url>";
		}

		try {
			// Check if it's a YouTube URL
			var isYoutubeUrl = url.matches("(?:https?://)?(?:www\\.)?(?:youtube\\.com|youtu\\.be).*");
			if (isYoutubeUrl) {
				logger.debug("YouTube URL detected: {}", url);
				// Launch download asynchronously and return immediately
				executorService.submit(() -> downloadYoutubeVideoAsync(url, userName));
				return "Video download started. You'll be notified when it's ready.";
			} else {
				return "ERROR: Only YouTube URLs are currently supported yet";
			}
		} catch (Exception e) {
			logger.error("Error downloading file from {}: {}", url, e.getMessage(), e);
			return "ERROR: " + e.getMessage();
		}
	}

	/**
	 * Asynchronously download a YouTube video and notify the user when complete
	 * @param url the YouTube URL
	 * @param userName the user requesting the download
	 */
	private void downloadYoutubeVideoAsync(String url, String userName) {
		try {
			String result = downloadYoutubeVideo(url, userName);
			if (result != null && !result.startsWith("ERROR")) {
				sendMessage(userName, "✅ " + result, null);
			} else {
				sendMessage(userName, "❌ " + (result != null ? result : "Failed to download YouTube video"), null);
			}
		} catch (Exception e) {
			logger.error("Error during async YouTube download for user {}: {}", userName, e.getMessage(), e);
			sendMessage(userName, "❌ Error: " + e.getMessage(), null);
		}
	}

	/**
	 * Download a YouTube video using youtube-dl in a Docker container
	 * @param url the YouTube URL
	 * @param userName the user requesting the download
	 * @return the URL to the downloaded file, or null if download failed
	 */
	private String downloadYoutubeVideo(String url, String userName) {
		try {
			logger.info("Starting YouTube download for URL: {}", url);
			String gid = properties.getProperty("user." + userName + ".group");
			String uid = properties.getProperty("user." + userName + ".user");
			String cmd = properties.getProperty("youtube.mp3.download.command")
				.replace("$user", userName)
				.replace("$url", url)
				.replace("$gid", gid)
				.replace("$uid", uid);
			logger.info("Executing command: {}", cmd);

			// Execute the docker command
			ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			// Read command output
			StringBuilder output = new StringBuilder();
			try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
					logger.debug("youtube download output: {}", line);
				}
			}

			// Wait for process completion
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				logger.error("YouTube download failed with exit code {}. Output: {}", exitCode, output);
				return "YouTube download failed";
			}

			// Extract filename from output to build the complete URL
			logger.info("YouTube downloaded successfully. Output: {}", output);
			return "YouTube downloaded successfully. Check the downloaded file in your drive.";
		} 
		catch (Exception e) {
			logger.error("Error during YouTube download: {}", e.getMessage(), e);
			return "ERROR: " + e.getMessage();
		}
	}

}
