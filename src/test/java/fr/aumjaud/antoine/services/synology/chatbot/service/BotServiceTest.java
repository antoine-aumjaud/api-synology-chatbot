package fr.aumjaud.antoine.services.synology.chatbot.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import fr.aumjaud.antoine.services.synology.chatbot.model.ChatBotResponse;
public class BotServiceTest {

	private BotService botService = new BotService();

	@Test
	public void buildChatBotResponse_should_parse_an_API_AI_complete_reponse() throws IOException, URISyntaxException {
		// Given
		String msg = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("api-ai_complete-response.json").toURI())));
System.out.println(msg);
		// When
		ChatBotResponse cbr = botService.buildChatBotResponse(msg);

		// Then
		assertNotNull(cbr);
		assertNotNull(cbr.getResult());
        assertFalse(cbr.getResult().isActionIncomplete());
        assertEquals("family-weight-set", cbr.getResult().getAction());
        assertEquals("xxx", cbr.getResult().getParameters());
    }

    @Test
    public void buildChatBotResponse_should_parse_an_API_AI_incomplete_reponse() throws IOException, URISyntaxException {
		// Given
		String msg = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("api-ai_incomplete-response.json").toURI())));

		// When
		ChatBotResponse cbr = botService.buildChatBotResponse(msg);

		// Then
		assertNotNull(cbr);
		assertNotNull(cbr.getResult());
        assertTrue(cbr.getResult().isActionIncomplete());
        assertNotNull(cbr.getResult().getFulfillment());
        assertEquals("xxx", cbr.getResult().getFulfillment().getSpeech());
    }

    @Test
    public void buildChatBotResponse_should_parse_an_API_AI_output_reponse() throws IOException, URISyntaxException {
		// Given
		String msg = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("api-ai_output-response.json").toURI())));

		// When
		ChatBotResponse cbr = botService.buildChatBotResponse(msg);

		// Then
		assertNotNull(cbr);
		assertNotNull(cbr.getResult());
        assertFalse(cbr.getResult().isActionIncomplete());
        assertTrue(cbr.getResult().getAction().contains("output"));
        assertNotNull(cbr.getResult().getFulfillment());
        assertEquals("xxx", cbr.getResult().getFulfillment().getSpeech());
    }
}