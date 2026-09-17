package com.stream.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.stream.mapper.GamesMapper;
import com.stream.pojo.Games;
import com.stream.service.GamesService;
import com.stream.utils.MybatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDateTime;
import java.util.List;

public class GamesServiceImpl implements GamesService {

    private GamesMapper getGamesMapper() {
        SqlSession sqlSession = MybatisUtil.getSqlSession();
        return sqlSession.getMapper(GamesMapper.class);
    }

    @Override
    public int addGame(Games games) {
        LocalDateTime now=LocalDateTime.now();
        games.setLastSyncTime(now);
        return getGamesMapper().insertGame(games);
    }

    @Override
    public int deleteGame(Integer id) {
        return getGamesMapper().deleteById(id);
    }

    @Override
    public int updateGame(Games games) {
        return getGamesMapper().updateGame(games);
    }

    @Override
    public Games getGameById(Integer id) {
        return getGamesMapper().selectById(id);
    }

    @Override
    public List<Games> getAllGames() {
        return getGamesMapper().selectAll();
    }

    @Override
    public PageInfo<Games> getGamesByPage(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().selectByPage();
        return new PageInfo<>(games);
    }

    @Override
    public PageInfo<Games> getAllByTopSeller(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().selectAllByTopSeller();
        return new PageInfo<>(games);
    }

    @Override
    public PageInfo<Games> getAllByDiscount(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().selectAllByDiscount();
        return new PageInfo<>(games);
    }

    @Override
    public PageInfo<Games> searchByGnameTopSeller(String gname, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().searchByGnameByTopSeller(gname);
        return new PageInfo<>(games);
    }

    @Override
    public PageInfo<Games> searchByGnameDiscount(String gname, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().searchByGnameByDiscount(gname);
        return new PageInfo<>(games);
    }

    @Override
    public PageInfo<Games> getGiveawayGames(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Games> games = getGamesMapper().selectGiveawayGames();
        return new PageInfo<>(games);
    }
}
