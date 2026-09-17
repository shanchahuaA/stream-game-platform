package com.stream.service;

import com.stream.pojo.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserService {
   User doLogin(
           @Param("username")String username,
           @Param("password")String password);
   List<User>getUserAll(
           @Param("sortType") String sortType
   );
   User selectUserById(
           @Param("id") Integer id
   );
   List<User> selectUserByNick(
           @Param("nickname") String nickname
   );
   int insertUser(
           @Param("username") String username,
           @Param("password") String password,
           @Param("nickname") String nickname
   );
   int updateUser(
           @Param("id") Integer id,
           @Param("username") String username,
           @Param("password") String password,
           @Param("nickname") String nickname,
           @Param("avatar") String avatar,
           @Param("status") Integer status
   );
  int deleteUser(
          @Param("id") Integer id
  );

}

