package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.aumjaud.antoine.services.common.http.HttpCode;
import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.HttpResponse;
import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.SecurityHelper;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import fr.aumjaud.antoine.services.synology.chatbot.model.TravisPayload;

public class TravisService {

	//private static final Logger logger = LoggerFactory.getLogger(TravisService.class);

	private SecurityHelper securityHelper = new SecurityHelper();
	private HttpHelper httpHelper = new HttpHelper();

	private Properties properties;
	private Gson gson;

	public TravisService() {
		GsonBuilder builder = new GsonBuilder();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
		gson = builder.create();
	}

	/**
	 * Set config
	 * @param properties the config to set
	 * @return true if config set successfully
	 */
	public void setConfig(Properties properties) {
		this.properties = properties;
	}

	/**
	 * Get message from Travis payload and check its signature
	 * @param payload the Travis paylaod
	 * @param signatureB64 the payload signature
	 * @return the message controlled
	 */
	public String getMessage(String payload, String signatureB64) {
		// Get Travis public key
		String publicKeyStr = getTravisPublicKey();

		// Check signature
		securityHelper.checkSignature(publicKeyStr, payload, signatureB64);

		// Parse payload
		TravisPayload travisPayload = extractTravisPayload(payload);

		// Transform to message
		String message = buildTravisMessage(travisPayload);

		return message;
	}

	/*
	 * PRIVATE
	 */
	
	/**
	 * Load public key from Travis website
	 * @return the public key
	 */
	private String getTravisPublicKey() {
		//Load public key form Travis URL
		HttpResponse responsePublicKey = httpHelper.getData(properties.getProperty("travis.public-key.url"));
		if (responsePublicKey.getHttpCode() != HttpCode.OK)
			throw new NoAccessException("can't get public key", "Travis Public key is not accessible");

		//Extract public key value
		Pattern pattern = Pattern.compile(properties.getProperty("travis.public-key.regexp"));
		Matcher matcher = pattern.matcher(responsePublicKey.getContent());
		if (!matcher.find())
			throw new NoAccessException("can't find public key", "Can't find Travis public key");

		return matcher.group(1).replaceAll("\\\\n", "\n");
	}

	/**
	 * Parse payload
	 * @param payload the payload to parse
	 * @return a TravisPayload object
	 */
	TravisPayload extractTravisPayload(String payload) {
		return gson.fromJson(payload, TravisPayload.class);
	}
	
	/**
	 * Build message from Travis payload
	 * @param travisPayload the payload 
	 * @return the message
	 */
	String buildTravisMessage(TravisPayload travisPayload) {
		if (travisPayload.getRepository() == null)
			throw new WrongRequestException("payload is not well formed", "Payload has null value");

		String textMessage = travisPayload.getStatus() == 0 //
				? "Build success of %1$s" //
				: "Build <%5$s|%3$s> of %1$s: [%2$s] %4$s";
		return String.format(textMessage, travisPayload.getRepository().getName(), travisPayload.getAuthorName(), travisPayload.getStatusMessage(),
				travisPayload.getMessage(), travisPayload.getBuildUrl());
	}
}
