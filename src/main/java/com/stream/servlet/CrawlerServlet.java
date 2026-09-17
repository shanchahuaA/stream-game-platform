package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.stream.service.CrawlerService;
import com.stream.service.impl.CrawlerServiceImpl;
import com.stream.utils.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.DayOfWeek;

@WebServlet("/crawler")
public class CrawlerServlet extends BaseServlet {

    private final CrawlerService crawlerService = new CrawlerServiceImpl();
    private static final int LIMIT = 10000; // 折扣爬虫临时测试上限
    private static final int TOP_SELLER_LIMIT = 100; // 热销榜爬虫临时测试上限
    private static LocalDate lastWeeklySyncDate = null; // 记录最近一次自动周同步成功完成的日期

    /**
     * 启动 Steam 折扣爬虫 (写死最大 10000)
     */
    public void startSteamDiscount(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        try {
            int count = crawlerService.steamDiscountCrawler(LIMIT);
            resp.getWriter().write(JSON.toJSONString(Result.success(count)));
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write(JSON.toJSONString(Result.error("Steam折扣同步异常: " + e.getMessage())));
        }
    }

    /**
     * 启动 Steam 热销爬虫 (写死最大 100)
     */
    public void startSteamTopSeller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        try {
            int count = crawlerService.steamTopSellerCrawler(TOP_SELLER_LIMIT);
            resp.getWriter().write(JSON.toJSONString(Result.success(count)));
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write(JSON.toJSONString(Result.error("Steam热销榜同步异常: " + e.getMessage())));
        }
    }

    /**
     * 启动 Epic 免费游戏爬虫
     */
    public void startEpicFreeGames(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        try {
            int count = crawlerService.epicFreeGamesCrawler();
            resp.getWriter().write(JSON.toJSONString(Result.success(count)));
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write(JSON.toJSONString(Result.error("Epic免费游戏同步异常: " + e.getMessage())));
        }
    }

    /**
     * 周一自动爬虫自检与顺序同步触发接口（折扣 -> 热销 -> Epic）
     */
    public void checkAndTriggerWeekly(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        
        // 1. 判断是否是周一
        if (dayOfWeek != DayOfWeek.MONDAY) {
            resp.getWriter().write(JSON.toJSONString(Result.success("今天不是周一，无需自动同步数据。")));
            return;
        }
        
        // 2. 判断今天是否已经进行过自动同步了
        if (lastWeeklySyncDate != null && lastWeeklySyncDate.equals(today)) {
            resp.getWriter().write(JSON.toJSONString(Result.success("今天周一已成功执行过自动同步，无需重复执行。")));
            return;
        }
        
        // 3. 异步线程顺序同步（折扣 -> 热销 -> Epic）
        new Thread(() -> {
            try {
                System.out.println("====== [Weekly Auto Sync] 开始每周一自动同步数据 ======");
                
                // A. 同步折扣
                System.out.println("[Weekly Auto Sync] 1. 开始同步 Steam 折扣数据...");
                int discountCount = crawlerService.steamDiscountCrawler(LIMIT);
                System.out.println("[Weekly Auto Sync] Steam 折扣数据同步完毕，更新记录: " + discountCount);
                
                // B. 同步热销
                System.out.println("[Weekly Auto Sync] 2. 开始同步 Steam 热销数据...");
                int topSellerCount = crawlerService.steamTopSellerCrawler(TOP_SELLER_LIMIT);
                System.out.println("[Weekly Auto Sync] Steam 热销数据同步完毕，更新记录: " + topSellerCount);
                
                // C. 同步 Epic
                System.out.println("[Weekly Auto Sync] 3. 开始同步 Epic 免费游戏数据...");
                int epicCount = crawlerService.epicFreeGamesCrawler();
                System.out.println("[Weekly Auto Sync] Epic 免费游戏数据同步完毕，更新记录: " + epicCount);
                
                // 4. 更新同步成功日期
                lastWeeklySyncDate = today;
                System.out.println("====== [Weekly Auto Sync] 每周一数据自动同步顺利完成！ ======");
            } catch (Exception e) {
                System.err.println("[Weekly Auto Sync] 自动同步遇到异常: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        
        resp.getWriter().write(JSON.toJSONString(Result.success("自动数据检测到符合周一条件，后台同步任务已异步启动（Steam折扣 -> Steam热销 -> Epic）")));
    }
}
