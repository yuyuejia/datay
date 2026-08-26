package com.data.datafusion.util;

import java.sql.*;
import java.util.Properties;

public class JDBCUtils {

    public static Connection getConnection(String userName, String password, String url, String className) throws Exception {
        //        String jarFilePath = "jar:file:/home/xxx/libs/mysql-connector-java-8.0.29.jar!/";
        //        URLClassLoader loader = new URLClassLoader(new URL[]{new URL(jarFilePath)}, null);
        Driver driver = (Driver) Class.forName(className).newInstance();
        Connection connection = null;

        Properties info = new Properties();
        info.put("user", userName);
        info.put("password", password);
        connection = driver.connect(url, info);
        return connection;
    }

    public static void closeAll(Connection connection, Statement statement, ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
