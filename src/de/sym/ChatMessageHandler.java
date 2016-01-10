package de.sym;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.websocket.MessageHandler.Whole;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.mindrot.jbcrypt.BCrypt;

public class ChatMessageHandler implements Whole<String> {
	private RemoteEndpoint.Basic remote;
	private Connection connection;
	private int loginAttempts = 0;
	private boolean isLoggedIn = false;
	private String nickname;
	private Session session;
	private List<Session> sessionList;

	private enum MessageType {
		INVALID, // 0
		LOGIN, // 1
		LOGOUT, // 2
		MESSAGE, // 3
		DELETEBUDDY, // 4
		GETCONVERSATIONS // 5
	}

	public ChatMessageHandler(Session session, List<Session> sessionList, Connection connection) {
		this.remote = session.getBasicRemote();
		this.session = session;
		this.sessionList = sessionList;
		this.connection = connection;
	}

	@Override
	public void onMessage(String message) {
		JsonObject jsonObject = Json.createReader(new StringReader(message)).readObject();
		MessageType messageType;

		// Read message type
		try {
			messageType = intToMessageType(jsonObject.getInt("msgtype"));
		} catch (NullPointerException e) {
			System.err.println("Received invalid message without msgtype");
			try {
				remote.sendText("Missing msgtype");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}

		// Redirect message
		switch (messageType) {
		case LOGIN:
			handleLogin(jsonObject);
			break;

		case LOGOUT:
			// Do we really need this? I mean, if the client disconnects from
			// the WebSocket we'll notice in LoginServer's onClose() method
			System.err.println("logout message received");
			break;

		case MESSAGE:
			String msg = "Message received: " + message;
			System.err.println(msg);
			sendResponse(msg);
			break;

		case DELETEBUDDY:
			// Hier müssen wir noch überlegen was mit dem buddy passiert.
			// Im Client des Buddys muss die Freundschft ja auch gelöscht werden.
			// Wie soll er z.B. informiert werden?
			handleDeleteBuddy(jsonObject);
			break;
			
		case GETCONVERSATIONS:
			handleGetConversations(jsonObject);
			break;

		default:
			sendResponse("Received invalid msgtype");
			break;
		}
	}

	private void handleGetConversations(JsonObject jsonObject) {
		if(!isLoggedIn){
			sendResponse("Not logged in");
			return;
		}
		
		String sql = "SELECT title "
				+ "FROM chat, message "
				+ "WHERE chat.id = message.chat_id "
				+ "AND message.nickname = ?";
		try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			ResultSet resultSet = preparedStatement.executeQuery();
			
			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 5);
			
			JsonArrayBuilder conversations = Json.createArrayBuilder();
			while(resultSet.next()) {
				JsonObjectBuilder conversation = Json.createObjectBuilder();
				conversation.add("title", resultSet.getString(1));
				conversations.add(conversation);
			}
			response.add("conversations", conversations.build());
			sendResponse(response.build().toString());
			
		} catch (SQLException e) {
			System.err.println("Couldn't get conversations of user " + nickname);
			e.printStackTrace();
		}
	}

	/**
	 * Handles login
	 * 
	 * @param jsonObject
	 */
	private void handleLogin(JsonObject jsonObject) {
		// Max 3 login attempts, after that connection gets closed.
		if (loginAttempts >= 2) {
			try {
				sessionList.remove(this.session);
				session.close();
			} catch (IOException e) {
				System.err.println("Couldn't close session after 3 failed login attempts");
				e.printStackTrace();
			}
		}

		// Already logged in
		if (isLoggedIn) {
			sendResponse("Already logged in");
			return;
		}

		String plaintext;
		try {
			nickname = jsonObject.getString("nickname");
			plaintext = jsonObject.getString("password");
		} catch (NullPointerException e) {
			System.err.println("Login attempt with invalid user");
			sendResponse("Login failed");
			return;
		}

		String sql = "SELECT password, firstname, lastname, quotation FROM user WHERE nickname = ?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);

			ResultSet resultSet = preparedStatement.executeQuery();
			String passwordHash = null;
			String firstname = null;
			String lastname = null;
			String quotation = null;
			while (resultSet.next()) {
				passwordHash = resultSet.getString(1);
				firstname = resultSet.getString(2);
				lastname = resultSet.getString(3);
				quotation = resultSet.getString(4);
			}

			if (passwordHash != null) {
				if (BCrypt.checkpw(plaintext, passwordHash)) {
					isLoggedIn = true;

					JsonObjectBuilder response = Json.createObjectBuilder();
					response.add("msgtype", 1);
					response.add("nickname", nickname);
					response.add("firstname", firstname);
					response.add("lastname", lastname);
					response.add("quotation", quotation);
					response.add("friendslist", getFriendsList(nickname));

					sendResponse(response.build().toString());
				} else {
					sendResponse("Login failed");
				}
			} else {
				sendResponse("Login failed");
			}
			this.loginAttempts++;

		} catch (SQLException e) {
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}
	}
	private void handleDeleteBuddy(JsonObject jsonObject) {
		if(!isLoggedIn){
			sendResponse("Not logged in");
			return;
		}
		
		String buddyname = jsonObject.getString("buddyname");

		String sql = "DELETE FROM user_user WHERE (nickname1 =? AND nickname2 =?) OR (nickname2=? AND nickname1=?)";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			preparedStatement.setString(2, buddyname);
			preparedStatement.setString(3, nickname);
			preparedStatement.setString(4, buddyname);
			System.err.println(preparedStatement.toString());
			preparedStatement.execute();
		} catch (SQLException e) {
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}
	}

	private JsonArray getFriendsList(String nickname) {
		String sql = "SELECT nickname, quotation "
				+ "FROM user, user_user "
				+ "WHERE (user.nickname = user_user.nickname2 "
				+ "AND user_user.nickname1 = ?) "
				+ "OR (user.nickname = user_user.nickname1 "
				+ "AND user_user.nickname2 = ?) ";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, nickname);
			statement.setString(2, nickname);
			System.err.println(statement.toString());
			ResultSet resultSet = statement.executeQuery();
			JsonArrayBuilder friendslist = Json.createArrayBuilder();
			while (resultSet.next()) {
				JsonObjectBuilder currentLine = Json.createObjectBuilder();
				currentLine.add("nickname", resultSet.getString(1));
				currentLine.add("quotation", resultSet.getString(2));
				friendslist.add(currentLine);
			}

			return friendslist.build();

		} catch (SQLException e) {
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Sends message back to client
	 * 
	 * @param message
	 *            contains message to be sent to client
	 */
	private void sendResponse(String message) {
		try {
			remote.sendText(message);
		} catch (IOException e) {
			System.err.println("Couldn't send answer to client");
			e.printStackTrace();
		}
	}

	/**
	 * Identifies message type and returns MessageType
	 * 
	 * @param messageType
	 *            numeric number type
	 * @return msgType as MessageType
	 */
	private MessageType intToMessageType(int messageType) {
		switch (messageType) {
		case 1:
			return MessageType.LOGIN;
		case 2:
			return MessageType.LOGOUT;
		case 3:
			return MessageType.MESSAGE;
		case 4:
			return MessageType.DELETEBUDDY;
		case 5:
			return MessageType.GETCONVERSATIONS;
		default:
			return MessageType.INVALID;
		}
	}

}
