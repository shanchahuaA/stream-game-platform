package com.stream.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stream.mapper.GamesMapper;
import com.stream.pojo.Games;
import com.stream.service.CrawlerService;
import com.stream.utils.AppConfig;
import com.stream.utils.MybatisUtil;
import com.stream.utils.PythonManager;
import org.apache.ibatis.session.SqlSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;

public class CrawlerServiceImpl implements CrawlerService {
    private Integer pageSize = 50;
    @Override
    public void startService() {

    }

    @Override
    public void stopService() {

    }

    @Override
    public int steamDiscountCrawler(int limit) {
        int successCount = 0;
        try {
            PythonManager.start();
            Thread.sleep(3000);

            if (PythonManager.isAlive()) {
                SqlSession initSession = null;
                try {
                    initSession = MybatisUtil.getSqlSession();
                    GamesMapper mapper = initSession.getMapper(GamesMapper.class);
                    mapper.resetAllDiscountRank();
                    initSession.commit();
                    System.out.println("折扣重置成功");
                } catch (Exception e) {
                    if (initSession != null) {
                        initSession.rollback();
                    }
                    System.err.println("折扣重置失败" + e.getMessage());
                    throw e;
                } finally {
                    if (initSession != null) {
                        initSession.close();
                    }
                }

                for (int start = 0; start < limit; start += pageSize) {
                    System.out.println("正在获取第" + (start / pageSize + 1) + "页数据");

                    int currentSize = Math.min(pageSize, limit - start);
                    int count = getSteamDiscountData(start, currentSize);
                    if (count == 0) {
                        System.out.println("已没有更多新数据，同步提前结束。");
                        break;
                    }

                    successCount += count;

                    Thread.sleep(1500);
                }

            } else {
                System.err.println("启动失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PythonManager.stop();

        }
        return successCount;
    }
    private int getSteamDiscountData(int start, int pageSize) throws Exception {
        String apiUrl = AppConfig.crawlerBaseUrl() + "/steam/discount?start=" + start + "&limit=" + pageSize;
        URL url = new URL(apiUrl);
        StringBuilder result = null;
        SqlSession sqlSession = null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            result = new StringBuilder();
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
            in.close();
            System.out.println("\nsuccess: " + result.toString());
        } else {
            System.err.println("failed " + responseCode);
            return 0;
        }
        connection.disconnect();

        JSONObject jsonObject = JSON.parseObject(result.toString());
        if (jsonObject == null || !jsonObject.getBooleanValue("success")) {
            System.err.println("JSON解析失败");
            return 0;
        }
        JSONArray dataArray = jsonObject.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            System.out.println("无数据");
            return 0;
        }
        int successCount = 0;


        try {
            sqlSession = MybatisUtil.getSqlSession();
            GamesMapper gamesMapper = sqlSession.getMapper(GamesMapper.class);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                Games game = new Games();
                game.setPlatform(item.getString("platform"));
                game.setPlatformGameId(item.getString("platform_game_id"));
                game.setGname(item.getString("gname"));
                game.setCoverUrl(item.getString("cover_url"));
                game.setShopUrl(item.getString("shop_url"));
                game.setOriginalPrice(item.getDouble("original_price"));
                game.setFinalPrice(item.getDouble("final_price"));
                game.setDiscountPercent(item.getInteger("discount_percent"));
                game.setTopSellerRank(item.getInteger("top_seller_rank"));
                game.setDiscountRank(item.getInteger("discount_rank"));
                game.setReviewCount(item.getInteger("review_count"));
                game.setPositiveRate(item.getBigDecimal("positive_rate"));
                game.setReviewTier(item.getString("review_tier"));
                game.setLastSyncTime(now); // 手动设置本次同步时间
                int rows = gamesMapper.insertGame(game);
                if (rows > 0) {
                    successCount++;
                }

            }
            sqlSession.commit();
        }catch (Exception e){
            sqlSession.rollback();
            e.printStackTrace();
        }finally {

            sqlSession.close();
        }
        return successCount;
    }



    @Override
    public int steamTopSellerCrawler(int limit) {
        int successCount = 0;
        try {
            PythonManager.start();
            Thread.sleep(3000);

            if (PythonManager.isAlive()) {
                SqlSession initSession = null;
                try {
                    initSession = MybatisUtil.getSqlSession();
                    GamesMapper mapper = initSession.getMapper(GamesMapper.class);
                    mapper.resetAllTopSellerRank();
                    initSession.commit();
                    System.out.println("热搜重置成功");
                } catch (Exception e) {
                    if (initSession != null) {
                        initSession.rollback();
                    }
                    System.err.println("热搜重置失败" + e.getMessage());
                    throw e;
                } finally {
                    if (initSession != null) {
                        initSession.close();
                    }
                }

                for (int start = 0; start < limit; start += pageSize) {
                    System.out.println("正在获取热销榜第" + (start / pageSize + 1) + "页数据");

                    int currentSize = Math.min(pageSize, limit - start);
                    int count = getSteamTopSellerData(start, currentSize);
                    if (count == 0) {
                        System.out.println("已没有更多新数据，同步提前结束。");
                        break;
                    }

                    successCount += count;

                    Thread.sleep(1500);
                }

            } else {
                System.err.println("启动失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PythonManager.stop();
        }
        return successCount;
    }

    private int getSteamTopSellerData(int start, int pageSize) throws Exception {
        String apiUrl = AppConfig.crawlerBaseUrl() + "/steam/topsellers?start=" + start + "&limit=" + pageSize;
        URL url = new URL(apiUrl);
        StringBuilder result = null;
        SqlSession sqlSession = null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            result = new StringBuilder();
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
            in.close();
            System.out.println("\nsuccess: " + result.toString());
        } else {
            System.err.println("failed " + responseCode);
            return 0;
        }
        connection.disconnect();

        JSONObject jsonObject = JSON.parseObject(result.toString());
        if (jsonObject == null || !jsonObject.getBooleanValue("success")) {
            System.err.println("JSON解析失败");
            return 0;
        }
        JSONArray dataArray = jsonObject.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            System.out.println("无数据");
            return 0;
        }
        int successCount = 0;

        try {
            sqlSession = MybatisUtil.getSqlSession();
            GamesMapper gamesMapper = sqlSession.getMapper(GamesMapper.class);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                Games game = new Games();
                game.setPlatform(item.getString("platform"));
                game.setPlatformGameId(item.getString("platform_game_id"));
                game.setGname(item.getString("gname"));
                game.setCoverUrl(item.getString("cover_url"));
                game.setShopUrl(item.getString("shop_url"));
                game.setOriginalPrice(item.getDouble("original_price"));
                game.setFinalPrice(item.getDouble("final_price"));
                game.setDiscountPercent(item.getInteger("discount_percent"));
                game.setTopSellerRank(item.getInteger("top_seller_rank"));
                game.setDiscountRank(item.getInteger("discount_rank"));
                game.setReviewCount(item.getInteger("review_count"));
                game.setPositiveRate(item.getBigDecimal("positive_rate"));
                game.setReviewTier(item.getString("review_tier"));
                game.setLastSyncTime(now); // 手动设置本次同步时间
                int rows = gamesMapper.insertGame(game);
                if (rows > 0) {
                    successCount++;
                }
            }
            sqlSession.commit();
        } catch (Exception e) {
            sqlSession.rollback();
            e.printStackTrace();
        } finally {
            sqlSession.close();
        }
        return successCount;
    }


    @Override
    public int epicFreeGamesCrawler() {
        int successCount = 0;
        try {
            PythonManager.start();
            Thread.sleep(3000);

            if (PythonManager.isAlive()) {
                SqlSession initSession = null;
                try {
                    initSession = MybatisUtil.getSqlSession();
                    GamesMapper mapper = initSession.getMapper(GamesMapper.class);
                    mapper.deleteEpicGames();
                    initSession.commit();
                    System.out.println("清空Epic历史数据成功");
                } catch (Exception e) {
                    if (initSession != null) {
                        initSession.rollback();
                    }
                    System.err.println("清空Epic历史数据失败" + e.getMessage());
                    throw e;
                } finally {
                    if (initSession != null) {
                        initSession.close();
                    }
                }

                System.out.println("正在获取 Epic 免费游戏数据");
                successCount = getEpicFreeGamesData();

            } else {
                System.err.println("启动失败");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PythonManager.stop();
        }
        return successCount;
    }

    private int getEpicFreeGamesData() throws Exception {
        String apiUrl = AppConfig.crawlerBaseUrl() + "/epic/freegames";
        URL url = new URL(apiUrl);
        StringBuilder result = null;
        SqlSession sqlSession = null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            result = new StringBuilder();
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
            in.close();
            System.out.println("\nsuccess: " + result.toString());
        } else {
            System.err.println("failed " + responseCode);
            return 0;
        }
        connection.disconnect();

        JSONObject jsonObject = JSON.parseObject(result.toString());
        if (jsonObject == null || !jsonObject.getBooleanValue("success")) {
            System.err.println("JSON解析失败");
            return 0;
        }
        JSONArray dataArray = jsonObject.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            System.out.println("无数据");
            return 0;
        }
        int successCount = 0;

        try {
            sqlSession = MybatisUtil.getSqlSession();
            GamesMapper gamesMapper = sqlSession.getMapper(GamesMapper.class);
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                Games game = new Games();
                game.setPlatform(item.getString("platform"));
                game.setPlatformGameId(item.getString("platform_game_id"));
                game.setGname(item.getString("gname"));
                game.setCoverUrl(item.getString("cover_url"));
                game.setShopUrl(item.getString("shop_url"));
                game.setOriginalPrice(item.getDouble("original_price"));
                game.setFinalPrice(item.getDouble("final_price"));
                game.setDiscountPercent(item.getInteger("discount_percent"));
                game.setTopSellerRank(item.getInteger("top_seller_rank"));
                game.setDiscountRank(item.getInteger("discount_rank"));
                game.setReviewCount(item.getInteger("review_count"));
                game.setPositiveRate(item.getBigDecimal("positive_rate"));
                game.setReviewTier(item.getString("review_tier"));
                game.setLastSyncTime(now);
                int rows = gamesMapper.insertGame(game);
                if (rows > 0) {
                    successCount++;
                }
            }
            sqlSession.commit();
        } catch (Exception e) {
            sqlSession.rollback();
            e.printStackTrace();
        } finally {
            sqlSession.close();
        }
        return successCount;
    }
}
