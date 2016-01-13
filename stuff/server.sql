-- phpMyAdmin SQL Dump
-- version 4.5.1
-- http://www.phpmyadmin.net
--
-- Host: 127.0.0.1
-- Erstellungszeit: 08. Jan 2016 um 12:51
-- Server-Version: 10.1.9-MariaDB
-- PHP-Version: 5.5.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Datenbank: `sym`
--
use `sym`;
SET foreign_key_checks = 0;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `chat`
--


CREATE TABLE `chat` (
  `id` mediumint(9) NOT NULL,
  `title` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `chat_user`
--

CREATE TABLE `chat_user` (
  `nickname` varchar(255) NOT NULL,
  `chat_id` mediumint(9) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `message`
--

CREATE TABLE `message` (
  `id` mediumint(9) NOT NULL,
  `nickname` varchar(255) NOT NULL,
  `chat_id` mediumint(9) NOT NULL,
  `date` date DEFAULT NULL,
  `content` text
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `user`
--

CREATE TABLE `user` (
  `nickname` varchar(255) NOT NULL,
  `firstname` varchar(50) DEFAULT NULL,
  `lastname` varchar(50) DEFAULT NULL,
  `quotation` varchar(100) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Daten für Tabelle `user`
--

INSERT INTO `user` (`nickname`, `firstname`, `lastname`, `quotation`, `password`) VALUES
('Chris', 'Christian', 'Wansart', 'Pinguuuus :D', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Ceme', 'Lukas', 'Hannigbrink', ':O', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Larr', 'Larissa', 'Schenk', 'Katzööööön <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG'),
('Pauli', 'Paulina', 'Giercza', 'Wodka <3', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG!'),
('stalker', 'Raphael', 'Grewe', 'blubbbbbbbbbbbbbb', '$2a$10$j.nc6PJ94ZdE5As97FoRGu1Md.HX2f7T2j9rsxSZrJwrdnclFOZLG');

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `user_user`
--

CREATE TABLE `user_user` (
  `nickname1` varchar(255) NOT NULL,
  `nickname2` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Daten für Tabelle `user_user`
--

INSERT INTO `user_user` (`nickname1`, `nickname2`) VALUES
('Chris', 'Pauli'),
('Chris', 'Larr'),
('Ceme', 'Larr'),
('Larr', 'Pauli');

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `chat`
--
ALTER TABLE `chat`
  ADD PRIMARY KEY (`id`);

--
-- Indizes für die Tabelle `chat_user`
--
ALTER TABLE `chat_user`
  ADD PRIMARY KEY (`nickname`, `chat_id`),
  ADD KEY `nickname` (`nickname`),
  ADD KEY `chat_id` (`chat_id`);

--
-- Indizes für die Tabelle `message`
--
ALTER TABLE `message`
  ADD PRIMARY KEY (`id`, `chat_id`),
  ADD KEY `nickname` (`nickname`),
  ADD KEY `chat_id` (`chat_id`);

--
-- Indizes für die Tabelle `user`
--
ALTER TABLE `user`
  ADD PRIMARY KEY (`nickname`);

--
-- Indizes für die Tabelle `user_user`
--
ALTER TABLE `user_user`
  ADD PRIMARY KEY (`nickname1`,`nickname2`),
  ADD KEY `nickname1` (`nickname1`),
  ADD KEY `nickname2` (`nickname2`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `chat`
--
ALTER TABLE `chat`
  MODIFY `id` mediumint(9) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT für Tabelle `message`
--
ALTER TABLE `message`
  MODIFY `id` mediumint(9) NOT NULL AUTO_INCREMENT;
--
-- Constraints der exportierten Tabellen
--

--
-- Constraints der Tabelle `chat_user`
--
ALTER TABLE `chat_user`
  ADD CONSTRAINT `chat_user_ibfk_1` FOREIGN KEY (`nickname`) REFERENCES `user` (`nickname`),
  ADD CONSTRAINT `chat_user_ibfk_2` FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`);

--
-- Constraints der Tabelle `message`
--
ALTER TABLE `message`
  ADD CONSTRAINT `message_ibfk_1` FOREIGN KEY (`nickname`) REFERENCES `user` (`nickname`),
  ADD CONSTRAINT `message_ibfk_2` FOREIGN KEY (`chat_id`) REFERENCES `chat` (`id`);
--
-- Constraints der Tabelle `user_user`
--
ALTER TABLE `user_user`
  ADD CONSTRAINT `user_user_ibfk_1` FOREIGN KEY (`nickname1`) REFERENCES `user` (`nickname`),
  ADD CONSTRAINT `user_user_ibfk_2` FOREIGN KEY (`nickname2`) REFERENCES `user` (`nickname`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
SET foreign_key_checks = 1;