package com.stream.mapper;

import com.stream.pojo.Games;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GamesMapper {

    // 增：插入游戏
    int insertGame(@Param("game") Games games);

    // 删：根据 ID 删除游戏
    int deleteById(@Param("id") Integer id);

    // 改：更新游戏信息
    int updateGame(@Param("game") Games games);

    // 查：根据 ID 查询单个游戏
    Games selectById(@Param("id") Integer id);

    // 查：查询所有游戏
    List<Games> selectAll();

    // 查：分页查询
    List<Games> selectByPage();

    // 查：查询全部 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
    List<Games> selectAllByTopSeller();

    // 查：查询全部 - 按 discount_rank 升序，rank=0 按 review_count 降序
    List<Games> selectAllByDiscount();

    // 搜：按游戏名模糊查询 - 按 top_seller_rank 升序，rank=0 按 review_count 降序
    List<Games> searchByGnameByTopSeller(@Param("gname") String gname);

    // 搜：按游戏名模糊查询 - 按 discount_rank 升序，rank=0 按 review_count 降序
    List<Games> searchByGnameByDiscount(@Param("gname") String gname);

    // 重置所有游戏的折扣状态与排名
    int resetAllDiscountRank();

    // 重置所有游戏的热销榜排名
    int resetAllTopSellerRank();

    // 删除所有 Epic 平台的游戏
    int deleteEpicGames();

    // 查：查询喜加一（限免）游戏 - 原价不为0且现价为0
    List<Games> selectGiveawayGames();
}
