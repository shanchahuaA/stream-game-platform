package com.stream.service;

import com.stream.pojo.UserFavorites;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserFavoritesService {
    //根据用户id来获取用户喜欢表
    List<UserFavorites> listMyFavoriteByUserId(
            @Param("userId")Integer userId
    );
    //插入喜爱表，添加喜欢，根据用户id和游戏id
    int insertFavorite(
            @Param("userId")Integer userId,
            @Param("gameId")Integer gameId
    );
    //删除，根据喜爱id（主键）
    int deleteFavoriteById(
            @Param("id")Integer id
    );
    //判断这个游戏是不是在这个用户的喜爱表中（是否添加这个游戏为喜爱）
    Integer countFavoriteByUserGame(
            @Param("userId")Integer userId,
            @Param("gameId")Integer gameId
    );

}
