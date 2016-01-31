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
		ADDFRIEND, // 6
		FRIENDSHIPREQUEST, // 7
		STATUS, // 8
		IMAGE // 9
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

		case FRIENDSHIPREQUEST:
			handleFriendshipRequest(jsonObject);
			break;

		case STATUS:
			handleStatus(jsonObject);
			break;

		case IMAGE:
			handleImage(jsonObject);
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
					response.add("successful", true);
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

		// Inform friends that the user just came online.
		JsonArray friends = getFriendsList(nickname);
		synchronized (messageHandlerList) {
			for (ChatMessageHandler messageHandler : messageHandlerList) {
				for (int i = 0; i < friends.size(); i++) {
					if (messageHandler.getNickname() == null)
						continue;

					JsonObject friendObject = friends.getJsonObject(i);
					String nickname = friendObject.getString("nickname");
					if (messageHandler.getNickname().equals(nickname)) {
						JsonObjectBuilder builder = Json.createObjectBuilder();
						builder.add("msgtype", 8);
						builder.add("online", true);
						builder.add("nickname", this.nickname);
						messageHandler.sendResponse(builder.build().toString());

					}
				}
			}
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

		spreadMessage(chatId, message);
	}

	/**
	 * Handles DELETEBUDDY (msgtype 3)
	 * 
	 * @param jsonObject
	 */
	private void handleDeleteBuddy(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 3, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		String buddyname;
		try {
			buddyname = jsonObject.getString("buddyname");
		} catch (NullPointerException e) {
			System.err.println("Delete buddy failed, missing buddyname");
			sendResponse("{\"msgtype\": 3, \"successful\": false, \"error\": \"missing buddyname\"}");
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
			sendResponse("{\"msgtype\": 3, \"successful\": false, \"error\": \"delete buddy failed\"}");
			System.err.println("SQL Error: ");
			e.printStackTrace();
		}

		synchronized (this.messageHandlerList) {
			for (ChatMessageHandler messageHandler : this.messageHandlerList) {
				if (messageHandler.getNickname().equals(buddyname)) {
					JsonObjectBuilder response = Json.createObjectBuilder();
					response.add("msgtype", 3);
					response.add("successful", true);
					response.add("nickname", nickname);
					messageHandler.sendResponse(response.build().toString());
				}
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
			response.add("successful", true);

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

		String sql = "SELECT id, nickname, date, content, image, type FROM `message` WHERE chat_id =?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setInt(1, id);
			ResultSet resultSet = preparedStatement.executeQuery();

			JsonObjectBuilder response = Json.createObjectBuilder();
			response.add("msgtype", 5);
			response.add("successful", true);
			response.add("id", id);

			JsonArrayBuilder messages = Json.createArrayBuilder();
			while (resultSet.next()) {
				String date = resultSet.getString(3);
				if (date.endsWith(".0")) {
					date = date.substring(0, date.length() - 2);
				}

				// Check if message is text or image.
				String content = resultSet.getString(6).equals("t") ? resultSet.getString(4) : resultSet.getString(5);

				JsonObjectBuilder message = Json.createObjectBuilder();
				message.add("id", resultSet.getInt(1));
				message.add("nickname", resultSet.getString(2));
				message.add("date", date);
				message.add("content", content);
				messages.add(message);
			}

			response.add("messages", messages.build());
			sendResponse(response.build().toString());

		} catch (SQLException e) {
			System.err.println("Couldn't get messages of user " + nickname);
			sendResponse("{\"msgtype\": 5, \"successful\": false, \"error\": \"couldn't get messages\"}");
			e.printStackTrace();
		}
	}

	/**
	 * handles AddFriend (msgtype 6 and msgtype 7)
	 * 
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
			sendResponse("{\"msgtype\": 6, \"successful\": false, \"error\": \"missing nickname\"}");
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

			JsonObjectBuilder notFoundResponse = Json.createObjectBuilder();
			notFoundResponse.add("msgtype", 6);
			notFoundResponse.add("successful", false);
			notFoundResponse.add("error", "not found");

			// Friend not found
			if (buddyname == null) {
				sendResponse(notFoundResponse.build().toString());
			}
			// found friend
			else {
				boolean found = false;
				synchronized (messageHandlerList) {
					for (ChatMessageHandler messageHandler : messageHandlerList) {
						if (messageHandler.getNickname().equals(buddyname)) {
							found = true;
							JsonObjectBuilder request = Json.createObjectBuilder();
							request.add("msgtype", 7);
							request.add("successful", true);
							request.add("nickname", this.nickname);
							messageHandler.sendResponse(request.build().toString());
						}
					}
				}

				if (!found) {
					sendResponse(notFoundResponse.build().toString());
				}
			}
		} catch (SQLException e) {
			sendResponse("{\"msgtype\": 6, \"successful\": false, \"error\": \"couldn't add friend\"}");
			System.err.println("Could't send message due to errors");
			e.printStackTrace();
		}
	}

	/**
	 * Handles friendship requests
	 * 
	 * @param jsonObject
	 */
	private void handleFriendshipRequest(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 7, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		String buddyname;
		boolean accepted;
		String quotation1 = "";
		String quotation2 = "";

		try {
			buddyname = jsonObject.getString("nickname");
			accepted = jsonObject.getBoolean("accepted");
		} catch (NullPointerException e) {
			sendResponse("{\"msgtype\": 7, \"successful\": false, \"error\": \"missing nickname or accepted\"}");
			e.printStackTrace();
			return;
		}

		int chatId = -1;
		if (accepted) {
			String sql = "SELECT add_chat(?, ?)";
			try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
				preparedStatement.setString(1, nickname);
				preparedStatement.setString(2, buddyname);

				ResultSet resultSet = preparedStatement.executeQuery();
				while (resultSet.next()) {
					chatId = resultSet.getInt(1);
				}

			} catch (SQLException e) {
				sendResponse("{\"msgtype\": 7, \"successful\": false, \"error\": \"couldn't add friend\"}");
				e.printStackTrace();
			}

			String sql2 = "SELECT quotation FROM user WHERE nickname =?";

			try (PreparedStatement preparedStatement = connection.prepareStatement(sql2)) {
				preparedStatement.setString(1, buddyname);
				ResultSet resultSet = preparedStatement.executeQuery();
				while (resultSet.next()) {
					quotation1 = resultSet.getString(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

			try (PreparedStatement preparedStatement = connection.prepareStatement(sql2)) {
				preparedStatement.setString(1, nickname);
				ResultSet resultSet = preparedStatement.executeQuery();
				while (resultSet.next()) {
					quotation2 = resultSet.getString(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		// Antwort msgtype 6 an User 1
		JsonObjectBuilder response1 = Json.createObjectBuilder();
		response1.add("msgtype", 6);
		response1.add("successful", accepted);

		if (!accepted) {
			response1.add("error", "not accepted");
		} else {
			response1.add("nickname", buddyname);
			response1.add("chatid", chatId);
			response1.add("quotation", quotation1);
		}
		String responseText1 = response1.build().toString();
		sendResponse(responseText1);

		// Antwort msgtype 6 an User 2
		synchronized (messageHandlerList) {
			for (ChatMessageHandler messageHandler : messageHandlerList) {
				if (messageHandler.getNickname().equals(buddyname)) {
					JsonObjectBuilder response2 = Json.createObjectBuilder();
					response2.add("msgtype", 6);
					response2.add("successful", accepted);

					if (!accepted) {
						response2.add("error", "not accepted");
					} else {
						response2.add("nickname", nickname);
						response2.add("chatid", chatId);
						response2.add("quotation", quotation2);
					}

					String responseText2 = response2.build().toString();
					messageHandler.sendResponse(responseText2);
				}
			}
		}
	}

	/**
	 * Handles status messages. Informs online friends about the logout.
	 * 
	 * @param jsonObject
	 */
	private void handleStatus(JsonObject jsonObject) {
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 8, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		boolean online;
		try {
			online = jsonObject.getBoolean("online");
		} catch (NullPointerException e) {
			sendResponse("{\"msgtype\": 8, \"successful\": false, \"error\": \"missing online\"}");
			e.printStackTrace();
			return;
		}

		JsonArray friends = getFriendsList(nickname);
		synchronized (messageHandlerList) {
			for (ChatMessageHandler messageHandler : messageHandlerList) {
				for (int i = 0; i < friends.size(); i++) {
					JsonObject friendObject = friends.getJsonObject(i);
					String nickname = friendObject.getString("nickname");
					if (messageHandler.getNickname().equals(nickname)) {
						JsonObjectBuilder builder = Json.createObjectBuilder();
						builder.add("msgtype", 8);
						builder.add("online", online);
						builder.add("nickname", this.nickname);
						messageHandler.sendResponse(builder.build().toString());
					}
				}
			}
		}
	}

	/**
	 * Handles images and stores it in the database. (msgtype 9)
	 * 
	 * @param jsonObject
	 */
	private void handleImage(JsonObject jsonObject) {
		System.err.println("handleImage1");
		if (!isLoggedIn) {
			sendResponse("{\"msgtype\": 9, \"successful\": false, \"error\": \"not logged in\"}");
			return;
		}

		System.err.println("handleImage2");
		int chatId;
		String blob;
		try {
			chatId = jsonObject.getInt("id");
			blob = jsonObject.getString("blob");
		} catch (NullPointerException e) {
			System.err.println("Message didn't contain a chat id or message");
			sendResponse("{\"msgtype\": 9, \"successful\": false, \"error\": \"missing id or message\"}");
			return;
		}

		// Check if blob contains a quote character. We need to prevent this to
		// prevent injections.
		if (blob.contains("\"")) {
			System.err.println("Image blob contains a quote character");
			sendResponse("{\"msgtype\": 9, \"successful\": false, \"error\": \"invalid image\"}");
			return;
		}

		// Check if blob starts with data
		if (!blob.startsWith("data:image/")) {
			System.err.println("Not a valid blob");
			sendResponse("{\"msgtype\": 9, \"successful\": false, \"error\": \"invalid blob\"}");
			return;
		}

		StringBuilder imgTag = new StringBuilder();
		imgTag.append("<img src=\"");
		imgTag.append(blob);
		imgTag.append("\">");

		// Insert message into database
		String sql = "INSERT INTO message (nickname, chat_id, image, type) VALUES (?, ?, ?, ?)";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setString(1, nickname);
			preparedStatement.setInt(2, chatId);
			preparedStatement.setString(3, imgTag.toString());
			preparedStatement.setString(4, "i");
			preparedStatement.executeUpdate();
		} catch (SQLException e) {
			System.err.println("Couldn't send/save the message.");
			sendResponse("{\"msgtype\": 2, \"successful\": false, \"error\": \"couldn't save the message\"}");
			e.printStackTrace();
			return;
		}

		spreadMessage(chatId, imgTag.toString());
	}

	// ==============================================================================================================
	// Other private methods that we use in other methods.
	/**
	 * Spreads a message to all participants of the chat.
	 * 
	 * @param chatId
	 * @param message
	 */
	private void spreadMessage(int chatId, String message) {
		String sql;
		// Get all users of the current chat to iterate over and notify users of
		// new messages.
		sql = "SELECT nickname FROM chat_user WHERE chat_id =?";
		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			// preparedStatement.setString(1, nickname);
			preparedStatement.setInt(1, chatId);
			ResultSet resultSet = preparedStatement.executeQuery();

			while (resultSet.next()) {
				String nickname2 = resultSet.getString(1);
				synchronized (messageHandlerList) {
					for (ChatMessageHandler messageHandler : messageHandlerList) {
						if (messageHandler.getNickname().equals(nickname2)) {
							JsonObjectBuilder response = Json.createObjectBuilder();
							response.add("msgtype", 2);
							response.add("successful", true);
							response.add("id", chatId);
							response.add("author", nickname);

							SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							response.add("date", dateFormat.format(new Date()));

							response.add("message", message);
							messageHandler.sendResponse(response.build().toString());
						}
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
				boolean online = false;

				synchronized (messageHandlerList) {
					for (ChatMessageHandler messageHandler : messageHandlerList) {
						if (messageHandler.getNickname().equals(resultSet.getString(1))) {
							online = true;
						}
					}
				}

				JsonObjectBuilder currentLine = Json.createObjectBuilder();
				currentLine.add("nickname", resultSet.getString(1));
				currentLine.add("quotation", resultSet.getString(2));
				currentLine.add("id", resultSet.getInt(3));
				currentLine.add("online", online);
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
		case 7:
			return MessageType.FRIENDSHIPREQUEST;
		case 8:
			return MessageType.STATUS;
		case 9:
			return MessageType.IMAGE;
		default:
			return MessageType.INVALID;
		}
	}

}
