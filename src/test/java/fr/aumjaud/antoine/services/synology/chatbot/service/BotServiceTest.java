package fr.aumjaud.antoine.services.synology.chatbot.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;

public class BotServiceTest {

	private BotService botService;
	private Properties properties;

	@Before
	public void setUp() {
		botService = new BotService();
		properties = new Properties();
		properties.setProperty("chat-tokens", "token1;token2");
		properties.setProperty("mp3.channel-id", "19");
		properties.setProperty("synology-chat.url", "http://synology/api?token=%s");
		properties.setProperty("token.testuser", "usertoken123");
		properties.setProperty("user.testuser.group", "1000");
		properties.setProperty("user.testuser.user", "1001");
		botService.setConfig(properties);
	}

	@Test
	public void testDownloadServiceReturnsImmediatelyForYoutubeUrl() {
		String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
		String result = botService.downloadService(url, "testuser");

		// Should immediately return this message, not wait for download
		assertTrue(result.contains("Video download started"));
		assertTrue(result.contains("You'll be notified when it's ready"));
	}

	@Test
	public void testDownloadServiceReturnsErrorForEmptyUrl() {
		String result = botService.downloadService("", "testuser");
		assertTrue(result.contains("ERROR: No URL provided"));
	}

	@Test
	public void testDownloadServiceReturnsErrorForNullUrl() {
		String result = botService.downloadService(null, "testuser");
		assertTrue(result.contains("ERROR: No URL provided"));
	}

	@Test
	public void testDownloadServiceReturnsErrorForNonYoutubeUrl() {
		String url = "https://www.example.com/document.pdf";
		String result = botService.downloadService(url, "testuser");
		assertTrue(result.contains("ERROR: Only YouTube URLs are currently supported"));
	}

	@Test
	public void testDownloadServiceDetectsVarioesYoutubeUrlFormats() {
		String[] youtubeUrls = {
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://youtu.be/dQw4w9WgXcQ",
			"http://youtube.com/watch?v=dQw4w9WgXcQ",
			"youtube.com/watch?v=dQw4w9WgXcQ"
		};

		for (String url : youtubeUrls) {
			String result = botService.downloadService(url, "testuser");
			assertTrue("URL format not detected: " + url, result.contains("Video download started"));
		}
	}

	@Test
	public void testSendMessageThrowsExceptionForUnknownUser() {
		try {
			botService.sendMessage("unknownuser", "Test message", null);
		} catch (WrongRequestException e) {
			assertTrue(e.getMessage().contains("user doesn't have a token set"));
		}
	}

	@Test
	public void testEchoService() {
		String result = botService.echoService();
		assertEquals("echo from bot service", result);
	}

	@Test
	public void testReceiveMessageWithEchoCommand() {
		String response = botService.receiveMessage("1", "token1", "testuser", "echo");
		assertTrue(response.contains("echo from bot service"));
	}

	@Test
	public void testReceiveMessageWithYoutubeUrlOnChannel19() {
		String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
		String response = botService.receiveMessage("19", "token1", "testuser", url);
		// Response should indicate async processing
		assertTrue(response.contains("Video download started"));
	}

}