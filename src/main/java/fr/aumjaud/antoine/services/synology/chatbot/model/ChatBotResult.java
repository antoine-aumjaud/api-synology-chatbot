package fr.aumjaud.antoine.services.synology.chatbot.model;

public class ChatBotResult {
    private String action;
    private boolean actionIncomplete;
    private Object parameters;

    private ChatBotFulfillment fulfillment;

    public String getAction() {
        return action;
    }
    public boolean isActionIncomplete() {
        return actionIncomplete;
    }
    public Object getParameters() {
        return parameters;
    }
    public ChatBotFulfillment getFulfillment() {
        return fulfillment;
    }
}