package com.stream.servlet;

import com.alibaba.fastjson.JSON;
import com.stream.pojo.User;
import com.stream.service.impl.UserServiceImpl;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 Servlet
 * 提供用户的登录、注册、查询、更新、删除等功能
 *
 * 使用方式：
 *   用户登录：/user?action=login&username=xxx&password=xxx
 *   获取所有用户：/user?action=getAll&sortType=xxx
 *   根据ID查询用户：/user?action=getById&id=1
 *   根据昵称搜索用户：/user?action=searchByNick&nickname=xxx
 *   注册用户：/user?action=register&username=xxx&password=xxx&nickname=xxx
 *   更新用户信息：/user?action=update&id=1&username=xxx&password=xxx&nickname=xxx&avatar=xxx&status=1
 *   删除用户：/user?action=delete&id=1
 *   更新个人资料（含头像）：POST /user?action=updateProfile (multipart/form-data)
 */
@MultipartConfig(maxFileSize = 2097152, maxRequestSize = 4194304) // 最大2MB文件，4MB请求
@WebServlet("/user")
public class UserServlet extends BaseServlet {

    private final UserServiceImpl userService = new UserServiceImpl();

    /**
     * 用户登录
     * 参数：username - 用户名（必填），password - 密码（必填）
     */
    public void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        Map<String, Object> result = new HashMap<>();

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：username, password");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            User user = userService.doLogin(username, password);
            if (user != null) {
                if (user.getStatus() != null && user.getStatus() == 0) {
                    result.put("status", "error");
                    result.put("message", "该账号已被禁用，请联系管理员！");
                } else {
                    result.put("status", "success");
                    result.put("message", "登录成功");
                    result.put("data", user);
                }
            } else {
                result.put("status", "error");
                result.put("message", "用户名或密码错误");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "登录异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 获取所有用户列表
     * 参数：sortType - 排序类型（可选）
     */
    public void getAll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sortType = req.getParameter("sortType");

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
     * 根据ID查询用户
     * 参数：id - 用户ID（必填）
     */
    public void getById(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
            User user = userService.selectUserById(id);
            if (user != null) {
                result.put("status", "success");
                result.put("data", user);
            } else {
                result.put("status", "error");
                result.put("message", "用户不存在");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "ID格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "查询失败：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 根据昵称搜索用户
     * 参数：nickname - 昵称（必填）
     */
    public void searchByNick(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String nickname = req.getParameter("nickname");

        Map<String, Object> result = new HashMap<>();

        if (nickname == null || nickname.isEmpty()) {
            result.put("status", "error");
            result.put("message", "缺少必填参数：nickname");
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write(JSON.toJSONString(result));
            return;
        }

        try {
            List<User> users = userService.selectUserByNick(nickname);
            result.put("status", "success");
            result.put("count", users.size());
            result.put("data", users);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "搜索失败：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 注册用户
     * 参数：username - 用户名（必填），password - 密码（必填），nickname - 昵称（必填）
     */
    public void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
                result.put("message", "注册成功");
            } else {
                result.put("status", "error");
                result.put("message", "注册失败，可能用户名已存在");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "注册异常：" + e.getMessage());
        }

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }

    /**
     * 更新用户信息
     * 参数：id - 用户ID（必填），其他参数可选（username, password, nickname, avatar, status）
     */
    public void update(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
            String username = req.getParameter("username");
            String password = req.getParameter("password");
            String nickname = req.getParameter("nickname");
            String avatar = req.getParameter("avatar");
            String statusStr = req.getParameter("status");

            Integer status = null;
            if (statusStr != null && !statusStr.isEmpty()) {
                status = Integer.parseInt(statusStr);
            }

            int rows = userService.updateUser(id, username, password, nickname, avatar, status);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "更新成功");
            } else {
                result.put("status", "error");
                result.put("message", "更新失败，用户不存在");
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
     * 参数：id - 用户ID（必填）
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
            int rows = userService.deleteUser(id);
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "删除成功");
            } else {
                result.put("status", "error");
                result.put("message", "删除失败，用户不存在");
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

    /**
     * 更新用户个人资料（支持头像上传）
     * POST /user?action=updateProfile
     * 参数（multipart/form-data）：
     *   - id: 用户ID（必填）
     *   - nickname: 昵称（可选）
     *   - password: 新密码（可选，留空则不修改）
     *   - avatar: 头像文件（可选，图片格式 jpg/png/gif/webp）
     */
    public void updateProfile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取用户ID
            String idStr = req.getParameter("id");
            if (idStr == null || idStr.isEmpty()) {
                result.put("status", "error");
                result.put("message", "缺少必填参数：id");
                resp.setContentType("application/json;charset=utf-8");
                resp.getWriter().write(JSON.toJSONString(result));
                return;
            }
            
            Integer userId = Integer.parseInt(idStr);
            String nickname = req.getParameter("nickname");
            String password = req.getParameter("password");
            String avatarPath = null;
            
            // 处理头像上传
            Part avatarPart = req.getPart("avatar");
            if (avatarPart != null && avatarPart.getSize() > 0) {
                String fileName = avatarPart.getSubmittedFileName();
                if (fileName != null && !fileName.isEmpty()) {
                    // 验证文件类型
                    String contentType = avatarPart.getContentType();
                    if (contentType == null || !contentType.startsWith("image/")) {
                        result.put("status", "error");
                        result.put("message", "只支持图片格式（jpg/png/gif/webp）");
                        resp.setContentType("application/json;charset=utf-8");
                        resp.getWriter().write(JSON.toJSONString(result));
                        return;
                    }
                    
                    // 生成唯一文件名：userId_timestamp.ext
                    String ext = fileName.substring(fileName.lastIndexOf("."));
                    String uniqueFileName = userId + "_" + System.currentTimeMillis() + ext;
                    
                    // 获取webapp目录下的avatars文件夹路径
                    String webappPath = req.getServletContext().getRealPath("/");
                    String avatarDir = webappPath + "assets/imgs/avatars/";
                    
                    // 确保目录存在
                    File dir = new File(avatarDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    
                    // 保存文件
                    String savePath = avatarDir + uniqueFileName;
                    Files.copy(avatarPart.getInputStream(), Paths.get(savePath));
                    
                    // 设置相对路径（用于数据库存储和前端访问）
                    avatarPath = "assets/imgs/avatars/" + uniqueFileName;
                }
            }
            
            // 如果密码为空字符串，设为null表示不修改
            if (password != null && password.isEmpty()) {
                password = null;
            }
            
            // 调用Service更新
            int rows = userService.updateUser(userId, null, password, nickname, avatarPath, null);
            
            if (rows > 0) {
                result.put("status", "success");
                result.put("message", "更新成功");
                // 返回更新后的用户信息
                User updatedUser = userService.selectUserById(userId);
                result.put("data", updatedUser);
            } else {
                result.put("status", "error");
                result.put("message", "更新失败，用户不存在");
            }
        } catch (NumberFormatException e) {
            result.put("status", "error");
            result.put("message", "参数格式错误");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "更新异常：" + e.getMessage());
            e.printStackTrace();
        }
        
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(JSON.toJSONString(result));
    }
}
