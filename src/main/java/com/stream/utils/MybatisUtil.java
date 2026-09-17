package com.stream.utils;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class MybatisUtil {

    private static SqlSessionFactory sqlSessionFactory;
    private static RuntimeException initFailure;

    static {
        try (InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml")) {
            // 连接信息在这里解析：环境变量优先，其次本地 db.properties
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, DbConfig.load());
        } catch (IOException e) {
            initFailure = new IllegalStateException("加载 mybatis-config.xml 失败", e);
        } catch (RuntimeException e) {
            // 配置缺失之类的问题留到取连接时再抛，否则会被包装成 ExceptionInInitializerError，
            // 真正可操作的提示就看不见了
            initFailure = e;
        }
    }

    public static SqlSession getSqlSession() {
        if (sqlSessionFactory == null) {
            throw initFailure;
        }
        return sqlSessionFactory.openSession();
    }

    public static void closeAll(SqlSession sqlSession){
        sqlSession.commit();
        sqlSession.close();
    }
}