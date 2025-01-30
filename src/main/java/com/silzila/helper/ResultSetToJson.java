package com.silzila.helper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ResultSetToJson {

    // for converting a resultset from DB
    public static JSONArray convertToJson(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int numCols = metaData.getColumnCount();
        List<String> colNames = IntStream.range(0, numCols)
                .mapToObj(i -> {
                    try {
                        return metaData.getColumnName(i + 1);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return "?";
                    }
                })
                .collect(Collectors.toList());

        JSONArray result = new JSONArray();
        while (resultSet.next()) {
            JSONObject row = new JSONObject();
            colNames.forEach(cn -> {
                try {
                    Object cnValue = resultSet.getObject(cn);
                    if(cnValue== null){ 
                        row.put(cn,JSONObject.NULL);
                    }
                    else{
                        row.put(cn, cnValue);
                    }
                } catch (JSONException | SQLException e) {
                    e.printStackTrace();
                }
            });
            result.put(row);
        }
        return result;
    }

     public static JSONObject convertToArray(ResultSet resultSet) throws SQLException {
        JSONObject result = new JSONObject();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int numCols = metaData.getColumnCount();

        // Map to hold Sets for each column to ensure unique values
        Map<String, Set<Object>> columnSets = new HashMap<>();
        // Initialize sets for each column
        for (int i = 1; i <= numCols; i++) {
            String columnName = metaData.getColumnName(i);
            columnSets.put(columnName, new HashSet<>());
        }
        try {
            while (resultSet.next()) {
                for (int i = 1; i <= numCols; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);

                    // Add the value to the corresponding column's set for uniqueness
                    if (value == null) {
                        columnSets.get(columnName).add(JSONObject.NULL);
                    } else {
                        columnSets.get(columnName).add(value);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Convert sets back to JSONArray and add to JSONObject
        for (int i = 1; i <= numCols; i++) {
            String columnName = metaData.getColumnName(i);
            JSONArray jsonArray = new JSONArray(columnSets.get(columnName));
            result.put(columnName, jsonArray);
        }

        System.out.println(result);
        return result;
    }
   
   

    // for file uploads,preview schema changes 
    public static JSONArray convertToJsonFlatFiles(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int numCols = metaData.getColumnCount();
        List<String> colNames = IntStream.range(0, numCols)
                .mapToObj(i -> {
                    try {
                        return metaData.getColumnName(i + 1);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return "?";
                    }
                })
                .collect(Collectors.toList());

        JSONArray result = new JSONArray();
        while (resultSet.next()) {
            JSONObject row = new JSONObject();
            colNames.forEach(cn -> {
                try {
                    Object cnValue = resultSet.getObject(cn);
                    row.put(cn, cnValue);
                } catch (JSONException | SQLException e) {
                    e.printStackTrace();
                }
            });
            result.put(row);
        }
        return result;
    }
}

