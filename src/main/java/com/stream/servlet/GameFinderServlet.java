package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.stream.utils.AppConfig;
import com.stream.utils.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 游戏助手 Servlet —— 代理转发请求到 Python FastAPI 微服务
 *
 * 数据流：前端 → GameFinderServlet → Python FastAPI (game-finder/main.py) → 返回游戏链接
 *
 * 接口说明（供 Java 开发者参考）：
 *   1. findGame: 接收用户自然语言查询，转发到 Python 微服务获取游戏链接
 *      调用方式: GET /gameFinder?action=findGame&userQuery=我是学生我想玩黑神话悟空
 *      返回格式: Result.success(data) 或 Result.error(message)
 *      data 内容: {success: boolean, game_name: String, game_url: String, message: String}
 *
 *   2. healthCheck: 检测 Python 微服务是否在线
 *      调用方式: GET /gameFinder?action=healthCheck
 *      返回格式: Result.success("Python微服务在线") 或 Result.error("Python微服务未启动")
 */
@WebServlet("/gameFinder")
public class GameFinderServlet extends BaseServlet {

    /** Python FastAPI 微服务地址 —— 游戏搜索接口（端口取自 app.properties，Java 与 Python 两侧共用一个值） */
    private static final String PYTHON_SERVICE_URL = AppConfig.gameFinderBaseUrl() + "/api/v1/find-game";
    /** Python FastAPI 微服务地址 —— 健康检查接口 */
    private static final String PYTHON_HEALTH_URL = AppConfig.gameFinderBaseUrl() + "/health";
    /** HTTP 请求超时时间（毫秒）—— 大模型多次 Tool Call 推理较慢，放宽至 35 秒 */
    private static final int TIMEOUT_MS = 35000;

    /**
     * 核心接口：查找游戏直达链接
     *
     * 前端调用: fetch('gameFinder?action=findGame&userQuery=' + encodeURIComponent(query))
     *
     * 处理流程：
     *   1. 读取前端传入的 userQuery 参数
     *   2. 构造 JSON 请求体 {"user_query": "xxx"}
     *   3. 通过 HttpURLConnection POST 到 Python 微服务
     *   4. 解析 Python 返回的 JSON 响应
     *   5. 包装为 Result 格式返回给前端
     */
    public void findGame(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 1. 读取并校验用户查询
        String userQuery = req.getParameter("userQuery");
        resp.setContentType("application/json;charset=utf-8");

        if (userQuery == null || userQuery.trim().isEmpty()) {
            try {
                resp.getWriter().write(JSON.toJSONString(Result.error(400, "查询内容不能为空")));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 2. 构造发送给 Python 微服务的 JSON 请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("user_query", userQuery.trim());
        byte[] bodyBytes = requestBody.toJSONString().getBytes("UTF-8");

        // 3. 通过 HttpURLConnection 发送 POST 请求到 Python 微服务
        try {
            URL url = new URL(PYTHON_SERVICE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            // 写入请求体
            OutputStream out = conn.getOutputStream();
            out.write(bodyBytes);
            out.flush();
            out.close();

            // 4. 读取 Python 微服务的响应
            int statusCode = conn.getResponseCode();
            BufferedReader reader;
            if (statusCode >= 200 && statusCode < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            }

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();
            conn.disconnect();

            String responseStr = responseBuilder.toString();

            // 5. 解析 Python 响应并包装为 Result 格式
            JSONObject pyResult = JSON.parseObject(responseStr);
            Map<String, Object> data = new HashMap<>();
            data.put("success", pyResult.getBooleanValue("success"));
            data.put("game_name", pyResult.getString("game_name"));
            data.put("game_url", pyResult.getString("game_url"));
            data.put("message", pyResult.getString("message"));

            resp.getWriter().write(JSON.toJSONString(Result.success(data)));

        } catch (java.net.ConnectException e) {
            // Python 微服务未启动
            resp.getWriter().write(JSON.toJSONString(Result.error("Python微服务未启动，请先双击运行项目根目录下的 start_game_helper.bat")));
        } catch (java.net.SocketTimeoutException e) {
            // 请求超时
            resp.getWriter().write(JSON.toJSONString(Result.error("请求超时，目标网站响应较慢，请稍后重试")));
        } catch (Exception e) {
            // 其他异常（解析失败、网络错误等）
            resp.getWriter().write(JSON.toJSONString(Result.error("Python微服务调用失败: " + e.getMessage())));
        }
    }

    /**
     * 健康检查：检测 Python 微服务是否在线
     *
     * 前端调用: fetch('gameFinder?action=healthCheck')
     * 返回: Result.success("在线") 或 Result.error("Python微服务未启动")
     */
    public void healthCheck(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            URL url = new URL(PYTHON_HEALTH_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);  // 健康检查超时 5 秒
            conn.setReadTimeout(5000);

            int statusCode = conn.getResponseCode();
            conn.disconnect();

            if (statusCode == 200) {
                resp.getWriter().write(JSON.toJSONString(Result.success("Python微服务在线")));
            } else {
                resp.getWriter().write(JSON.toJSONString(Result.error("Python微服务异常")));
            }

        } catch (java.net.ConnectException e) {
            resp.getWriter().write(JSON.toJSONString(Result.error("Python微服务未启动")));
        } catch (Exception e) {
            resp.getWriter().write(JSON.toJSONString(Result.error("Python微服务连接失败")));
        }
    }
}
