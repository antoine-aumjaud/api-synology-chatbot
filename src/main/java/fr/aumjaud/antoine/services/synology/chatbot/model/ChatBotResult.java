package fr.aumjaud.antoine.services.synology.chatbot.model;

public class ChatBotResult {
    private String action;
    private boolean actionIncomplete;
    private Object parameters;
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
    public Object getParameters() {
        return parameters;
    }
    public String getJsonParameters() {
        return jsonParameters;
    }
    public void setJsonParameters(String jsonParameters) {
        this.jsonParameters = jsonParameters;
    }
}