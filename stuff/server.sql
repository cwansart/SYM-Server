--
-- Datenbank: sym
--
USE sym;
SET foreign_key_checks = 0;

DROP TABLE IF EXISTS chat;
DROP TABLE IF EXISTS chat_user;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS user_user;

-- stored function to add new chats
DROP FUNCTION IF EXISTS add_chat;
DELIMITER $$
CREATE FUNCTION add_chat(nickname1 VARCHAR(255), nickname2 VARCHAR(255))
  RETURNS INT
BEGIN
  DECLARE chatid INT;
  
  INSERT INTO chat VALUE();
  SELECT chat.id INTO chatid
  FROM chat
  WHERE chat.id NOT IN (
  	SELECT chat_user.chat_id
  	FROM chat_user
  )
  ORDER BY id ASC
  LIMIT 1;

  INSERT INTO user_user
  VALUES(nickname1, nickname2, chatid);

  INSERT INTO chat_user
  VALUES(nickname1, chatid);

  INSERT INTO chat_user
  VALUES(nickname2, chatid);

  RETURN chatid;
END;
$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- chat

CREATE TABLE chat (
  id mediumint(9) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO chat (title) VALUES
('Paulis Wodkarunde'),
('Larissas Strickrunde'),
('Chris schlechte Witze'),
('möp'),
(NULL), -- Einzelchat, ID 5
(NULL), -- Einzelchat, ID 6
(NULL), -- Einzelchat, ID 7
(NULL); -- Einzelchat, ID 8

-- ----------------------------------------------------------------------------
-- user

CREATE TABLE user (
  nickname varchar(255) NOT NULL PRIMARY KEY,
  firstname varchar(50) DEFAULT NULL,
  lastname varchar(50) DEFAULT NULL,
  quotation varchar(100) DEFAULT NULL,
  password varchar(100) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO user (nickname, firstname, lastname, quotation, password) VALUES
('Chris', 'Christian', 'Wansart', 'Pinguuuus :D', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Ceme', 'Lukas', 'Hannigbrink', ':O', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Larr', 'Larissa', 'Schenk', 'Katzööööön <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Pauli', 'Paulina', 'Giercza', 'Wodka <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('stalker', 'Raphael', 'Grewe', 'blubbbbbbbbbbbbbb', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG');

-- ----------------------------------------------------------------------------
-- chat_user

CREATE TABLE chat_user (
  nickname varchar(255) NOT NULL,
  chat_id mediumint(9) NOT NULL,
  FOREIGN KEY (nickname) REFERENCES user (nickname),
  FOREIGN KEY (chat_id) REFERENCES chat (id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO chat_user VALUES
('Larr', 1),
('Larr', 2),
('Larr', 3),
('Pauli', 5),
('Larr', 6),
('Chris', 1),
('Chris', 3),
('Chris', 4),
('Chris', 5),
('Pauli', 1),
('Pauli', 2),
('Chris', 6),
('Ceme', 3),
('Ceme', 4),
('Ceme', 7),
('Larr', 7),
('Larr', 8),
('Pauli', 8),
('stalker', 4);

-- ----------------------------------------------------------------------------
-- message

CREATE TABLE message (
  id mediumint(9) NOT NULL AUTO_INCREMENT,
  nickname varchar(255) NOT NULL,
  chat_id mediumint(9) NOT NULL,
  date TIMESTAMP DEFAULT NOW(),
  content text,
  PRIMARY KEY (id, chat_id),
  FOREIGN KEY (nickname) REFERENCES user (nickname),
  FOREIGN KEY (chat_id) REFERENCES chat (id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO message (nickname, chat_id, content) VALUES
('Larr', 2, 'Hat wer Lust zu stricken? :)'),
('Chris', 3, 'Kommt ein Pferd in ne Bar, sagt der Barkeeper: Mach doch nicht so ein langes Gesicht!'),
('Larr', 3, 'Nicht noch mehr schlechte Witze...'),
('Chris', 1, 'YAY WODKA PAULE!! LASS PARTEY MACHEN :D'),
('Pauli', 1, 'Partyyyyyyyyyyyyyy :D'),
('stalker', 4, 'hello world'),
('Ceme', 4, 'Huhu :o'),
('Chris', 5, 'Hey du :-) Na wie geht\'s?'),
('Larr', 5, 'Alles gut, und selbst?'),
('Chris', 5, 'Auch gut, danke. :D'),
('Chris', 6, 'Lass mal tanzen gehen!'),
('Larr', 6, 'Klar gerne, wann denn?'),
('Chris', 6, 'Freitag??'),
('Larr', 6, 'OK, bin dann um 8 bei dir');

-- ----------------------------------------------------------------------------
-- user_user

CREATE TABLE user_user (
  nickname1 varchar(255) NOT NULL,
  nickname2 varchar(255) NOT NULL,
  chatID mediumint(9) NOT NULL,
  PRIMARY KEY (nickname1, nickname2),
  FOREIGN KEY (nickname1) REFERENCES user (nickname),
  FOREIGN KEY (nickname2) REFERENCES user (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO user_user (nickname1, nickname2, chatID) VALUES
('Chris', 'Pauli', 5),
('Chris', 'Larr', 6),
('Ceme', 'Larr', 7),
('Larr', 'Pauli', 8);

SET foreign_key_checks = 1;