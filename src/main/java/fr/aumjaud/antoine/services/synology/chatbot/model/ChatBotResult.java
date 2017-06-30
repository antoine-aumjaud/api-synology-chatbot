package fr.aumjaud.antoine.services.synology.chatbot.model;

import java.util.Map;

public class ChatBotResult {
    private String action;
    private boolean actionIncomplete;
    private Map<String, String> parameters;
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
    public String getJsonParameters() {
        return jsonParameters;
    }
    public void setJsonParameters(String jsonParameters) {
        this.jsonParameters = jsonParameters;
    }
}