package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.*;

public class JDBCExample {

    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
//        Class.forName("com.mysql.jdbc.Driver");
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3310/remote-config?useSSL=false&serverTimezone=UTC","root","123456");
//        conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/test_demo","root","password");
        PreparedStatement show_databases = conn.prepareStatement("select * from remote_condition_config");
        ResultSet resultSet = show_databases.executeQuery();
        print(resultSet);
        show_databases.close();
        conn.close();
    }

    public static void print(ResultSet resultSet) throws SQLException {
        Gson gson = new Gson();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        Map<Integer, String> meta = new HashMap<>();
        for (int i = 1; i < columnCount + 1; i++) {
            meta.put(i, metaData.getColumnLabel(i));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> line = new TreeMap<>();

            for (Integer id: meta.keySet()) {
                line.put(meta.get(id), resultSet.getObject(id));
            }
            result.add(line);
        }
        result.forEach(line -> {
            System.out.println(gson.toJson(line));
        });
//        System.out.println(gson.toJson(result));
    }
}
