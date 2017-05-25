package fr.aumjaud.antoine.services.synology.chatbot;

import java.io.IOException;
import java.io.StringReader;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.aumjaud.antoine.services.common.http.HttpCode;
import fr.aumjaud.antoine.services.common.http.HttpHelper;
import fr.aumjaud.antoine.services.common.http.HttpResponse;
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
		Security.addProvider(new BouncyCastleProvider());

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
	public String sendTravisPayload(Request request, Response response) throws InvalidKeyException {
		String payload = request.queryParams("payload");
		if (payload == null)
			throw new WrongRequestException("payload is null", "Payload to send is not present");

		String signatureB64 = request.headers("Signature");
		if (signatureB64 == null)
			throw new WrongRequestException("signature is null", "No signature send with payload");

		// Obtain the public key corresponding to the private key that signed the payload.
		String publicKeyStr = getTravisPublicKey();
		
		// Check signature
		checkSignature(publicKeyStr, payload, signatureB64);

		// Parse payload
		TravisPayload travisPayload = getTravisPayload(payload);

		// Transform to message
		String message = getTravisMessage(travisPayload);

		return sendMessage(request, response, message);
	}

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

	void checkSignature(String publicKeyStr, String payload, String signatureB64) {
		try {
			// Obtain the Signature header value, and base64-decode it.
			byte[] signature = Base64.getDecoder().decode(signatureB64);

			// Load public key
			byte[] keyContent;
			try (PemReader pemReader = new PemReader(new StringReader(publicKeyStr))) {
				keyContent = pemReader.readPemObject().getContent();
			}
			PublicKey publicKey = KeyFactory.getInstance("RSA", "BC")
					.generatePublic(new X509EncodedKeySpec(keyContent));

			// Verify the signature using the public key and SHA1 digest.
			Signature sig = Signature.getInstance("SHA1withRSA");
			sig.initVerify(publicKey);
			sig.update(payload.getBytes("utf-8"));
			if (!sig.verify(signature))
				throw new NoAccessException("signature is not valid", "Signature is not valid");

		} catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException | SignatureException
				| IOException | NoSuchProviderException e) {
			logger.error("Exception during check signature", e);
			throw new NoAccessException("signature error", "Can't check signature");
		}
	}

	String getTravisMessage(TravisPayload travisPayload) {
		if (travisPayload.getRepository() == null)
			throw new WrongRequestException("payload is not well formed", "Payload has null value");

		String textMessage = travisPayload.getStatus() == 0 //
				? "Build success of %1$s" //
				: "Build <%5$s|%3$s> of %1$s: [%2$s] %4$s";
		return String.format(textMessage, travisPayload.getRepository().getName(), travisPayload.getAuthorName(),
				travisPayload.getStatusMessage(), travisPayload.getMessage(), travisPayload.getRepository().getUrl());
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

		HttpResponse postResponse = httpHelper.postData(targetUrl, payload);
		if (postResponse != null) {
			logger.debug("Message '{}' sent to user {}, response: {}", message, user, postResponse);
			return "{\"status\"=\"sent\"}";
		} else {
			logger.error("Message '{}' NOT sent to user {}", message, user);
			return "{\"status\"=\"error\"}";
		}
	}

}
