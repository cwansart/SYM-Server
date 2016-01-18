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
	private List<ChatMessageHandler> messageHandlerList;

	private enum MessageType {
		INVALID, // 0
		LOGIN, // 1
		MESSAGE, // 2
		DELETEBUDDY, // 3
		GETCONVERSATIONS, // 4
		GETMESSAGES, // 5
		SENDMESSAGE // 6
	}

	public ChatMessageHandler(Session session, List<Session> sessionList, List<ChatMessageHandler> messageHandlerList, Connection connection) {
		this.remote = session.getBasicRemote();
		this.session = session;
		this.sessionList = sessionList;
		this.messageHandlerList = messageHandlerList;
		this.connection = connection;
	}
	
	String getNickname() {
		return nickname;
	}

	@Override
	public void onMessage(String message) {
		System.err.println("MESSAGE: " + message);
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
			
		case GETMESSAGES:
			handleGetMessages(jsonObject);
			break;
			
		case SENDMESSAGE:
			handleSendMessage(jsonObject);
			break;

		default:
			sendResponse("Received invalid msgtype");
			break;
		}
	}

	private void handleSendMessage(JsonObject jsonObject) {
		if(!isLoggedIn) {
			sendResponse("Not logged in");
			return;
		}
		
		int chatId;
		String message;
		try {
			chatId = jsonObject.getInt("chatid");
			message = jsonObject.getString("message");
		} catch(NullPointerException e) {
			System.err.println("Message didn't contain a chat id or message");
			sendResponse("Couldn't send message. Missing ID or message-");
			return;
		}
		
		// Insert message into database
		String sql = "INSERT INTO message (nickname, chat_id, content) VALUES (?, ?, ?)";
		try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			preparedStatement.setInt(2, chatId);
			preparedStatement.setString(3, message);
			preparedStatement.executeUpdate();
		} catch (SQLException e) {
			System.err.println("Couldn't send the message.");
			sendResponse("Couldn't send message.");
			e.printStackTrace();
			return;
		}
		
		// Get all users of the current chat to iterate over and notify users of new messages.
		sql = "SELECT nickname FROM chat_user WHERE chat_id = 6"; // AND nickname != ?"; // zu Debugging-Zwecken auskommentiert
		try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			ResultSet resultSet = preparedStatement.executeQuery();
			
			while(resultSet.next()) {
				String nickname2 = resultSet.getString(1);
				for(ChatMessageHandler messageHandler: messageHandlerList) {
					if(messageHandler.getNickname().equals(nickname2)) {
						JsonObjectBuilder response = Json.createObjectBuilder();
						response.add("msgtype", 6);
						response.add("chatid", chatId);
						response.add("author", nickname);
						response.add("message", message);
						messageHandler.sendResponse(response.build().toString());
					}
				}
			}
		} catch (SQLException e) {
			sendResponse("Couldn't send message.");
			System.err.println("Could't send message due to errors");
			e.printStackTrace();
		}
	}

	private void handleGetMessages(JsonObject jsonObject) {
		if(!isLoggedIn){
			sendResponse("Not logged in");
			return;
		}
		
		int id;
		try {
			id = jsonObject.getInt("chatid");
		} catch(NullPointerException e) {
			System.err.println("Message didn't contain a chat id");
			sendResponse("Couldn't get message. Unknown ID");
			return;
		}
		
		String sql = "SELECT id, nickname, date, content FROM `message` WHERE chat_id =?";
		try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setInt(1,  id);
			ResultSet resultSet = preparedStatement.executeQuery();
			
			
			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 5);
			response.add("id", id);
			
			JsonArrayBuilder messages = Json.createArrayBuilder();
			while(resultSet.next()) {
				String date = resultSet.getString(3);
				if(date.endsWith(".0")) {
					date = date.substring(0, date.length()-2);
				}
				
				JsonObjectBuilder message = Json.createObjectBuilder();
				message.add("id", resultSet.getInt(1));
				message.add("nickname", resultSet.getString(2));
				message.add("date", date);
				message.add("content", resultSet.getString(4));
				messages.add(message);
			}
			System.out.println("messages: " + messages.toString());
			response.add("messages", messages.build());
			sendResponse(response.build().toString());
			
		} catch (SQLException e) {
			System.err.println("Couldn't get messages of user " + nickname);
			e.printStackTrace();
		}
	}

	private void handleGetConversations(JsonObject jsonObject) {
		if(!isLoggedIn){
			sendResponse("Not logged in");
			return;
		}
		
		String sql = "SELECT chat.id, title "
				+ "FROM chat, chat_user "
				+ "WHERE chat.title IS NOT NULL "
				+ "AND chat.id = chat_user.chat_id "
				+ "AND chat_user.nickname = ?";
		try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			ResultSet resultSet = preparedStatement.executeQuery();
			
			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 4);
			
			JsonArrayBuilder conversations = Json.createArrayBuilder();
			while(resultSet.next()) {
				JsonObjectBuilder conversation = Json.createObjectBuilder();
				conversation.add("id", resultSet.getInt(1));
				conversation.add("title", resultSet.getString(2));
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
		
		String buddyname;
		try {
			buddyname = jsonObject.getString("buddyname");
		} catch(NullPointerException e) {
			System.err.println("Delete buddy failed, missing buddyname");
			sendResponse("Delete buddy failed, missing buddyname");
			return;
		}

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
		String sql = "SELECT nickname, quotation, chatID "
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
				currentLine.add("chatid", resultSet.getInt(3));
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
	public void sendResponse(String message) {
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
			return MessageType.MESSAGE;
		case 3:
			return MessageType.DELETEBUDDY;
		case 4:
			return MessageType.GETCONVERSATIONS;
		case 5:
			return MessageType.GETMESSAGES;
		case 6:
			return MessageType.SENDMESSAGE;
		default:
			return MessageType.INVALID;
		}
	}

}
