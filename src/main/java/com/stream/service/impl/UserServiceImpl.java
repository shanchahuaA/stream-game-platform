package com.stream.service.impl;

import com.stream.mapper.UserMapper;
import com.stream.pojo.User;
import com.stream.service.UserService;
import com.stream.utils.MybatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

public class UserServiceImpl implements UserService {

    @Override
    public User doLogin(String username, String password) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getUserByNameAndPwd(username, password);
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public List<User> getUserAll(String sortType) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.getUserAll(sortType);
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public User selectUserById(Integer id) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.selectUserById(id);
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public List<User> selectUserByNick(String nickname) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            return userMapper.selectUserByNick(nickname);
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public int insertUser(String username, String password, String nickname) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int result = userMapper.insertUser(username, password, nickname);
            sqlSession.commit();
            return result;
        } catch (Exception e) {
            sqlSession.rollback();
            throw e;
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public int updateUser(Integer id, String username, String password, String nickname, String avatar, Integer status) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int result = userMapper.updateUser(id, username, password, nickname, avatar, status);
            sqlSession.commit();
            return result;
        } catch (Exception e) {
            sqlSession.rollback();
            throw e;
        } finally {
            sqlSession.close();
        }
    }

    @Override
    public int deleteUser(Integer id) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        try {
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            int result = userMapper.deleteUser(id);
            sqlSession.commit();
            return result;
        } catch (Exception e) {
            sqlSession.rollback();
            throw e;
        } finally {
            sqlSession.close();
        }
    }
}
