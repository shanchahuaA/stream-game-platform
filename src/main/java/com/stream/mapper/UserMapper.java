package com.stream.mapper;

import com.stream.pojo.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {
    //登录用的，单个
    User getUserByNameAndPwd(
            @Param("username")String username,
            @Param("password")String password
    );
    //全部查找返回userlist
    List<User> getUserAll(
            @Param("sortType")String sortType
    );
    //根据id查找单个
    User selectUserById(
            @Param("id")Integer id
    );
    //根据nickname模糊查询
    List<User> selectUserByNick(
            @Param("nickname")String nickname
    );
    //username pwd nickname 必填
    int insertUser(
            @Param("username")String username,
            @Param("password")String password,
            @Param("nickname")String nickname
    );
    //动态sql update（管理员用，可以更新状态，可以通过修改status修改逻辑状态（禁用）？QWQ
    int updateUser(
            @Param("id")Integer id,
            @Param("username")String username,
            @Param("password")String password,
            @Param("nickname")String nickname,
            @Param("avatar")String avatar,
            @Param("status")Integer status
    );
    //删除用户（管理员用，物理删除）
    int deleteUser(
            @Param("id")Integer id
    );

}
