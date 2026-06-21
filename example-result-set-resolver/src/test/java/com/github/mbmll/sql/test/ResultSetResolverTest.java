package com.github.mbmll.sql.test;

import com.github.mbmll.sql.entity.User;
import com.github.mbmll.sql.resolver.ResultSetResolver;
import com.github.mbmll.sql.resolver.UserResultSetResolver;
import junit.framework.TestCase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author xlc
 * @Description
 * @Date 2026/6/20 17:12
 */

public class ResultSetResolverTest extends TestCase {

    public void testResolve() throws SQLException {
        ResultSetResolver<User> resolver = new UserResultSetResolver();
        // 创建一个原生的 jdbc 连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "123456");
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM user");
        User user = resolver.resolve(rs);
        System.out.println(user);
    }
}