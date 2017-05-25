
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

	@Test
	public void checkSignature_should_check_a_valid_signature() {
		// Given
		String publicKey = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvtjdLkS+FP+0fPC09j25\ny/PiuYDDivIT86COVedvlElk99BBYTrqNaJybxjXbIZ1Q6xFNhOY+iTcBr4E1zJu\ntizF3Xi0V9tOuP/M8Wn4Y/1lCWbQKlWrNQuqNBmhovF4K3mDCYswVbpgTmp+JQYu\nBm9QMdieZMNry5s6aiMA9aSjDlNyedvSENYo18F+NYg1J0C0JiPYTxheCb4optr1\n5xNzFKhAkuGs4XTOA5C7Q06GCKtDNf44s/CVE30KODUxBi0MCKaxiXw/yy55zxX2\n/YdGphIyQiA5iO1986ZmZCLLW8udz9uhW5jUr3Jlp9LbmphAC61bVSf4ou2YsJaN\n0QIDAQAB\n-----END PUBLIC KEY-----";
		String payload = "{\"id\":235934504,\"repository\":{\"id\":13443111,\"name\":\"api-javalib\",\"owner_name\":\"antoine-aumjaud\",\"url\":null},\"number\":\"23\",\"config\":{\"language\":\"java\",\"jdk\":[\"oraclejdk8\"],\"os\":[\"linux\"],\"before_cache\":[\"rm -f  $HOME/.gradle/caches/modules-2/modules-2.lock\",\"rm -fr $HOME/.gradle/caches/*/plugin-resolution/\"],\"cache\":{\"directories\":[\"$HOME/.gradle/caches/\",\"$HOME/.gradle/wrapper/\"]},\"before_deploy\":[\"export RELEASE_PKG_FILE=$(ls build/libs/*.jar)\",\"echo \\\"deploying $RELEASE_PKG_FILE to GitHub releases\\\"\"],\"deploy\":{\"skip_cleanup\":true,\"provider\":\"releases\",\"api_key\":{\"secure\":\"pfMQC5ypmdwDT1GKUIpWqRR/IHtCBCd/FBhenSwTMv81scu/x9HRBs1afsz0A6ubkeMYexLet47nnWST+fqn8VAWNN/JbuOwMyCZ4S0jdbFJXAHGTl1l5odM0xCeE27vmYB34ss79MrjZkvss3aNXAiTd4Bm72P2QpjzEUGYpsWaKD9nI9RoKFljTooAFLr3JYbzZXX9s1us9sZ8ussQiv93T5A+1lTtHH4ay2XDU1/35dKlBIegY1xh8D4nBxVqAmSdAMHIO+rtT5XzjeU6qNI0mMvowlfxTuZah5JJ01tS9Z3dJ9hMfeVUs/UKsbKtDWRBLaKUiES15XZ/AgV4FaXC332A7OrhnmX2y+gUTPdth3o/otqNB2W1tniTg++okoUZaXkZAndsDlO9ID+U7KlH9lnMLRTOxWmcoJLB7YRHaOTGJtcXIujrs+PtYNmFEFTvmdl4EUgNej02WCl2ly6L/wW0hdMGZdSUotM1tUTjIp7lBwGpj6cPIvfdrns4vYJndT7Ya5DhS7ShfUd7M2iYtGthFWKMFcc1EpM/L1F5xAXEiA87j00cQlsUXeLf0cw+AbI6nSFN4T6T2uTH6mQq7k2MR2fSA9g4L4hZXRna7ciCkQYs1mbloiZYYH8p+uXmDmB0NSBg76pl8v7EkMTW/B5pGQ/QLcgfni15P+s=\"},\"file\":\"${RELEASE_PKG_FILE}\",\"true\":{\"tags\":true,\"repo\":\"antoine-aumjaud/api-javalib\"}},\"notifications\":{\"email\":{\"on_failure\":\"always\",\"on_sucess\":\"never\"},\"webhooks\":{\"urls\":[\"https://api-synology-chatbot.aumjaud.fr/send-travis/travis-ci\"],\"on_success\":\"always\",\"on_failure\":\"always\"}},\".result\":\"configured\",\"group\":\"stable\",\"dist\":\"precise\"},\"status\":0,\"result\":0,\"status_message\":\"Passed\",\"result_message\":\"Passed\",\"started_at\":\"2017-05-25T08:08:30Z\",\"finished_at\":\"2017-05-25T08:09:25Z\",\"duration\":55,\"build_url\":\"https://travis-ci.org/antoine-aumjaud/api-javalib/builds/235934504\",\"commit_id\":68359606,\"commit\":\"00c2608c862b1ee9c95de7199c3493e034aba034\",\"base_commit\":null,\"head_commit\":null,\"branch\":\"master\",\"message\":\":wrench: test travis signature\",\"compare_url\":\"https://github.com/antoine-aumjaud/api-javalib/compare/50043fb51dc8...00c2608c862b\",\"committed_at\":\"2017-05-25T08:08:20Z\",\"author_name\":\"Antoine Aumjaud\",\"author_email\":\"antoine_dev@aumjaud.fr\",\"committer_name\":\"Antoine Aumjaud\",\"committer_email\":\"antoine_dev@aumjaud.fr\",\"matrix\":[{\"id\":235934505,\"repository_id\":13443111,\"parent_id\":235934504,\"number\":\"23.1\",\"state\":\"finished\",\"config\":{\"language\":\"java\",\"jdk\":\"oraclejdk8\",\"os\":\"linux\",\"before_cache\":[\"rm -f  $HOME/.gradle/caches/modules-2/modules-2.lock\",\"rm -fr $HOME/.gradle/caches/*/plugin-resolution/\"],\"cache\":{\"directories\":[\"$HOME/.gradle/caches/\",\"$HOME/.gradle/wrapper/\"]},\"before_deploy\":[\"export RELEASE_PKG_FILE=$(ls build/libs/*.jar)\",\"echo \\\"deploying $RELEASE_PKG_FILE to GitHub releases\\\"\"],\"notifications\":{\"email\":{\"on_failure\":\"always\",\"on_sucess\":\"never\"},\"webhooks\":{\"urls\":[\"https://api-synology-chatbot.aumjaud.fr/send-travis/travis-ci\"],\"on_success\":\"always\",\"on_failure\":\"always\"}},\".result\":\"configured\",\"group\":\"stable\",\"dist\":\"precise\",\"addons\":{\"deploy\":{\"skip_cleanup\":true,\"provider\":\"releases\",\"api_key\":{\"secure\":\"pfMQC5ypmdwDT1GKUIpWqRR/IHtCBCd/FBhenSwTMv81scu/x9HRBs1afsz0A6ubkeMYexLet47nnWST+fqn8VAWNN/JbuOwMyCZ4S0jdbFJXAHGTl1l5odM0xCeE27vmYB34ss79MrjZkvss3aNXAiTd4Bm72P2QpjzEUGYpsWaKD9nI9RoKFljTooAFLr3JYbzZXX9s1us9sZ8ussQiv93T5A+1lTtHH4ay2XDU1/35dKlBIegY1xh8D4nBxVqAmSdAMHIO+rtT5XzjeU6qNI0mMvowlfxTuZah5JJ01tS9Z3dJ9hMfeVUs/UKsbKtDWRBLaKUiES15XZ/AgV4FaXC332A7OrhnmX2y+gUTPdth3o/otqNB2W1tniTg++okoUZaXkZAndsDlO9ID+U7KlH9lnMLRTOxWmcoJLB7YRHaOTGJtcXIujrs+PtYNmFEFTvmdl4EUgNej02WCl2ly6L/wW0hdMGZdSUotM1tUTjIp7lBwGpj6cPIvfdrns4vYJndT7Ya5DhS7ShfUd7M2iYtGthFWKMFcc1EpM/L1F5xAXEiA87j00cQlsUXeLf0cw+AbI6nSFN4T6T2uTH6mQq7k2MR2fSA9g4L4hZXRna7ciCkQYs1mbloiZYYH8p+uXmDmB0NSBg76pl8v7EkMTW/B5pGQ/QLcgfni15P+s=\"},\"file\":\"${RELEASE_PKG_FILE}\",\"true\":{\"tags\":true,\"repo\":\"antoine-aumjaud/api-javalib\"}}}},\"status\":0,\"result\":0,\"commit\":\"00c2608c862b1ee9c95de7199c3493e034aba034\",\"branch\":\"master\",\"message\":\":wrench: test travis signature\",\"compare_url\":\"https://github.com/antoine-aumjaud/api-javalib/compare/50043fb51dc8...00c2608c862b\",\"started_at\":\"2017-05-25T08:08:30Z\",\"finished_at\":\"2017-05-25T08:09:25Z\",\"committed_at\":\"2017-05-25T08:08:20Z\",\"author_name\":\"Antoine Aumjaud\",\"author_email\":\"antoine_dev@aumjaud.fr\",\"committer_name\":\"Antoine Aumjaud\",\"committer_email\":\"antoine_dev@aumjaud.fr\",\"allow_failure\":false}],\"type\":\"push\",\"state\":\"passed\",\"pull_request\":false,\"pull_request_number\":null,\"pull_request_title\":null,\"tag\":null}";
		String signatureB64 = "oNLYVGgdPk4kAoDKnoAeVxUn+ciGRdiGuAxFQnlzjtdBa/6ge0zvtXJvOx60cpOjbNo6vN277hf8j6wwr8+DWJsSGJLFZaps4Pbini1zafXv4t4szvQ+XCzvn6leOv+1DZu+kvmFeUEFaWvOxacrNB/RlseldYLYKfaPYAuPFUD5T460k9SoYxc5cloqXDf7YVdf1xUuehANIPOPsjKz4g+dOfBzsAQRcBX8OeUjZQqL+JByN3BmmXiSJBKW1gZhVyUqlnQJMpq55+IRev8dw+UeamY3+jdvCVJV+RyqCZfVzQBUP5jZxERr1HX8ECthHry9mk6jLfC8wu1yFKBNCQ==";

		// When
		botResource.checkSignature(publicKey, payload, signatureB64);

		// Then
		// nothing
	}

	@Test(expected = NoAccessException.class)
	public void checkSignature_should_throw_an_exception_if_signature_is_not_valid() {
		// Given
		String publicKey = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvtjdLkS+FP+0fPC09j25\ny/PiuYDDivIT86COVedvlElk99BBYTrqNaJybxjXbIZ1Q6xFNhOY+iTcBr4E1zJu\ntizF3Xi0V9tOuP/M8Wn4Y/1lCWbQKlWrNQuqNBmhovF4K3mDCYswVbpgTmp+JQYu\nBm9QMdieZMNry5s6aiMA9aSjDlNyedvSENYo18F+NYg1J0C0JiPYTxheCb4optr1\n5xNzFKhAkuGs4XTOA5C7Q06GCKtDNf44s/CVE30KODUxBi0MCKaxiXw/yy55zxX2\n/YdGphIyQiA5iO1986ZmZCLLW8udz9uhW5jUr3Jlp9LbmphAC61bVSf4ou2YsJaN\n0QIDAQAB\n-----END PUBLIC KEY-----";
		String payload = "{\"id\":235934504,\"repository\":{\"id\":13443111,\"name\":\"api-javalib\",\"owner_name\":\"antoine-aumjaud\",\"url\":null},\"number\":\"23\",\"config\":{\"language\":\"java\",\"jdk\":[\"oraclejdk8\"],\"os\":[\"linux\"],\"before_cache\":[\"rm -f  $HOME/.gradle/caches/modules-2/modules-2.lock\",\"rm -fr $HOME/.gradle/caches/*/plugin-resolution/\"],\"cache\":{\"directories\":[\"$HOME/.gradle/caches/\",\"$HOME/.gradle/wrapper/\"]},\"before_deploy\":[\"export RELEASE_PKG_FILE=$(ls build/libs/*.jar)\",\"echo \\\"deploying $RELEASE_PKG_FILE to GitHub releases\\\"\"],\"deploy\":{\"skip_cleanup\":true,\"provider\":\"releases\",\"api_key\":{\"secure\":\"pfMQC5ypmdwDT1GKUIpWqRR/IHtCBCd/FBhenSwTMv81scu/x9HRBs1afsz0A6ubkeMYexLet47nnWST+fqn8VAWNN/JbuOwMyCZ4S0jdbFJXAHGTl1l5odM0xCeE27vmYB34ss79MrjZkvss3aNXAiTd4Bm72P2QpjzEUGYpsWaKD9nI9RoKFljTooAFLr3JYbzZXX9s1us9sZ8ussQiv93T5A+1lTtHH4ay2XDU1/35dKlBIegY1xh8D4nBxVqAmSdAMHIO+rtT5XzjeU6qNI0mMvowlfxTuZah5JJ01tS9Z3dJ9hMfeVUs/UKsbKtDWRBLaKUiES15XZ/AgV4FaXC332A7OrhnmX2y+gUTPdth3o/otqNB2W1tniTg++okoUZaXkZAndsDlO9ID+U7KlH9lnMLRTOxWmcoJLB7YRHaOTGJtcXIujrs+PtYNmFEFTvmdl4EUgNej02WCl2ly6L/wW0hdMGZdSUotM1tUTjIp7lBwGpj6cPIvfdrns4vYJndT7Ya5DhS7ShfUd7M2iYtGthFWKMFcc1EpM/L1F5xAXEiA87j00cQlsUXeLf0cw+AbI6nSFN4T6T2uTH6mQq7k2MR2fSA9g4L4hZXRna7ciCkQYs1mbloiZYYH8p+uXmDmB0NSBg76pl8v7EkMTW/B5pGQ/QLcgfni15P+s=\"},\"file\":\"${RELEASE_PKG_FILE}\",\"true\":{\"tags\":true,\"repo\":\"antoine-aumjaud/api-javalib\"}},\"notifications\":{\"email\":{\"on_failure\":\"always\",\"on_sucess\":\"never\"},\"webhooks\":{\"urls\":[\"https://api-synology-chatbot.aumjaud.fr/send-travis/travis-ci\"],\"on_success\":\"always\",\"on_failure\":\"always\"}},\".result\":\"configured\",\"group\":\"stable\",\"dist\":\"precise\"},\"status\":0,\"result\":0,\"status_message\":\"Passed\",\"result_message\":\"Passed\",\"started_at\":\"2017-05-25T08:08:30Z\",\"finished_at\":\"2017-05-25T08:09:25Z\",\"duration\":55,\"build_url\":\"https://travis-ci.org/antoine-aumjaud/api-javalib/builds/235934504\",\"commit_id\":68359606,\"commit\":\"00c2608c862b1ee9c95de7199c3493e034aba034\",\"base_commit\":null,\"head_commit\":null,\"branch\":\"master\",\"message\":\":wrench: test travis signature\",\"compare_url\":\"https://github.com/antoine-aumjaud/api-javalib/compare/50043fb51dc8...00c2608c862b\",\"committed_at\":\"2017-05-25T08:08:20Z\",\"author_name\":\"Antoine Aumjaud\",\"author_email\":\"antoine_dev@aumjaud.fr\",\"committer_name\":\"Antoine Aumjaud\",\"committer_email\":\"antoine_dev@aumjaud.fr\",\"matrix\":[{\"id\":235934505,\"repository_id\":13443111,\"parent_id\":235934504,\"number\":\"23.1\",\"state\":\"finished\",\"config\":{\"language\":\"java\",\"jdk\":\"oraclejdk8\",\"os\":\"linux\",\"before_cache\":[\"rm -f  $HOME/.gradle/caches/modules-2/modules-2.lock\",\"rm -fr $HOME/.gradle/caches/*/plugin-resolution/\"],\"cache\":{\"directories\":[\"$HOME/.gradle/caches/\",\"$HOME/.gradle/wrapper/\"]},\"before_deploy\":[\"export RELEASE_PKG_FILE=$(ls build/libs/*.jar)\",\"echo \\\"deploying $RELEASE_PKG_FILE to GitHub releases\\\"\"],\"notifications\":{\"email\":{\"on_failure\":\"always\",\"on_sucess\":\"never\"},\"webhooks\":{\"urls\":[\"https://api-synology-chatbot.aumjaud.fr/send-travis/travis-ci\"],\"on_success\":\"always\",\"on_failure\":\"always\"}},\".result\":\"configured\",\"group\":\"stable\",\"dist\":\"precise\",\"addons\":{\"deploy\":{\"skip_cleanup\":true,\"provider\":\"releases\",\"api_key\":{\"secure\":\"pfMQC5ypmdwDT1GKUIpWqRR/IHtCBCd/FBhenSwTMv81scu/x9HRBs1afsz0A6ubkeMYexLet47nnWST+fqn8VAWNN/JbuOwMyCZ4S0jdbFJXAHGTl1l5odM0xCeE27vmYB34ss79MrjZkvss3aNXAiTd4Bm72P2QpjzEUGYpsWaKD9nI9RoKFljTooAFLr3JYbzZXX9s1us9sZ8ussQiv93T5A+1lTtHH4ay2XDU1/35dKlBIegY1xh8D4nBxVqAmSdAMHIO+rtT5XzjeU6qNI0mMvowlfxTuZah5JJ01tS9Z3dJ9hMfeVUs/UKsbKtDWRBLaKUiES15XZ/AgV4FaXC332A7OrhnmX2y+gUTPdth3o/otqNB2W1tniTg++okoUZaXkZAndsDlO9ID+U7KlH9lnMLRTOxWmcoJLB7YRHaOTGJtcXIujrs+PtYNmFEFTvmdl4EUgNej02WCl2ly6L/wW0hdMGZdSUotM1tUTjIp7lBwGpj6cPIvfdrns4vYJndT7Ya5DhS7ShfUd7M2iYtGthFWKMFcc1EpM/L1F5xAXEiA87j00cQlsUXeLf0cw+AbI6nSFN4T6T2uTH6mQq7k2MR2fSA9g4L4hZXRna7ciCkQYs1mbloiZYYH8p+uXmDmB0NSBg76pl8v7EkMTW/B5pGQ/QLcgfni15P+s=\"},\"file\":\"${RELEASE_PKG_FILE}\",\"true\":{\"tags\":true,\"repo\":\"antoine-aumjaud/api-javalib\"}}}},\"status\":0,\"result\":0,\"commit\":\"00c2608c862b1ee9c95de7199c3493e034aba034\",\"branch\":\"master\",\"message\":\":wrench: test travis signature\",\"compare_url\":\"https://github.com/antoine-aumjaud/api-javalib/compare/50043fb51dc8...00c2608c862b\",\"started_at\":\"2017-05-25T08:08:30Z\",\"finished_at\":\"2017-05-25T08:09:25Z\",\"committed_at\":\"2017-05-25T08:08:20Z\",\"author_name\":\"Antoine Aumjaud\",\"author_email\":\"antoine_dev@aumjaud.fr\",\"committer_name\":\"Antoine Aumjaud\",\"committer_email\":\"antoine_dev@aumjaud.fr\",\"allow_failure\":false}],\"type\":\"push\",\"state\":\"passed\",\"pull_request\":false,\"pull_request_number\":null,\"pull_request_title\":null,\"tag\":null}";
		String signatureB64 = "aaLYVGgdPk4kAoDKnoAeVxUn+ciGRdiGuAxFQnlzjtdBa/6ge0zvtXJvOx60cpOjbNo6vN277hf8j6wwr8+DWJsSGJLFZaps4Pbini1zafXv4t4szvQ+XCzvn6leOv+1DZu+kvmFeUEFaWvOxacrNB/RlseldYLYKfaPYAuPFUD5T460k9SoYxc5cloqXDf7YVdf1xUuehANIPOPsjKz4g+dOfBzsAQRcBX8OeUjZQqL+JByN3BmmXiSJBKW1gZhVyUqlnQJMpq55+IRev8dw+UeamY3+jdvCVJV+RyqCZfVzQBUP5jZxERr1HX8ECthHry9mk6jLfC8wu1yFKBNCQ==";

		// When
		botResource.checkSignature(publicKey, payload, signatureB64);

		// Then
		fail("Should throw an exception");
	}
}
