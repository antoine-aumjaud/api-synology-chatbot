package fr.aumjaud.antoine.services.synology.chatbot.api;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.SparkBase.port;

public class LaunchServer {
	
    public static void main(String[] args) {
        port(9981); 

        BotResource botResource = new BotResource();
        get("/hi", botResource::sayHello);
        
        post("/message", "application/json", botResource::message);
    }
}
