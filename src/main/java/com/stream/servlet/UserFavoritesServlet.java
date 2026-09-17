package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.stream.pojo.UserFavorites;
import com.stream.service.impl.UserFavoritesServiceImpl;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户收藏管理 Servlet
 * 提供用户收藏游戏的增删查等功能
 *
 * 使用方式：
 *   获取用户收藏列表：/userFavorite?action=getMyFavorites&userId=1
 *   添加收藏：/userFavorite?action=add&userId=1&gameId=2
 *   删除收藏：/userFavorite?action=delete&id=1
 *   检查是否已收藏：/userFavorite?action=isFavorited&userId=1&gameId=2
 */
@WebServlet("/userFavorite")
public class UserFavoritesServlet extends BaseServlet {

    private final UserFavoritesServiceImpl userFavoritesService = new UserFavoritesServiceImpl();

    /**
     * 获取用户的收藏列表
     * 参数：userId - 用户ID（必填）
     */
    public void getMyFavorites(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userIdStr = req.getParameter("userId");

        Map<String, Object> result = new HashMap<>();

        if (userIdStr == null || userIdStr.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：userId");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            Integer userId = Integer.parseInt(userIdStr);
            List<UserFavorites> favorites = userFavoritesService.listMyFavoriteByUserId(userId);
            result.put("status", "success");
            result.put("count", favorites.size());
            result.put("data", favorites);
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "用户ID格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "查询失败：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 添加收藏
     * 参数：userId - 用户ID（必填），gameId - 游戏ID（必填）
     */
    public void add(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userIdStr = req.getParameter("userId");
        String gameIdStr = req.getParameter("gameId");

        Map<String, Object> result = new HashMap<>();

        if (userIdStr == null || userIdStr.isEmpty() || 
            gameIdStr == null || gameIdStr.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：userId, gameId");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            Integer userId = Integer.parseInt(userIdStr);
            Integer gameId = Integer.parseInt(gameIdStr);

            // 先检查是否已经收藏
            Integer count = userFavoritesService.countFavoriteByUserGame(userId, gameId);
            if (count != null && count > 0) {
                result.put("status", "error");
                result.put("message", "该游戏已经在收藏列表中");
                resp.setContentType("application/json;charset=utf-8");
                resp.getWriter().write(JSON.toJSONString(result));
                return;
            }

            int rows = userFavoritesService.insertFavorite(userId, gameId);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "收藏成功");
            } else {
                result.put("status", "error");
                result.put("message", "收藏失败");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "参数格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "收藏异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 删除收藏
     * 参数：id - 收藏记录ID（必填）
     */
    public void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");

        Map<String, Object> result = new HashMap<>();

        if (idStr == null || idStr.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：id");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            Integer id = Integer.parseInt(idStr);
            int rows = userFavoritesService.deleteFavoriteById(id);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "取消收藏成功");
            } else {
                result.put("status", "error");
                result.put("message", "取消收藏失败，记录不存在");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "ID格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "取消收藏异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 检查是否已收藏
     * 参数：userId - 用户ID（必填），gameId - 游戏ID（必填）
     */
    public void isFavorited(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userIdStr = req.getParameter("userId");
        String gameIdStr = req.getParameter("gameId");

        Map<String, Object> result = new HashMap<>();

        if (userIdStr == null || userIdStr.isEmpty() || 
            gameIdStr == null || gameIdStr.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：userId, gameId");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            Integer userId = Integer.parseInt(userIdStr);
            Integer gameId = Integer.parseInt(gameIdStr);
            Integer count = userFavoritesService.countFavoriteByUserGame(userId, gameId);
            
            result.put("status", "success");
            result.put("isFavorited", count != null && count > 0);
            result.put("count", count != null ? count : 0);
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "参数格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "查询异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }
}
