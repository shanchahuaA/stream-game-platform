package com.stream.mapper;

import com.stream.pojo.UserFavorites;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserFavoritesMapper {

    //根据用户id查询该用户所有收藏（附带游戏完整信息）
    List<UserFavorites> listMyFavoriteByUserId(
            @Param("userId") Integer userId
    );

   //添加收藏，需要userid和gameid，自增收藏id不需要
    int insertFavorite(
            @Param("userId") Integer userId,
            @Param("gameId") Integer gameId
    );

   //删除收藏根据收藏表的id
    int deleteFavoriteById(
            @Param("id") Integer id
    );

   //记录用户有多少收藏数，通过userid判断是哪个用户，判断用户是否收藏了游戏以及游戏数量
    Integer countFavoriteByUserGame(
            @Param("userId") Integer userId,
            @Param("gameId") Integer gameId
    );
}
