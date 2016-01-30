package de.sym;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/chat")
public class ChatServer {
	List<User> userList = new ArrayList<>();
	List<Session> sessionList = new ArrayList<>();
	static List<ChatMessageHandler> messageHandlerList = new ArrayList<>();
	Connection connection = null;

	public ChatServer() {
		try {
			if (connection == null) {
				Class.forName("com.mysql.jdbc.Driver");
				this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/sym?useUnicode=true&characterEncoding=UTF-8", "sym", "sym");
			}
		} catch (SQLException e) {
			System.err.println("Couldn't connect to MySQL database. Perhaps you forgot to start? ;-)");
			e.printStackTrace();
			System.exit(-1);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(-2);
		}
	}


	@OnOpen
	public void onOpen(Session session) {
		System.err.println("Opened Session:" + session.toString());
		ChatMessageHandler messageHandler = new ChatMessageHandler(session, sessionList, messageHandlerList, connection);
		session.addMessageHandler(messageHandler);
		ChatServer.messageHandlerList.add(messageHandler);
		this.sessionList.add(session);
	}

	@OnClose
	public void onClose(Session session) {
		System.err.println("Closed Session:" + session.toString());
		for(MessageHandler messageHandler: session.getMessageHandlers()) {
			messageHandlerList.remove(messageHandler);
		}
		this.sessionList.remove(session);
	}

	@OnError
	public void onError(Throwable t) {
		System.err.println(t.getMessage());
	}
}
