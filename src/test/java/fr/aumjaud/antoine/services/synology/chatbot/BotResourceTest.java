
package fr.aumjaud.antoine.services.synology.chatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import fr.aumjaud.antoine.services.common.security.NoAccessException;
import fr.aumjaud.antoine.services.common.security.WrongRequestException;
import fr.aumjaud.antoine.services.synology.chatbot.model.TravisPayload;
import fr.aumjaud.antoine.services.synology.chatbot.model.TravisRepository;

public class BotResourceTest {

	private Properties properties;
	private BotResource botResource = new BotResource();

	@Before
	public void init() {
		properties = new Properties();
		properties.put("chat-tokens", "");
		botResource.setConfig(properties);
	}

	@Test
	public void getTravisPayload_should_parse_an_docker_webhook_info() throws IOException, URISyntaxException {
		// Given
		String msg = new String(
				Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("travis_webhook.json").toURI())));

		// When
		TravisPayload dpd = botResource.getTravisPayload(msg);

		// Then
		assertNotNull(dpd.getRepository());
		assertEquals("minimal", dpd.getRepository().getName());
		assertEquals("http://github.com/svenfuchs/minimal", dpd.getRepository().getUrl());
		assertEquals("the commit message", dpd.getMessage());
		assertEquals(0, dpd.getStatus());
		assertEquals("Passed", dpd.getStatusMessage());
		assertEquals("Sven Fuchs", dpd.getAuthorName());
	}

	@Test
	public void getTravisPayload_should_return_a_success_message_is_payoff_says_it_is_a_sucess()
			throws IOException, URISyntaxException {
		// Given
		TravisPayload payload = new TravisPayload() {
			public String getMessage() {
				return "commit message";
			};

			public TravisRepository getRepository() {
				return new TravisRepository() {
					public String getName() {
						return "repo_name";
					};

					public String getUrl() {
						return "http://repo_url";
					};
				};
			}

			public String getAuthorName() {
				return "aa";
			};

			public int getStatus() {
				return 0;
			};

			public String getStatusMessage() {
				return "status";
			};
		};

		// When
		String msg = botResource.getTravisMessage(payload);

		// Then
		assertEquals("Build success of repo_name", msg);
	}

	@Test
	public void getTravisPayload_should_return_a_failed_message_is_payoff_says_it_is_a_failed()
			throws IOException, URISyntaxException {
		// Given
		TravisPayload payload = new TravisPayload() {
			public String getMessage() {
				return "commit message";
			};

			public TravisRepository getRepository() {
				return new TravisRepository() {
					public String getName() {
						return "repo_name";
					};

					public String getUrl() {
						return "http://repo_url";
					};
				};
			}

			public String getAuthorName() {
				return "aa";
			};

			public int getStatus() {
				return 1;
			};

			public String getStatusMessage() {
				return "status";
			};
		};

		// When
		String msg = botResource.getTravisMessage(payload);

		// Then
		assertEquals("Build <http://repo_url|status> of repo_name: [aa] commit message", msg);
	}

	@Test(expected = WrongRequestException.class)
	public void getTravisPayload_should_throw_an_exception_if_missing_values_in_payoff()
			throws IOException, URISyntaxException {
		// Given
		TravisPayload payload = new TravisPayload();

		// When
		botResource.getTravisMessage(payload);

		// Then
		fail("should has thrown an exception");
	}

}
