package com.stream.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 读取 classpath 上的 app.properties，并允许环境变量覆盖其中的每一项。
 *
 * 取值顺序：环境变量 > app.properties > 代码内兜底值。
 * 这样默认值可以随仓库出行，机器相关的部分（解释器路径、工作目录）不必写死在代码里。
 */
public final class AppConfig {

    private static final Properties FILE_PROPS = load();

    private AppConfig() {
    }

    /** Python 解释器，留空即使用 PATH 上的 python。 */
    public static String pythonExecutable() {
        return get("PYTHON_EXE", "python.executable", "python");
    }

    /** 爬虫服务的工作目录。 */
    public static String crawlerWorkDir() {
        return get("PYTHON_CRAWLER_WORKDIR", "python.crawler.workdir", "crawler");
    }

    /** 爬虫微服务端口。启动方（PythonManager）与调用方（CrawlerServiceImpl）共用。 */
    public static int crawlerPort() {
        return getInt("CRAWLER_PORT", "crawler.port", 8099);
    }

    /** 游戏助手微服务端口。 */
    public static int gameFinderPort() {
        return getInt("GAME_FINDER_PORT", "game-finder.port", 8090);
    }

    /** 爬虫微服务基地址，Java 侧所有对爬虫的调用都由它拼接。 */
    public static String crawlerBaseUrl() {
        return "http://127.0.0.1:" + crawlerPort();
    }

    /** 游戏助手微服务基地址。 */
    public static String gameFinderBaseUrl() {
        return "http://127.0.0.1:" + gameFinderPort();
    }

    private static String get(String envKey, String fileKey, String fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String fromFile = FILE_PROPS.getProperty(fileKey);
        if (fromFile != null && !fromFile.trim().isEmpty()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static int getInt(String envKey, String fileKey, int fallback) {
        String raw = get(envKey, fileKey, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            System.err.println("[AppConfig] " + fileKey + " 不是合法端口号: " + raw + "，改用 " + fallback);
            return fallback;
        }
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (in != null) {
                // 显式按 UTF-8 读，否则 Properties 会用 ISO-8859-1，文件里的中文注释会变成乱码
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.err.println("[AppConfig] 读取 app.properties 失败: " + e.getMessage());
        }
        return props;
    }
}