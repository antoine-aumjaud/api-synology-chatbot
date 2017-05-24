package fr.aumjaud.antoine.services.synology.chatbot.model;

public class TravisPayload {

	private String message;
	private String statusMessage;
	private String authorName;
	private int status;

	private TravisRepository repository;

	public String getMessage() {
		return message;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public String getAuthorName() {
		return authorName;
	}

	public TravisRepository getRepository() {
		return repository;
	}

	public int getStatus() {
		return status;
	}
	
	 
}
