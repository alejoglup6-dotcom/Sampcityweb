-- Tablas mínimas del juego que la web lee (solo para pruebas; la base real ya las tiene).
CREATE TABLE IF NOT EXISTS crews (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(40));
CREATE TABLE IF NOT EXISTS discord_links (player_id INT PRIMARY KEY, discord_id VARCHAR(32), linked_at DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS player (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(24) UNIQUE, ip VARCHAR(16), email VARCHAR(32), salt VARCHAR(16), pass VARCHAR(64), reg_date DATETIME, last_connection DATETIME, last_connection_timestamp INT DEFAULT 0,
 level INT DEFAULT 1, rep INT DEFAULT 1, connected INT DEFAULT 0, playerid INT DEFAULT 0, time_for_rep INT DEFAULT 0, skin INT DEFAULT 0, cash INT DEFAULT 0, pos_x FLOAT, pos_y FLOAT, pos_z FLOAT, angle FLOAT, state INT DEFAULT 0, fight_style INT DEFAULT 0, health FLOAT, hungry FLOAT, thirst FLOAT,
 config_sounds INT, config_audio INT, config_time INT, config_hud INT, config_admin INT, config_secure_login INT, phone_visible_number INT, doubt_channel INT,
 time_playing INT DEFAULT 0, admin_level INT DEFAULT 0, vip INT DEFAULT 0, vip_expire_date DATETIME NULL, bank_money INT DEFAULT 0, phone_number INT DEFAULT 0, wanted_level INT DEFAULT 0, arrests_count INT DEFAULT 0, kills_count INT DEFAULT 0, crew INT DEFAULT 0);
