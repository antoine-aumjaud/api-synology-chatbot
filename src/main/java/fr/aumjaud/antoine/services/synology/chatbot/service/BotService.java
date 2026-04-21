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
			String url = extractUrlFromMessage(message);
			response = downloadService(url, userName);
		} 
		else {
			// TODO call AI with skills
			throw new UnsupportedOperationException("Message not supported: " + message);
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
	 * Extract URL from message starting with "get"
	 * @param message the full message
	 * @return the extracted URL, or null if not found
	 */
	private String extractUrlFromMessage(String message) {
		if (message == null || !message.startsWith("get")) {
			return null;
		}
		
		// Extract URL after "get " prefix
		String url = message.substring(3).trim();
		return url.isEmpty() ? null : url;
	}

	/**
	 * Service which download a file and send its to a share drive, then send the url to the chat
	 * @param url the URL to download
	 * @param userName the user requesting the download
	 * @return the string to send to the chat
	 */
	private String downloadService(String url, String userName) {
		// Validate URL
		if (url == null || url.trim().isEmpty()) {
			return "ERROR: No URL provided. Usage: get <url>";
		}

		try {
			// Check if it's a YouTube URL
			var isYoutubeUrl = url.matches("(?:https?://)?(?:www\\.)?(?:youtube\\.com|youtu\\.be).*");
			if (isYoutubeUrl) {
				logger.debug("YouTube URL detected: {}", url);
				String downloadedFileUrl = downloadYoutubeVideo(url, userName);
				if (downloadedFileUrl != null) {
					return "Video downloaded successfully: " + downloadedFileUrl;
				} else {
					return "ERROR: Failed to download YouTube video";
				}
			} else {
				return "ERROR: Only YouTube URLs are currently supported yet";
			}
		} catch (Exception e) {
			logger.error("Error downloading file from {}: {}", url, e.getMessage(), e);
			return "ERROR: " + e.getMessage();
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
			String cmd = properties.getProperty("youtube.mp3.download.command")
				.replace("\\$user", userName)
				.replace("\\$url", url);

			logger.debug("Executing command: {}", cmd);

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
					logger.debug("youtube-dl output: {}", line);
				}
			}

			// Wait for process completion
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				logger.error("Docker youtube-dl failed with exit code {}. Output: {}", exitCode, output);
				return null;
			}

			// Extract filename from output to build the complete URL
			String fileName = extractFileNameFromYoutubeDL(output.toString());
			if (fileName != null) {
				logger.info("YouTube video downloaded successfully. File name: {}", fileName);
				return fileName;
			}

			logger.warn("Could not extract filename from youtube-dl output");
			return null;
		} 
		catch (Exception e) {
			logger.error("Error during YouTube download: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Extract the downloaded filename from youtube-dl output
	 * @param output the youtube-dl command output
	 * @return the filename, or null if not found
	 */
	private String extractFileNameFromYoutubeDL(String output) {
		// youtube-dl typically outputs lines like: "Destination: filename.ext"
		Pattern pattern = Pattern.compile("Destination:\\s*(.+)");
		Matcher matcher = pattern.matcher(output);
		if (matcher.find()) {
			String path = matcher.group(1);
			// Extract just the filename from the full path
			return new java.io.File(path).getName();
		}
		return null;
	}


}
