package de.sym;

public class User {
	private String nickname;
	private String firstname;
	private String lastname;

	public User(String nickname, String firstname, String lastname) {
		this.nickname = nickname;
		this.firstname = firstname;
		this.lastname = lastname;
	}

	public User() {
		this.nickname = this.firstname = this.lastname = "null";
	}

	public String getNickname() {
		return this.nickname;
	}

	public String toJsonString() {
		return "{\"nickname\": \"" + nickname + "\", \"firstname\": \"" + firstname + "\", \"lastname\": \"" + lastname
				+ "\"};";
	}
}
