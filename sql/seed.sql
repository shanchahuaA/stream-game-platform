-- ============================================================
-- Stream 游戏信息平台 —— 虚构示例数据
--
-- 用法（先跑过 sql/schema.sql）：
--     mysql -uroot -p < sql/seed.sql
--
-- 全部是编造的演示数据：用户名、昵称、游戏名、价格都不是真实数据。
-- 用 INSERT IGNORE + 显式主键，所以可以重复执行，也不会覆盖或删除库里已有的行。
--
-- 数据刻意覆盖了前端的几种展示形态：
--   * discount_rank > 0   → 出现在「折扣」页
--   * top_seller_rank > 0 → 出现在「热销榜」
--   * original_price > 0 且 final_price = 0 → 出现在「喜加一」
--   * epic + 即将免费     → 出现在「喜加一」的预告分组
-- ============================================================

-- 同 sql/schema.sql：先声明字符集，否则下面的中文数据会存成乱码（而且不报错）。
SET NAMES utf8mb4;

USE `stream`;

-- ------------------------------------------------------------
-- 用户（口令是明文，与当前代码的登录逻辑一致；status = 0 表示被管理员禁用）
-- ------------------------------------------------------------
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `nickname`, `status`) VALUES
    (1, 'alice', 'demo123456', '小海', 1),
    (2, 'bob',   'demo123456', '小林', 1),
    (3, 'carol', 'demo123456', '阿澈', 0),
    (4, 'dave',  'demo123456', '星野', 1),
    -- 管理员面板是按 username === 'admin' 判定的（前端判断，见 README 已知问题）
    (5, 'admin', 'demo123456', '演示管理员', 1);

-- ------------------------------------------------------------
-- 游戏
-- ------------------------------------------------------------
INSERT IGNORE INTO `games`
    (`id`, `platform`, `platform_game_id`, `gname`, `cover_url`, `shop_url`,
     `original_price`, `final_price`, `discount_percent`,
     `top_seller_rank`, `discount_rank`, `review_count`, `positive_rate`, `review_tier`)
VALUES
    (1, 'steam', '900001', '星海远征 Star Voyage',
     'https://cdn.example.com/covers/900001.jpg', 'https://store.steampowered.com/app/900001',
     298.00, 149.00, 50, 0, 1, 24817, 92.50, '好评如潮'),
    (2, 'steam', '900002', '深空回响 Echoes of the Void',
     'https://cdn.example.com/covers/900002.jpg', 'https://store.steampowered.com/app/900002',
     198.00, 79.20, 60, 0, 2, 3402, 88.00, '特别好评'),
    (3, 'steam', '900003', '雨夜列车',
     'https://cdn.example.com/covers/900003.jpg', 'https://store.steampowered.com/app/900003',
     48.00, 48.00, 0, 1, 0, 9012, 96.10, '好评如潮'),
    (4, 'steam', '900004', '像素农场物语',
     'https://cdn.example.com/covers/900004.jpg', 'https://store.steampowered.com/app/900004',
     68.00, 54.40, 20, 2, 3, 15873, 90.30, '特别好评'),
    (5, 'steam', '900005', '霓虹竞速 Neon Rush',
     'https://cdn.example.com/covers/900005.jpg', 'https://store.steampowered.com/app/900005',
     158.00, 39.50, 75, 3, 4, 2210, 81.20, '多半好评'),
    (6, 'steam', '900006', '机械之心',
     'https://cdn.example.com/covers/900006.jpg', 'https://store.steampowered.com/app/900006',
     98.00, 0.00, 100, 0, 5, 6420, 94.80, '好评如潮'),
    (7, 'epic', 'EPIC90001', '沙丘旅人 Dune Wanderer',
     'https://cdn.example.com/covers/EPIC90001.jpg', 'https://store.epicgames.com/p/dune-wanderer',
     168.00, 0.00, 100, 0, 0, 0, 0.00, '限时免费'),
    (8, 'epic', 'EPIC90002', '光之回声',
     'https://cdn.example.com/covers/EPIC90002.jpg', 'https://store.epicgames.com/p/echo-of-light',
     88.00, 88.00, 0, 0, 0, 0, 0.00, '即将免费');

-- ------------------------------------------------------------
-- 收藏（user_id / game_id 对应上面两张表的主键）
-- ------------------------------------------------------------
INSERT IGNORE INTO `user_favorites` (`id`, `user_id`, `game_id`) VALUES
    (1, 1, 1),
    (2, 1, 3),
    (3, 2, 6),
    (4, 2, 7);