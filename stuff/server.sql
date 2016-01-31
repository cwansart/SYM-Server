--
-- Datenbank: sym
--
USE sym;
SET foreign_key_checks = 0;

SET NAMES utf8mb4;
ALTER DATABASE sym CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS chat;
DROP TABLE IF EXISTS chat_user;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS user_user;

-- stored function to add new chats
DROP FUNCTION IF EXISTS add_chat;
DELIMITER $$
CREATE FUNCTION add_chat(nickname1 VARCHAR(191), nickname2 VARCHAR(191))
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
  title VARCHAR(191) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
  nickname VARCHAR(191) NOT NULL PRIMARY KEY,
  firstname varchar(50) DEFAULT NULL,
  lastname varchar(50) DEFAULT NULL,
  quotation varchar(100) DEFAULT NULL,
  password varchar(100) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user (nickname, firstname, lastname, quotation, password) VALUES
('Chris', 'Christian', 'Wansart', 'Pinguuuus :D', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Ceme', 'Lukas', 'Hannigbrink', ':O', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Larr', 'Larissa', 'Schenk', 'Katzööööön <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Pauli', 'Paulina', 'Giercza', 'Wodka <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('stalker', 'Raphael', 'Grewe', 'blubbbbbbbbbbbbbb', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG');

-- ----------------------------------------------------------------------------
-- chat_user

CREATE TABLE chat_user (
  nickname VARCHAR(191) NOT NULL,
  chat_id mediumint(9) NOT NULL,
  FOREIGN KEY (nickname) REFERENCES user (nickname),
  FOREIGN KEY (chat_id) REFERENCES chat (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
  nickname VARCHAR(191) NOT NULL,
  chat_id mediumint(9) NOT NULL,
  date TIMESTAMP DEFAULT NOW(),
  content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  image LONGTEXT, -- We don't use blob here, since our image is base64 encoded
  type CHAR(1) NOT NULL DEFAULT 't', 
  PRIMARY KEY (id, chat_id),
  FOREIGN KEY (nickname) REFERENCES user (nickname),
  FOREIGN KEY (chat_id) REFERENCES chat (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO message (nickname, chat_id, date, content, image, type) VALUES
('Larr', 2, NOW(), 'Hat wer Lust zu stricken? :)', NULL, 't'),
('Chris', 3, NOW(), 'Kommt ein Pferd in ne Bar, sagt der Barkeeper: Mach doch nicht so ein langes Gesicht!', NULL, 't'),
('Larr', 3, NOW(), 'Nicht noch mehr schlechte Witze...', NULL, 't'),
('Chris', 1, NOW(), 'YAY WODKA PAULE!! LASS PARTEY MACHEN :D', NULL, 't'),
('Pauli', 1, NOW(), 'Partyyyyyyyyyyyyyy :D', NULL, 't'),
('stalker', 4, NOW(), 'hello world', NULL, 't'),
('Ceme', 4, NOW(), 'Huhu :o', NULL, 't'),
('Chris', 5, NOW(), 'Hey du :-) Na wie gehts?', NULL, 't'),
('Larr', 5, NOW(), 'Alles gut, und selbst?', NULL, 't'),
('Chris', 5, NOW(), 'Auch gut, danke. :D', NULL, 't'),
('Chris', 6, NOW(), 'Lass mal tanzen gehen!', NULL, 't'),
('Larr', 6, NOW(), 'Klar gerne, wann denn?', NULL, 't'),
('Chris', 6, NOW(), 'Freitag??', NULL, 't'),
('Larr', 6, NOW(), 'OK, bin dann um 8 bei dir', NULL, 't'),
('Chris', 6, NOW(), NULL, '<img src="data:image/jpeg;base64,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">', 'i'),
('Larr', 6, NOW(), 'sweeeet', NULL, 't');

-- ----------------------------------------------------------------------------
-- user_user

CREATE TABLE user_user (
  nickname1 VARCHAR(191) NOT NULL,
  nickname2 VARCHAR(191) NOT NULL,
  chatID mediumint(9) NOT NULL,
  PRIMARY KEY (nickname1, nickname2),
  FOREIGN KEY (nickname1) REFERENCES user (nickname),
  FOREIGN KEY (nickname2) REFERENCES user (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user_user (nickname1, nickname2, chatID) VALUES
('Chris', 'Pauli', 5),
('Chris', 'Larr', 6),
('Ceme', 'Larr', 7),
('Larr', 'Pauli', 8);

SET foreign_key_checks = 1;