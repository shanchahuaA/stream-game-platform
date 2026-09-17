package com.stream.service.impl;

import com.stream.mapper.UserFavoritesMapper;
import com.stream.pojo.UserFavorites;
import com.stream.service.UserFavoritesService;
import com.stream.utils.MybatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

public class UserFavoritesServiceImpl implements UserFavoritesService {
    @Override
    public List<UserFavorites>listMyFavoriteByUserId (Integer userId) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        UserFavoritesMapper userFavoritesMapper=sqlSession.getMapper(UserFavoritesMapper.class);
        List<UserFavorites> userFavoritesList=userFavoritesMapper.listMyFavoriteByUserId(userId);
        sqlSession.close();
        return userFavoritesList;
    }

    @Override
    public int insertFavorite(Integer userId, Integer gameId) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        UserFavoritesMapper userFavoritesMapper=sqlSession.getMapper(UserFavoritesMapper.class);
        int rs=userFavoritesMapper.insertFavorite(userId,gameId);
        sqlSession.commit();
        sqlSession.close();
        return rs;
    }

    @Override
    public int deleteFavoriteById(Integer id) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        UserFavoritesMapper userFavoritesMapper=sqlSession.getMapper(UserFavoritesMapper.class);
        int rs=userFavoritesMapper.deleteFavoriteById(id);
        sqlSession.commit();
        sqlSession.close();
        return rs;
    }

    @Override
    public Integer countFavoriteByUserGame(Integer userId, Integer gameId) {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        UserFavoritesMapper userFavoritesMapper=sqlSession.getMapper(UserFavoritesMapper.class);
        int rs=userFavoritesMapper.countFavoriteByUserGame(userId,gameId);
        sqlSession.commit();
        sqlSession.close();
        return rs;
    }
}
