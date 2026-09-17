package com.stream.servlet;

import com.alibaba.fastjson.JSONObject;
import com.stream.pojo.User;
import com.stream.service.UserService;
import com.stream.service.impl.UserServiceImpl;
import com.stream.utils.MybatisUtil;
import org.apache.ibatis.session.SqlSession;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/login")
public class TestLoginServlet extends HttpServlet {
    private UserService userService=new UserServiceImpl();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("utf-8");
        resp.setContentType("text/html;charset=utf-8");
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer= resp.getWriter();
        SqlSession sqlSession=MybatisUtil.getSqlSession();
        JSONObject json = new JSONObject();
        String action=req.getParameter("action");
        try {
            if ("login".equals(action)) {
                String username = req.getParameter("username");
                String password = req.getParameter("password");
                User user = userService.doLogin(username, password);
                if (user != null) {
                    json.put("code", 200);
                    json.put("msg", "登陆成功");
                    json.put("user", user);
                } else {
                    json.put("code", 400);
                    json.put("msg", "登陆失败");
                }

            }else{
                json.put("code", 400);
                json.put("msg", "action错误");
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            MybatisUtil.closeAll(sqlSession);
        }
        writer.write(json.toJSONString());
        writer.flush();
        writer.close();

    }
}
