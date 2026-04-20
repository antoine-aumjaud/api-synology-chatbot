package fr.aumjaud.antoine.services.synology.chatbot.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Test;

import com.google.gson.JsonObject;

import fr.aumjaud.antoine.services.common.security.NoAccessException;

public class GithubServiceTest {

	private GithubService githubService = new GithubService();

	@Test
	public void extractGithubPayload_should_parse_a_workflow_notification() throws IOException, URISyntaxException {
		// Given
		String payload = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("github_workflow_notification.json").toURI())));

		// When
		JsonObject parsed = githubService.extractGithubPayload(payload);

		// Then
		assertNotNull(parsed);
		assertNotNull(parsed.get("repository"));
		assertEquals("antoine-aumjaud/api-synology-chatbot", parsed.get("repository").getAsString());
		assertEquals("failure", parsed.get("status").getAsString());
	}

	@Test
	public void buildGithubMessage_should_return_success_message_when_status_is_success() {
		// Given
		JsonObject payload = new JsonObject();
		payload.addProperty("repository", "antoine-aumjaud/api-synology-chatbot");
		payload.addProperty("workflow", "java build");
		payload.addProperty("status", "success");
		payload.addProperty("branch", "master");

		// When
		String msg = githubService.buildGithubMessage(payload);

		// Then
		assertEquals("Workflow success of antoine-aumjaud/api-synology-chatbot: java build (master)", msg);
	}

	@Test
	public void buildGithubMessage_should_return_failure_message_when_status_is_failure() {
		// Given
		JsonObject payload = new JsonObject();
		payload.addProperty("repository", "antoine-aumjaud/api-synology-chatbot");
		payload.addProperty("workflow", "java build");
		payload.addProperty("status", "failure");
		payload.addProperty("branch", "master");
		payload.addProperty("url", "https://github.com/antoine-aumjaud/api-synology-chatbot/actions/runs/42");
		payload.addProperty("message", "Tests failed");

		// When
		String msg = githubService.buildGithubMessage(payload);

		// Then
		assertEquals("Workflow <https://github.com/antoine-aumjaud/api-synology-chatbot/actions/runs/42|failure> of antoine-aumjaud/api-synology-chatbot: java build (master) - Tests failed", msg);
	}

	@Test
	public void getMessage_should_validate_signature_and_return_message() throws Exception {
		// Given
		String payload = "{\"repository\":\"antoine-aumjaud/api-synology-chatbot\",\"workflow\":\"java build\",\"status\":\"success\",\"branch\":\"master\"}";
		String secret = "top-secret";
		Properties properties = new Properties();
		properties.setProperty("github.webhook.secret", secret);
		githubService.setConfig(properties);
		String signature = "sha256=" + computeHmacSha256Hex(payload, secret);

		// When
		String msg = githubService.getMessage(payload, signature);

		// Then
		assertTrue(msg.contains("Workflow success"));
	}

	@Test(expected = NoAccessException.class)
	public void getMessage_should_reject_payload_when_signature_is_invalid() {
		// Given
		String payload = "{\"repository\":\"antoine-aumjaud/api-synology-chatbot\",\"workflow\":\"java build\",\"status\":\"failure\",\"branch\":\"master\"}";
		Properties properties = new Properties();
		properties.setProperty("github.webhook.secret", "top-secret");
		githubService.setConfig(properties);

		// When
		githubService.getMessage(payload, "sha256=deadbeef");

		// Then
		fail("should has thrown an exception");
	}

	private String computeHmacSha256Hex(String payload, String secret) throws Exception {
		Mac sha256Hmac = Mac.getInstance("HmacSHA256");
		sha256Hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] digest = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder(digest.length * 2);
		for (byte b : digest) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}
}
