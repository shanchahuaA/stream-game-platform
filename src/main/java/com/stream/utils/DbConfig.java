package com.stream.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 数据库连接配置：环境变量优先，其次是本地 db.properties。
 *
 * 环境变量：DB_URL / DB_USER / DB_PASSWORD / DB_DRIVER
 * 本地文件：classpath 下的 db.properties（已被 .gitignore 排除，模板见 db.properties.example）
 *
 * 解析结果交给 MyBatis，用来填充 mybatis-config.xml 里的 ${url} 等占位符。
 */
public final class DbConfig {

    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";

    private DbConfig() {
    }

    public static Properties load() {
        Properties file = loadFile();

        String url = pick("DB_URL", file, "url");
        String username = pick("DB_USER", file, "username");
        String password = pick("DB_PASSWORD", file, "password");

        if (url == null || username == null || password == null) {
            throw new IllegalStateException(
                    "数据库连接配置不完整，缺少: " + missing(url, username, password) + "\n"
                            + "二选一：\n"
                            + "  1) 设置环境变量 DB_URL / DB_USER / DB_PASSWORD\n"
                            + "  2) 把 src/main/resources/db.properties.example 复制为 db.properties 并填入真实连接信息");
        }

        Properties resolved = new Properties();
        resolved.setProperty("driverClassName", pick("DB_DRIVER", file, "driverClassName", DEFAULT_DRIVER));
        resolved.setProperty("url", url);
        resolved.setProperty("username", username);
        resolved.setProperty("password", password);
        return resolved;
    }

    private static String missing(String url, String username, String password) {
        StringBuilder sb = new StringBuilder();
        if (url == null) {
            sb.append("DB_URL ");
        }
        if (username == null) {
            sb.append("DB_USER ");
        }
        if (password == null) {
            sb.append("DB_PASSWORD ");
        }
        return sb.toString().trim();
    }

    private static String pick(String envKey, Properties file, String fileKey) {
        return pick(envKey, file, fileKey, null);
    }

    private static String pick(String envKey, Properties file, String fileKey, String fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String fromFile = file.getProperty(fileKey);
        if (fromFile != null && !fromFile.trim().isEmpty()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static Properties loadFile() {
        Properties props = new Properties();
        try (InputStream in = DbConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                // 显式按 UTF-8 读，否则 Properties 会用 ISO-8859-1
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.err.println("[DbConfig] 读取 db.properties 失败: " + e.getMessage());
        }
        return props;
    }
}