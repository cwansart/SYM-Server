package de.sym;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
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
		ADDFRIEND	// 6
	}

	public ChatMessageHandler(Session session, List<Session> sessionList, List<ChatMessageHandler> messageHandlerList,
			Connection connection) {
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
			handleSendMessage(jsonObject);
			break;

		case DELETEBUDDY:
			// Hier müssen wir noch überlegen was mit dem buddy passiert.
			// Im Client des Buddys muss die Freundschft ja auch gelöscht
			// werden.
			// Wie soll er z.B. informiert werden?
			handleDeleteBuddy(jsonObject);
			break;

		case GETCONVERSATIONS:
			handleGetConversations(jsonObject);
			break;

		case GETMESSAGES:
			handleGetMessages(jsonObject);
			break;
			
		case ADDFRIEND:
			handleAddFriend(jsonObject);
			break;

		default:
			sendResponse("{\"msgtype\": 0}");
			break;
		}
	}

	/**
	 * Handles LOGIN (msgtype 1)
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
				e.printStackTrace();
			} finally {
				sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"3 login attempts failed\"}");
			}
		}

		// Already logged in
		if (isLoggedIn) {
			sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"already logged in\"}");
			return;
		}

		String plaintext;
		try {
			nickname = jsonObject.getString("nickname");
			plaintext = jsonObject.getString("password");
		} catch (NullPointerException e) {
			System.err.println("Login attempt with invalid user");
			sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"login failed\"}");
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
					response.add("successful",  true);
					response.add("nickname", nickname);
					response.add("firstname", firstname);
					response.add("lastname", lastname);
					response.add("quotation", quotation);
					response.add("friendslist", getFriendsList(nickname));

					sendResponse(response.build().toString());
				} else {
					sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"login failed\"}");
				}
			} else {
				sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"login failed\"}");
			}
			this.loginAttempts++;

		} catch (SQLException e) {
			sendResponse("{\"msgtype\": 1, \"successful\": false, \"error\": \"login failed\"}");
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}
	}

	/**
	 * Hanldes SENDMESSAGE (msgtype 2)
	 * 
	 * @param jsonObject
	 */
	private void handleSendMessage(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 2, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		int chatId;
		String message;
		try {
			chatId = jsonObject.getInt("id");
			message = jsonObject.getString("message");
		} catch (NullPointerException e) {
			System.err.println("Message didn't contain a chat id or message");
			sendResponse("{\"msgtype\": 2, \"successful\": false, \"error\": \"missing id or message\"}");
			return;
		}

		// Insert message into database
		String sql = "INSERT INTO message (nickname, chat_id, content) VALUES (?, ?, ?)";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			preparedStatement.setInt(2, chatId);
			preparedStatement.setString(3, message);
			preparedStatement.executeUpdate();
		} catch (SQLException e) {
			System.err.println("Couldn't send/save the message.");
			sendResponse("{\"msgtype\": 2, \"successful\": false, \"error\": \"couldn't save the message\"}");
			e.printStackTrace();
			return;
		}

		// Get all users of the current chat to iterate over and notify users of
		// new messages.
		sql = "SELECT nickname FROM chat_user WHERE chat_id =?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			// preparedStatement.setString(1, nickname);
			preparedStatement.setInt(1, chatId);
			ResultSet resultSet = preparedStatement.executeQuery();

			while (resultSet.next()) {
				String nickname2 = resultSet.getString(1);
				for (ChatMessageHandler messageHandler : messageHandlerList) {
					if (messageHandler.getNickname().equals(nickname2)) {
						JsonObjectBuilder response = Json.createObjectBuilder();
						response.add("msgtype", 2);
						response.add("successful",  true);
						response.add("id", chatId);
						response.add("author", nickname);

						SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						response.add("date", dateFormat.format(new Date()));

						response.add("message", message);
						messageHandler.sendResponse(response.build().toString());
					}
				}
			}
		} catch (SQLException e) {
			sendResponse("Couldn't send message.");
			System.err.println("Could't send message due to errors");
			sendResponse("{\"msgtype\": 2, \"successful\": false, \"error\": \"couldn't save the message\"}");
			e.printStackTrace();
		}
	}

	/**
	 * Handles DELETEBUDDY (msgtype 3)
	 * 
	 * @param jsonObject
	 */
	private void handleDeleteBuddy(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 4, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		String buddyname;
		try {
			buddyname = jsonObject.getString("buddyname");
		} catch (NullPointerException e) {
			System.err.println("Delete buddy failed, missing buddyname");
			sendResponse("{\"msgtype\": 4, \"successful\": false, \"error\": \"missing buddyname\"}");
			return;
		}

		String sql = "DELETE FROM user_user WHERE (nickname1 =? AND nickname2 =?) OR (nickname2=? AND nickname1=?)";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			preparedStatement.setString(2, buddyname);
			preparedStatement.setString(3, nickname);
			preparedStatement.setString(4, buddyname);
			preparedStatement.execute();
		} catch (SQLException e) {
			sendResponse("{\"msgtype\": 4, \"successful\": false, \"error\": \"delete buddy failed\"}");
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}

		for (ChatMessageHandler messageHandler : this.messageHandlerList) {
			System.err.println("NICK1: " + messageHandler.getNickname());
			if (messageHandler.getNickname().equals(buddyname)) {
				JsonObjectBuilder response = Json.createObjectBuilder();
				response.add("msgtype", 3);
				response.add("successful",  true);
				response.add("nickname", nickname);
				messageHandler.sendResponse(response.build().toString());
			}
		}
	}

	/**
	 * Handles GETCONVERSATIONS (msgtype 4)
	 * 
	 * @param jsonObject
	 */
	private void handleGetConversations(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 4, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		String sql = "SELECT chat.id, title " + "FROM chat, chat_user " + "WHERE chat.title IS NOT NULL "
				+ "AND chat.id = chat_user.chat_id " + "AND chat_user.nickname = ?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			ResultSet resultSet = preparedStatement.executeQuery();

			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 4);
			response.add("successful",  true);

			JsonArrayBuilder conversations = Json.createArrayBuilder();
			while (resultSet.next()) {
				int id = resultSet.getInt(1);

				JsonObjectBuilder conversation = Json.createObjectBuilder();
				conversation.add("id", id);
				conversation.add("title", resultSet.getString(2));
				conversation.add("participants", getParticipants(id));

				conversations.add(conversation);
			}
			response.add("conversations", conversations.build());
			sendResponse(response.build().toString());

		} catch (SQLException e) {
			System.err.println("Couldn't get conversations of user " + nickname);
			sendResponse("{\"msgtype\": 4, \"successful\": false, \"error\": \"couldn't get conversations\"}");
			e.printStackTrace();
		}
	}

	/**
	 * Handles GETMESSAGES (msgtype 5)
	 * 
	 * @param jsonObject
	 */
	private void handleGetMessages(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 5, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		int id;
		try {
			id = jsonObject.getInt("id");
		} catch (NullPointerException e) {
			System.err.println("Message didn't contain a chat id");
			sendResponse("{\"msgtype\": 5, \"successful\": false, \"error\": \"unknown id\"}");
			return;
		}

		String sql = "SELECT id, nickname, date, content FROM `message` WHERE chat_id =?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setInt(1, id);
			ResultSet resultSet = preparedStatement.executeQuery();

			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 5);
			response.add("successful",  true);
			response.add("id", id);

			JsonArrayBuilder messages = Json.createArrayBuilder();
			while (resultSet.next()) {
				String date = resultSet.getString(3);
				if (date.endsWith(".0")) {
					date = date.substring(0, date.length() - 2);
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
			sendResponse("{\"msgtype\": 5, \"successful\": false, \"error\": \"couldn't get messages\"}");
			e.printStackTrace();
		}
	}
	
	/**
	 * handles AddFriend (msgtype 6)
	 * @param jsonObject
	 */
	private void handleAddFriend(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 6, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}
		
		String nickname;
		
		try {
			nickname = jsonObject.getString("nickname");
		} catch (NullPointerException e) {
			System.err.println("Message didn't contain a nickname");
			sendResponse("Couldn't send message. Missing nickname-");
			return;
		}
		
		String sql = "SELECT nickname FROM user WHERE nickname =?";
		
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			ResultSet resultSet = preparedStatement.executeQuery();
			
			String buddyname = null;
			while (resultSet.next()) {
				buddyname = resultSet.getString(1);
			}
			
			// Friend not found
			if(buddyname == null) {
				JsonObjectBuilder response = Json.createObjectBuilder();
				response.add("msgtype", 6);
				response.add("successful", false);
				response.add("error", "not found");
				sendResponse(response.build().toString());
			}
			// found friend
			else {
				
			}
		} catch (SQLException e) {
			sendResponse("Couldn't send message.");
			System.err.println("Could't send message due to errors");
			e.printStackTrace();
		}

	}

	// ==============================================================================================================
	// Other private methods that we use in other methods.
	/**
	 * Gets the friendslist of a certain user `nickname`
	 * 
	 * @param nickname
	 * @return
	 */
	private JsonArray getFriendsList(String nickname) {
		JsonArrayBuilder friendslist = Json.createArrayBuilder();
		String sql = "SELECT nickname, quotation, chatID " + "FROM user, user_user "
				+ "WHERE (user.nickname = user_user.nickname2 " + "AND user_user.nickname1 = ?) "
				+ "OR (user.nickname = user_user.nickname1 " + "AND user_user.nickname2 = ?) ";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, nickname);
			statement.setString(2, nickname);
			System.err.println(statement.toString());
			ResultSet resultSet = statement.executeQuery();

			while (resultSet.next()) {
				JsonObjectBuilder currentLine = Json.createObjectBuilder();
				currentLine.add("nickname", resultSet.getString(1));
				currentLine.add("quotation", resultSet.getString(2));
				currentLine.add("id", resultSet.getInt(3));
				friendslist.add(currentLine);
			}

		} catch (SQLException e) {
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}
		return friendslist.build();
	}

	/**
	 * Gets the participants of a certain chat or conversation.
	 * 
	 * @param chatId
	 * @return
	 */
	private JsonArray getParticipants(int chatId) {
		JsonArrayBuilder participants = Json.createArrayBuilder();
		String sql = "SELECT nickname FROM chat_user WHERE chat_id = ?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setInt(1, chatId);
			ResultSet resultSet = preparedStatement.executeQuery();

			while (resultSet.next()) {
				participants.add(resultSet.getString(1));
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return participants.build();
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
			return MessageType.ADDFRIEND;
		default:
			return MessageType.INVALID;
		}
	}

}
