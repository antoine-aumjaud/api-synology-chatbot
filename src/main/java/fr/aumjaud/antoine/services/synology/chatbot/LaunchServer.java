package fr.aumjaud.antoine.services.synology.chatbot;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.SparkBase.port;

public class LaunchServer {
	
    public static void main(String... args) {
        port(args.length > 0 ? Integer.parseInt(args[0]) : 9080); 

        get("/hi", (request, response) -> "hello");
        
        BotResource botResource = new BotResource();
        post("/message", "application/json", botResource::message);
    }
}
