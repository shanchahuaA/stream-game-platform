package com.stream.service;

import com.github.pagehelper.PageInfo;
import com.stream.pojo.Games;

import java.util.List;

public interface GamesService {

    // 增
    int addGame(Games games);

    // 删
    int deleteGame(Integer id);

    // 改
    int updateGame(Games games);

    // 查：按 ID
    Games getGameById(Integer id);

    // 查：全部
    List<Games> getAllGames();

    // 查：分页
    PageInfo<Games> getGamesByPage(int pageNum, int pageSize);

    // 查：全部 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
    PageInfo<Games> getAllByTopSeller(int pageNum, int pageSize);

    // 查：全部 - 按 discount_rank 升序，rank=0 按 review_count 降序
    PageInfo<Games> getAllByDiscount(int pageNum, int pageSize);

    // 搜：按游戏名模糊查询 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
    PageInfo<Games> searchByGnameTopSeller(String gname, int pageNum, int pageSize);

    // 搜：按游戏名模糊查询 - 按 discount_rank 升序，rank=0 按 review_count 降序
    PageInfo<Games> searchByGnameDiscount(String gname, int pageNum, int pageSize);

    // 查：喜加一（限免）游戏分页查询
    PageInfo<Games> getGiveawayGames(int pageNum, int pageSize);
}
