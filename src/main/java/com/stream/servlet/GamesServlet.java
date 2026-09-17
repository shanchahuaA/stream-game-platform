package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageInfo;
import com.stream.pojo.Games;
import com.stream.service.impl.GamesServiceImpl;
import com.stream.utils.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@WebServlet("/games")
public class GamesServlet extends BaseServlet {

    private final GamesServiceImpl gamesService = new GamesServiceImpl();

    /**
     * 增：添加游戏（仅 gname, shopUrl, platformGameId 必填）
     */
    public void addGame(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String gname = req.getParameter("gname");
        String shopUrl = req.getParameter("shopUrl");
        String platformGameId = req.getParameter("platformGameId");

        resp.setContentType("application/json;charset=utf-8");
        if (gname == null || gname.isEmpty()
                || shopUrl == null || shopUrl.isEmpty()
                || platformGameId == null || platformGameId.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少必填参数：gname, shopUrl, platformGameId")));
            return;
        }

        Games game = new Games(gname, shopUrl, platformGameId);
        int rows = gamesService.addGame(game);
        resp.getWriter().write(JSON.toJSONString(rows > 0 ? Result.success("添加成功") : Result.error("添加失败")));
    }

    /**
     * 删：删除游戏
     */
    public void deleteGame(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        resp.setContentType("application/json;charset=utf-8");
        if (idStr == null || idStr.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少id参数")));
            return;
        }
        int rows = gamesService.deleteGame(Integer.parseInt(idStr));
        resp.getWriter().write(JSON.toJSONString(rows > 0 ? Result.success("删除成功") : Result.error("删除失败")));
    }

    /**
     * 改：更新游戏
     */
    public void updateGame(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        resp.setContentType("application/json;charset=utf-8");
        if (idStr == null || idStr.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少id参数")));
            return;
        }

        Games game = new Games();
        game.setId(Integer.parseInt(idStr));

        String platform = req.getParameter("platform");
        String platformGameId = req.getParameter("platformGameId");
        String gname = req.getParameter("gname");
        String coverUrl = req.getParameter("coverUrl");
        String shopUrl = req.getParameter("shopUrl");
        String originalPriceStr = req.getParameter("originalPrice");
        String finalPriceStr = req.getParameter("finalPrice");
        String discountPercentStr = req.getParameter("discountPercent");
        String topSellerRankStr = req.getParameter("topSellerRank");
        String discountRankStr = req.getParameter("discountRank");
        String reviewCountStr = req.getParameter("reviewCount");
        String positiveRateStr = req.getParameter("positiveRate");
        String reviewTier = req.getParameter("reviewTier");

        if (platform != null) game.setPlatform(platform);
        if (platformGameId != null) game.setPlatformGameId(platformGameId);
        if (gname != null) game.setGname(gname);
        if (coverUrl != null) game.setCoverUrl(coverUrl);
        if (shopUrl != null) game.setShopUrl(shopUrl);
        if (originalPriceStr != null) game.setOriginalPrice(Double.parseDouble(originalPriceStr));
        if (finalPriceStr != null) game.setFinalPrice(Double.parseDouble(finalPriceStr));
        if (discountPercentStr != null) game.setDiscountPercent(Integer.parseInt(discountPercentStr));
        if (topSellerRankStr != null) game.setTopSellerRank(Integer.parseInt(topSellerRankStr));
        if (discountRankStr != null) game.setDiscountRank(Integer.parseInt(discountRankStr));
        if (reviewCountStr != null) game.setReviewCount(Integer.parseInt(reviewCountStr));
        if (positiveRateStr != null) game.setPositiveRate(new BigDecimal(positiveRateStr));
        if (reviewTier != null) game.setReviewTier(reviewTier);

        int rows = gamesService.updateGame(game);
        resp.getWriter().write(JSON.toJSONString(rows > 0 ? Result.success("更新成功") : Result.error("更新失败")));
    }

    /**
     * 查：根据 ID 查询单个游戏（返回 JSON）
     */
    public void getGameById(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        resp.setContentType("application/json;charset=utf-8");
        if (idStr == null || idStr.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少id参数")));
            return;
        }
        Games game = gamesService.getGameById(Integer.parseInt(idStr));
        resp.getWriter().write(JSON.toJSONString(Result.success(game)));
    }

    /**
     * 查：查询所有游戏（返回 JSON 列表）
     */
    public void getAllGames(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Games> games = gamesService.getAllGames();
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(Result.success(games)));
    }

    /**
     * 查：分页查询游戏（返回 JSON，包含分页信息）
     * 参数：pageNum 页码（默认1），pageSize 每页条数（默认10）
     */
    public void getGamesByPage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pageNumStr = req.getParameter("pageNum");
        String pageSizeStr = req.getParameter("pageSize");

        int pageNum = (pageNumStr != null && !pageNumStr.isEmpty()) ? Integer.parseInt(pageNumStr) : 1;
        int pageSize = (pageSizeStr != null && !pageSizeStr.isEmpty()) ? Integer.parseInt(pageSizeStr) : 10;

        PageInfo<Games> pageInfo = gamesService.getGamesByPage(pageNum, pageSize);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 查：查询全部游戏 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
     * 参数：pageNum 页码（默认1），pageSize 每页条数（默认10）
     */
    public void getAllByTopSeller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int pageNum = parseOrDefault(req.getParameter("pageNum"), 1);
        int pageSize = parseOrDefault(req.getParameter("pageSize"), 10);

        PageInfo<Games> pageInfo = gamesService.getAllByTopSeller(pageNum, pageSize);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 查：查询全部游戏 - 按 discount_rank 升序，rank=0 按 review_count 降序
     * 参数：pageNum 页码（默认1），pageSize 每页条数（默认10）
     */
    public void getAllByDiscount(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int pageNum = parseOrDefault(req.getParameter("pageNum"), 1);
        int pageSize = parseOrDefault(req.getParameter("pageSize"), 10);

        PageInfo<Games> pageInfo = gamesService.getAllByDiscount(pageNum, pageSize);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 搜：按游戏名模糊查询 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
     * 参数：gname 游戏名（必填），pageNum 页码（默认1），pageSize 每页条数（默认10）
     */
    public void searchByGnameTopSeller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String gname = req.getParameter("gname");
        resp.setContentType("application/json;charset=utf-8");
        if (gname == null || gname.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少gname参数")));
            return;
        }
        int pageNum = parseOrDefault(req.getParameter("pageNum"), 1);
        int pageSize = parseOrDefault(req.getParameter("pageSize"), 10);

        PageInfo<Games> pageInfo = gamesService.searchByGnameTopSeller(gname, pageNum, pageSize);
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 搜：按游戏名模糊查询 - 按 discount_rank 升序，rank=0 按 review_count 降序
     * 参数：gname 游戏名（必填），pageNum 页码（默认1），pageSize 每页条数（默认10）
     */
    public void searchByGnameDiscount(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String gname = req.getParameter("gname");
        resp.setContentType("application/json;charset=utf-8");
        if (gname == null || gname.isEmpty()) {
            resp.getWriter().write(JSON.toJSONString(Result.error(400, "缺少gname参数")));
            return;
        }
        int pageNum = parseOrDefault(req.getParameter("pageNum"), 1);
        int pageSize = parseOrDefault(req.getParameter("pageSize"), 10);

        PageInfo<Games> pageInfo = gamesService.searchByGnameDiscount(gname, pageNum, pageSize);
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 查：喜加一（限免）游戏分页查询
     */
    public void getGiveawayGames(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pageNumStr = req.getParameter("pageNum");
        String pageSizeStr = req.getParameter("pageSize");

        int pageNum = (pageNumStr != null && !pageNumStr.isEmpty()) ? Integer.parseInt(pageNumStr) : 1;
        int pageSize = (pageSizeStr != null && !pageSizeStr.isEmpty()) ? Integer.parseInt(pageSizeStr) : 10;

        PageInfo<Games> pageInfo = gamesService.getGiveawayGames(pageNum, pageSize);
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(Result.success(pageInfo)));
    }

    /**
     * 工具方法：字符串转 int，为空 or 解析失败返回默认值
     */
    private int parseOrDefault(String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
