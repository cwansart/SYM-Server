--
-- Datenbank: `sym`
--
USE `sym`;
SET foreign_key_checks = 0;

DROP TABLE IF EXISTS `chat`;
DROP TABLE IF EXISTS `chat_user`;
DROP TABLE IF EXISTS `message`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `user_user`;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `chat`
--

CREATE TABLE `chat` (
  `id` mediumint(9) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `title` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `chat` (`title`) VALUES
('Paulis Wodkarunde'),
('Larissas Strickrunde'),
('Chris schlechte Witze'),
('möp');

--
-- Tabellenstruktur für Tabelle `user`
--

CREATE TABLE `user` (
  `nickname` varchar(255) NOT NULL PRIMARY KEY,
  `firstname` varchar(50) DEFAULT NULL,
  `lastname` varchar(50) DEFAULT NULL,
  `quotation` varchar(100) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `user` (`nickname`, `firstname`, `lastname`, `quotation`, `password`) VALUES
('Chris', 'Christian', 'Wansart', 'Pinguuuus :D', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Ceme', 'Lukas', 'Hannigbrink', ':O', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Larr', 'Larissa', 'Schenk', 'Katzööööön <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Pauli', 'Paulina', 'Giercza', 'Wodka <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG!'),
('stalker', 'Raphael', 'Grewe', 'blubbbbbbbbbbbbbb', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG');

--
-- Tabellenstruktur für Tabelle `chat_user`
--

CREATE TABLE `chat_user` (
  `nickname` varchar(255) NOT NULL,
  `chat_id` mediumint(9) NOT NULL,
  FOREIGN KEY (`nickname`) REFERENCES `user` (`nickname`),
  FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `chat_user` VALUES
('Larr', 1),
('Larr', 2),
('Larr', 3),
('Chris', 1),
('Chris', 3),
('Chris', 4),
('Pauli', 1),
('Pauli', 2),
('Ceme', 3),
('Ceme', 4),
('stalker', 4);

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `message`
--

CREATE TABLE `message` (
  `id` mediumint(9) NOT NULL AUTO_INCREMENT,
  `nickname` varchar(255) NOT NULL,
  `chat_id` mediumint(9) NOT NULL,
  `date` date DEFAULT NULL,
  `content` text,
  PRIMARY KEY (`id`, `chat_id`),
  FOREIGN KEY (`nickname`) REFERENCES `user` (`nickname`),
  FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `message` (`nickname`, `chat_id`, `content`) VALUES
('Larr', 2, 'Hat wer Lust zu stricken? :)'),
('Chris', 3, 'Kommt ein Pferd in ne Bar, sagt der Barkeeper: Mach doch nicht so ein langes Gesicht!'),
('Larr', 3, 'Nicht noch mehr schlechte Witze...'),
('Chris', 1, 'YAY WODKA PAULE!! LASS PARTEY MACHEN :D'),
('Pauli', 1, 'Partyyyyyyyyyyyyyy :D'),
('stalker', 4, 'hello world'),
('Ceme', 4, 'Huhu :o');

--
-- Tabellenstruktur für Tabelle `user_user`
--

CREATE TABLE `user_user` (
  `nickname1` varchar(255) NOT NULL,
  `nickname2` varchar(255) NOT NULL,
  PRIMARY KEY (`nickname1`,`nickname2`),
  FOREIGN KEY (`nickname1`) REFERENCES `user` (`nickname`),
  FOREIGN KEY (`nickname2`) REFERENCES `user` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `user_user` (`nickname1`, `nickname2`) VALUES
('Chris', 'Pauli'),
('Chris', 'Larr'),
('Ceme', 'Larr'),
('Larr', 'Pauli');

SET foreign_key_checks = 1;