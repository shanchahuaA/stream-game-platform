-- ============================================================
-- Stream 游戏信息平台 —— 建库脚本
--
-- 用法（在仓库根目录）：
--     mysql -uroot -p < sql/schema.sql
--
-- 建出的库名是 stream，与 db.properties.example 里的连接串一致。
-- 想换库名，改下面的 CREATE DATABASE / USE，再改自己的连接串即可。
--
-- 表结构按本项目实际使用的库导出，字段类型、默认值、唯一键都与之一致，
-- 因为代码里有依赖：games 的 UNIQUE(platform, platform_game_id) 支撑
-- insertGame 的 ON DUPLICATE KEY UPDATE（爬虫重复抓取时更新而不是插重复行），
-- user_favorites 的 UNIQUE(user_id, game_id) 保证同一用户不会重复收藏同一游戏。
-- ============================================================

-- 本文件是 UTF-8，而中文 Windows 上 mysql 客户端默认按 gbk 解码它，
-- 里面的中文（表注释、列注释）会存成乱码，而且不报错，
-- 所以在任何含中文的语句之前先声明字符集。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `stream`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `stream`;

-- 有外键引用关系，删除顺序从依赖方开始
DROP TABLE IF EXISTS `user_favorites`;
DROP TABLE IF EXISTS `games`;
DROP TABLE IF EXISTS `user`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
CREATE TABLE `user` (
    `id`         int          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`   varchar(50)  NOT NULL COMMENT '登录账号',
    `password`   varchar(100) NOT NULL COMMENT '登录口令，当前为明文存储（见 README 已知问题）',
    `nickname`   varchar(50)           DEFAULT NULL COMMENT '用户昵称',
    `avatar`     varchar(255)          DEFAULT '/assets/imgs/default_avatar.png' COMMENT '用户头像',
    `status`     int                   DEFAULT '1' COMMENT '状态：1 启用，0 禁用',
    `created_at` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户表';

-- ------------------------------------------------------------
-- 游戏表：爬虫抓取结果的落地表，Steam 与 Epic 共用
-- ------------------------------------------------------------
CREATE TABLE `games` (
    `id`               int            NOT NULL AUTO_INCREMENT,
    `platform`         varchar(50)    NOT NULL DEFAULT 'steam' COMMENT '所属平台：steam / epic',
    `platform_game_id` varchar(100)   NOT NULL COMMENT '平台侧的原始游戏ID',
    `gname`            varchar(255)   NOT NULL DEFAULT 'default_game_name' COMMENT '游戏名称',
    `cover_url`        varchar(500)            DEFAULT NULL COMMENT '封面图片URL',
    `shop_url`         varchar(500)            DEFAULT NULL COMMENT '商店详情页链接',
    `original_price`   double         NOT NULL DEFAULT '0' COMMENT '原价',
    `final_price`      double         NOT NULL DEFAULT '0' COMMENT '现价',
    `discount_percent` int            NOT NULL DEFAULT '0' COMMENT '折扣百分比',
    `top_seller_rank`  int            NOT NULL DEFAULT '0' COMMENT '热销榜排名，1-100；0 表示不在榜',
    `discount_rank`    int            NOT NULL DEFAULT '0' COMMENT '折扣页顺序，1-N；0 表示不在折扣页',
    `last_sync_time`   datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后同步时间',
    `review_count`     int            NOT NULL DEFAULT '0' COMMENT '评测数量',
    `positive_rate`    decimal(5, 2)  NOT NULL DEFAULT '0.00' COMMENT '好评率',
    `review_tier`      varchar(50)             DEFAULT NULL COMMENT '评价等级，Epic 侧也用来标记限时免费/即将免费',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_game` (`platform`, `platform_game_id`) COMMENT '同一平台下的游戏ID不重复',
    KEY `idx_top_seller_rank` (`top_seller_rank`),
    KEY `idx_discount_rank` (`discount_rank`),
    KEY `idx_last_sync_time` (`last_sync_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '游戏信息表';

-- ------------------------------------------------------------
-- 收藏表
-- ------------------------------------------------------------
CREATE TABLE `user_favorites` (
    `id`         int      NOT NULL AUTO_INCREMENT,
    `user_id`    int      NOT NULL,
    `game_id`    int      NOT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_game` (`user_id`, `game_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_game_id` (`game_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户收藏表';