package fr.aumjaud.antoine.services.synology.chatbot.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChatBotResult {
    private String action;
    private boolean actionIncomplete;
    
    private Map<String, String> parameters;
    private ChatBotContext[] contexts;
    private String jsonParameters;

    private ChatBotFulfillment fulfillment;

    public String getAction() {
        return action;
    }
    public boolean isActionIncomplete() {
        return actionIncomplete;
    }
    public ChatBotFulfillment getFulfillment() {
        return fulfillment;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }
    public Map<String, String> getContextsParameters() {
        Map<String, String> contextsParameters = new HashMap<>();
        if(contexts != null && contexts.length > 0)
            Arrays.stream(contexts).forEach((context) -> { if(context.getParameters() != null) contextsParameters.putAll(context.getParameters()); });
        return contextsParameters;
    }
    public Map<String, String> getAllParameters() {
        Map<String, String> allParameters = new HashMap<>();
        allParameters.putAll(parameters);
        allParameters.putAll(getContextsParameters());
        return allParameters;
    }


    public String getJsonAllParameters() {
        return jsonParameters;
    }
    public void setJsonAllParameters(String jsonParameters) {
        this.jsonParameters = jsonParameters;
    }
}