package fr.aumjaud.antoine.services.synology.chatbot;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import spark.Request;
import spark.Response;

public class BotResource {

	private static Logger logger = LoggerFactory.getLogger(BotResource.class);

	private Properties properties;
	private Gson gson;

	public BotResource(Properties properties) {
		this.properties = properties;

		GsonBuilder builder = new GsonBuilder();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);

		gson = builder.create();
	}

	public String message(Request request, Response response) {
		logger.info(request.body());
		return "ok";
	}

}
