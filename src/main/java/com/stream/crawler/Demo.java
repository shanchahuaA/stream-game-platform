package com.stream.crawler;

import com.stream.utils.AppConfig;
import com.stream.utils.PythonManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 手动冒烟测试：启动爬虫服务并请求一次，用来确认端口与解释器配置通不通。 */
public class Demo {
    public static void main(String[] args) {
        try {

            PythonManager.start();


            Thread.sleep(3000);

            if (PythonManager.isAlive()) {
                requestSpecialsData();
            } else {
                System.err.println("启动失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PythonManager.stop();
        }
    }

    private static void requestSpecialsData() throws Exception {
        String apiUrl = AppConfig.crawlerBaseUrl() + "/steam/discount?start=0&limit=5";
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            StringBuilder result = new StringBuilder();
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
            in.close();
            System.out.println("\nsuccess: " + result.toString());
        } else {
            System.err.println("failed " + responseCode);
        }
        connection.disconnect();
    }
}