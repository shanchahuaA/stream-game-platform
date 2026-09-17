package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.stream.pojo.User;
import com.stream.service.impl.UserServiceImpl;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 Servlet（管理员专用）
 * 提供用户的增删改查功能
 *
 * 使用方式：
 *   获取所有用户列表：/userManagement?action=getAllUsers&sortType=newest
 *   新增用户：/userManagement?action=addUser&username=xxx&password=xxx&nickname=xxx
 *   修改用户：/userManagement?action=updateUser&id=1&username=xxx&password=xxx&nickname=xxx&avatar=xxx&status=1
 *   删除用户：/userManagement?action=deleteUser&id=1
 */
@WebServlet("/userManagement")
public class UserManagementServlet extends BaseServlet {

    private final UserServiceImpl userService = new UserServiceImpl();

    /**
     * 获取所有用户列表
     * 参数：sortType - 排序类型（可选，默认 newest）
     */
    public void getAllUsers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sortType = req.getParameter("sortType");
        if (sortType == null || sortType.isEmpty()) {
            sortType = "newest";
        }

        Map<String, Object> result = new HashMap<>();

        try {
            List<User> users = userService.getUserAll(sortType);
            result.put("status", "success");
            result.put("count", users.size());
            result.put("data", users);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "查询失败：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 新增用户
     * 参数：username（必填），password（必填），nickname（必填）
     */
    public void addUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String nickname = req.getParameter("nickname");

        Map<String, Object> result = new HashMap<>();

        if (username == null || username.isEmpty() || 
            password == null || password.isEmpty() || 
            nickname == null || nickname.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：username, password, nickname");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            int rows = userService.insertUser(username, password, nickname);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "用户创建成功");
            } else {
                result.put("status", "error");
                result.put("message", "用户创建失败");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "创建异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 修改用户信息
     * 参数：id（必填），username（可选），password（可选），nickname（可选），avatar（可选），status（可选）
     */
    public void updateUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String nickname = req.getParameter("nickname");
        String avatar = req.getParameter("avatar");
        String statusStr = req.getParameter("status");

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
            Integer status = statusStr != null && !statusStr.isEmpty() ? Integer.parseInt(statusStr) : null;
            
            int rows = userService.updateUser(id, username, password, nickname, avatar, status);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "用户更新成功");
            } else {
                result.put("status", "error");
                result.put("message", "用户更新失败");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "参数格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "更新异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 删除用户
     * 参数：id（必填）
     */
    public void deleteUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
            int rows = userService.deleteUser(id);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "用户删除成功");
            } else {
                result.put("status", "error");
                result.put("message", "用户删除失败");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "ID格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "删除异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }
}
