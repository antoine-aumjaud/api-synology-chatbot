package fr.aumjaud.antoine.services.synology.chatbot;

import static spark.Spark.path;
import static spark.Spark.post;

import java.util.Properties;

import fr.aumjaud.antoine.services.common.server.spark.SparkImplementation;
import fr.aumjaud.antoine.services.common.server.spark.SparkLauncher;
import fr.aumjaud.antoine.services.synology.chatbot.requesthandler.BotResource;

public class LaunchServer {

	public static void main(String... args) {
		new SparkLauncher(new SparkImplementation() {

			private BotResource botResource = new BotResource();

			@Override
			public String getAppConfigName() {
				return "api-synology-chatbot.properties";
			}

			@Override
			public String getApiName() {
				return "api-synology-chatbot";
			}

			@Override
			public void setConfig(Properties appProperties) {
				botResource.setConfig(appProperties);
			}

			@Override
			public void initSpark(String securePath) {
				post("/send-travis/:user", botResource::sendTravisPayload); //not secure, use Travis signature 
				path(securePath, () -> {
					post("/receive/", "application/json", botResource::receiveMessage); //let last "/" (bug on syno chat integration)
					post("/send-message/:user", botResource::sendMessage);
				});
			}
		});
	}
}
