
package com.stream.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class UserFavorites {
    private Integer id;
    private Integer userId;
    private Integer gameId;
    private Date createdAt;
    //关联字段，对应mapper中的外键关系，用来连接game表，就是收藏表肯定要显示游戏表的东西（他收藏的游戏的详细信息，所以要设置一个games对象来储存）
    private Games games;
}
