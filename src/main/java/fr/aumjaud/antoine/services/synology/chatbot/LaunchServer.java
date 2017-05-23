package fr.aumjaud.antoine.services.synology.chatbot;

import static spark.Spark.path;
import static spark.Spark.post;

import java.util.Properties;

import fr.aumjaud.antoine.services.common.server.SparkImplementation;
import fr.aumjaud.antoine.services.common.server.SparkLauncher;

public class LaunchServer {

	public static void main(String... args) {
		new SparkLauncher(new SparkImplementation() {

			private BotResource botResource = new BotResource();

			@Override
			public String getAppConfigName() {
				return "api-synology-chatbot.properties";
			}

			@Override
			public void setConfig(Properties appProperties) {
				botResource.setConfig(appProperties);
			}

			@Override
			public void initSpark(String securePath) {
				post("/receive", botResource::receiveMessage); //not secure by my secure-key... doesn't work (use the bot one)
				
				path(securePath, () -> {
					post("/receive/", botResource::receiveMessage); //let here for GUI API
					post("/send/:user", botResource::sendMessage);
				});
			}
		});
	}
}
