package fr.aumjaud.antoine.services.synology.chatbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;

public class GithubService {

	//private static final Logger logger = LoggerFactory.getLogger(GithubService.class);

	private static final Gson GSON = new Gson();

	private Properties properties;

	/**
	 * Set config
	 * @param properties the config to set
	 */
	public void setConfig(Properties properties) {
		this.properties = properties;
	}

	/**
	 * Get message from GitHub payload and check its signature.
	 * @param payload the GitHub payload
	 * @param signatureSha256 the payload signature (header X-Hub-Signature-256)
	 * @return the controlled message
	 */
	public String getMessage(String payload, String signatureSha256) {
		String secret = properties.getProperty("github.webhook.secret");
		if (secret == null || secret.length() == 0)
			throw new WrongRequestException("missing configuration", "github.webhook.secret not defined in configuration");

		if (!isValidSignature(payload, signatureSha256, secret))
			throw new NoAccessException("wrong signature", "GitHub payload signature is invalid");

		JsonObject githubPayload = extractGithubPayload(payload);
		return buildGithubMessage(githubPayload);
	}

    /*
     * PRIVATE
     */
	JsonObject extractGithubPayload(String payload) {
		JsonElement parsed = new JsonParser().parse(payload);
		if (parsed == null || !parsed.isJsonObject())
			throw new WrongRequestException("payload is not well formed", "GitHub payload is not a JSON object");

		return parsed.getAsJsonObject();
	}

	String buildGithubMessage(JsonObject githubPayload) {
		String repository = valueOrDefault(getFromPath(githubPayload, "repository", "full_name"),
				getFromPath(githubPayload, "repository", "name"),
				getString(githubPayload, "repository"),
				"unknown-repository");

		String workflow = valueOrDefault(getFromPath(githubPayload, "workflow_run", "name"),
				getString(githubPayload, "workflow"),
				"workflow");

		String status = valueOrDefault(getFromPath(githubPayload, "workflow_run", "conclusion"),
				getFromPath(githubPayload, "workflow_run", "status"),
				getString(githubPayload, "status"),
				"unknown").toLowerCase(Locale.ROOT);

		String branch = valueOrDefault(getFromPath(githubPayload, "workflow_run", "head_branch"),
				getString(githubPayload, "branch"),
				"unknown-branch");

		String url = valueOrDefault(getFromPath(githubPayload, "workflow_run", "html_url"),
				getString(githubPayload, "url"),
				null);

		String commitMessage = valueOrDefault(getFromPath(githubPayload, "workflow_run", "head_commit", "message"),
				getString(githubPayload, "message"),
				null);

		String workflowSummary = String.format("%s (%s)", workflow, branch);
		if ("success".equals(status) || "passed".equals(status)) {
			return String.format("Workflow success of %s: %s", repository, workflowSummary);
		}

		String workflowStatus = (url == null || url.length() == 0)
				? status
				: String.format("<%s|%s>", url, status);
		String textMessage = String.format("Workflow %s of %s: %s", workflowStatus, repository, workflowSummary);
		if (commitMessage != null && commitMessage.length() > 0) {
			textMessage = textMessage + " - " + commitMessage;
		}
		return textMessage;
	}

	boolean isValidSignature(String payload, String signatureSha256, String secret) {
		if (signatureSha256 == null || signatureSha256.length() == 0)
			throw new WrongRequestException("signature is null", "No GitHub signature sent with payload");
		if (!signatureSha256.startsWith("sha256="))
			throw new WrongRequestException("wrong signature format", "X-Hub-Signature-256 must start with sha256=");

		String expectedHex = computeHmacSha256Hex(payload, secret);
		String signedHex = signatureSha256.substring("sha256=".length());
		byte[] expectedBytes = expectedHex.getBytes(StandardCharsets.UTF_8);
		byte[] signedBytes = signedHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedBytes, signedBytes);
	}

	private String computeHmacSha256Hex(String payload, String secret) {
		try {
			Mac sha256Hmac = Mac.getInstance("HmacSHA256");
			sha256Hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception e) {
			throw new WrongRequestException("signature error", "Cannot compute GitHub payload signature: " + e.getMessage());
		}
	}

	private String getFromPath(JsonObject payload, String... fields) {
		JsonElement current = payload;
		for (String field : fields) {
			if (current == null || !current.isJsonObject())
				return null;
			JsonObject obj = current.getAsJsonObject();
			current = obj.get(field);
		}
		if (current == null || current.isJsonNull())
			return null;
		if (current.isJsonPrimitive())
			return current.getAsString();
		return GSON.toJson(current);
	}

	private String getString(JsonObject payload, String field) {
		JsonElement value = payload.get(field);
		if (value == null || value.isJsonNull())
			return null;
		if (!value.isJsonPrimitive())
			return null;
		return value.getAsString();
	}

	private String valueOrDefault(String... values) {
		for (String value : values) {
			if (value != null && value.length() > 0) {
				return value;
			}
		}
		return null;
	}
}
