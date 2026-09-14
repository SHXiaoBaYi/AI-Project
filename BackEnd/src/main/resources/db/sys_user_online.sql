-- Single-device online session (one row per user)
CREATE TABLE IF NOT EXISTS sys_user_online (
  user_id     BIGINT       NOT NULL COMMENT 'User ID',
  token_id    VARCHAR(64)  NOT NULL COMMENT 'JWT jti',
  username    VARCHAR(50)  DEFAULT '' COMMENT 'Username',
  ip          VARCHAR(128) DEFAULT '' COMMENT 'Login IP',
  user_agent  VARCHAR(512) DEFAULT '' COMMENT 'User-Agent',
  login_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'Login time',
  expire_time DATETIME     NOT NULL COMMENT 'Session expire time',
  PRIMARY KEY (user_id),
  KEY idx_token_id (token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User online session';
