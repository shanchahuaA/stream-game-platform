package com.stream.utils;

import java.io.File;

/**
 * 管理 Steam / Epic 爬虫 Python 服务（crawler/app.py）的生命周期。
 *
 * 解释器、工作目录、端口都来自 {@link AppConfig}（环境变量优先），不再写死本机路径。
 * 端口与 {@link com.stream.service.impl.CrawlerServiceImpl} 调用时取的是同一个值，
 * 不会出现「启动在 A 端口、调用 B 端口」的静默失效。
 */
public class PythonManager {

    private static Process pythonProcess;

    private PythonManager() {
    }

    public static synchronized void start() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            System.out.println("爬虫服务已在运行");
            return;
        }

        int port = AppConfig.crawlerPort();
        String python = AppConfig.pythonExecutable();
        File workDir = new File(AppConfig.crawlerWorkDir());

        if (!workDir.isDirectory()) {
            System.err.println("爬虫工作目录不存在: " + workDir.getAbsolutePath()
                    + "（相对路径是相对 JVM 工作目录解析的；用 Tomcat 部署时请用环境变量 "
                    + "PYTHON_CRAWLER_WORKDIR 指定 crawler 目录的绝对路径）");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    python, "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", String.valueOf(port)
            );
            pb.directory(workDir);
            pb.environment().put("PYTHONIOENCODING", "UTF-8");
            pb.environment().put("CRAWLER_PORT", String.valueOf(port));

            pb.inheritIO();

            pythonProcess = pb.start();
            System.out.println("爬虫服务启动中: " + python + " @ " + workDir.getAbsolutePath() + "（端口 " + port + "）");
        } catch (Exception e) {
            System.err.println("爬虫服务启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void stop() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            System.out.println("爬虫服务已停止，端口已释放");
        } else {
            System.out.println("爬虫服务未在运行");
        }
    }

    public static boolean isAlive() {
        return pythonProcess != null && pythonProcess.isAlive();
    }
}