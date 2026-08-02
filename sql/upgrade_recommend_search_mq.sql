USE `hmdp`;

-- ---------------------------------------------------------------------
-- 1. 秒杀订单表增加联合唯一索引 user_id + voucher_id（消费幂等兜底）
--    如果已存在则跳过，避免重复创建报错。
-- ---------------------------------------------------------------------
SET @idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hmdp'
      AND TABLE_NAME   = 'tb_voucher_order'
      AND INDEX_NAME   = 'uk_user_voucher'
);

SET @ddl = IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX uk_user_voucher ON tb_voucher_order (user_id, voucher_id)',
    'SELECT ''INDEX uk_user_voucher already exists, skip''');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2.商铺表 type_id 索引，提升按类型查询 / 推荐候选查询性能
-- ---------------------------------------------------------------------
SET @shop_idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hmdp'
      AND TABLE_NAME   = 'tb_shop'
      AND INDEX_NAME   = 'idx_shop_type'
);

SET @shop_ddl = IF(@shop_idx_exists = 0,
    'CREATE INDEX idx_shop_type ON tb_shop (type_id)',
    'SELECT ''INDEX idx_shop_type already exists, skip''');

PREPARE stmt2 FROM @shop_ddl;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ---------------------------------------------------------------------
-- 3.博客表 user_id / shop_id 索引，提升推荐候选查询性能
-- ---------------------------------------------------------------------
SET @blog_user_idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hmdp'
      AND TABLE_NAME   = 'tb_blog'
      AND INDEX_NAME   = 'idx_blog_user'
);

SET @blog_user_ddl = IF(@blog_user_idx_exists = 0,
    'CREATE INDEX idx_blog_user ON tb_blog (user_id)',
    'SELECT ''INDEX idx_blog_user already exists, skip''');

PREPARE stmt3 FROM @blog_user_ddl;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

SET @blog_shop_idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hmdp'
      AND TABLE_NAME   = 'tb_blog'
      AND INDEX_NAME   = 'idx_blog_shop'
);

SET @blog_shop_ddl = IF(@blog_shop_idx_exists = 0,
    'CREATE INDEX idx_blog_shop ON tb_blog (shop_id)',
    'SELECT ''INDEX idx_blog_shop already exists, skip''');

PREPARE stmt4 FROM @blog_shop_ddl;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;
