package fr.aumjaud.antoine.services.synology.chatbot;

import spark.Request;
import spark.Response;

public class BotResource {
 
    
    public String sayHello(Request request, Response response) {
        return "hello";
    }
    public String message(Request request, Response response) {
        return "to implement";
    }
    
    
}
