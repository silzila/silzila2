package com.silzila.querybuilder.syncFilterOption;


import com.silzila.dto.DatasetDTO;

import com.silzila.exception.BadRequestException;
import com.silzila.payload.internals.QueryClauseFieldListMap;
import com.silzila.payload.request.*;
import com.silzila.querybuilder.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class SyncFilterQuery {



    public static String getSyncFilterOptions(List<Filter> cf, String fromQuery, String vendorName,DatasetDTO ds) throws BadRequestException {
        QueryClauseFieldListMap selectQuery=null;
        boolean userSelection=false;
        String whereClause=null;
        int countCurrentSelection=0;
        boolean isUserSelectionAll=false;


        try {
            // Check for null or empty filters
            if (cf == null || cf.isEmpty()) {
                throw new IllegalArgumentException("Column filters cannot be null or empty.");
            }

            // Initialize variables
            StringBuilder finalQuery = new StringBuilder("SELECT DISTINCT ");
            List<String> selectedColumns = new ArrayList<>();
            List<Filter> userSelcetionFilter = new ArrayList<>();
            FilterPanel panel = new FilterPanel();
            List<Dimension> dimensions = new ArrayList<>();

            // Alias map for columns
            Map<String, Integer> aliasMap = new HashMap<>();
            int aliasCount = 1;

            // Process each ColumnFilter and create filter for it
            for (Filter filter : cf) {
                if (filter == null || filter.getFieldName() == null || filter.getFieldName().isEmpty()) {
                    throw new IllegalArgumentException("Invalid ColumnFilter: fieldName cannot be null or empty.");
                }

                // Add the column to the SELECT clause if currentSelection is false
                if (!filter.getCurrentSelection() ) {
                    if (filter.getOperator() == Filter.Operator.IN) {

                        if(filter.getUserSelection()!=null&&!filter.getUserSelection().get(0).equalsIgnoreCase("all")){
                            String columnName = filter.getTableId() + ".\"" + filter.getFieldName() + "\"";
                            selectedColumns.add(columnName);
                            aliasMap.put(columnName, aliasCount++);
                            isUserSelectionAll=true;

                        }

                        // Create and add the Dimension object
                        Dimension dimension = new Dimension(
                                filter.getIsCalculatedField(), filter.getCalculatedField(), filter.getTableId(),
                                                                filter.getFieldName(),
                                                                Dimension.DataType.fromValue(filter.getDataType().toString()),
                                                                Dimension.TimeGrain.fromValue(filter.getTimeGrain().toString()),
                                                                false, aliasCount);
                        dimensions.add(dimension);
                    }
                }else{
                    if(filter.getUserSelection()==null && filter.getOperator() == Filter.Operator.IN){
                        String columnName = filter.getTableId() + ".\"" + filter.getFieldName() + "\"";
                            selectedColumns.add(columnName);

                            Dimension dimension = new Dimension(
                                filter.getIsCalculatedField(), filter.getCalculatedField(), filter.getTableId(),
                                                                filter.getFieldName(),
                                                                Dimension.DataType.fromValue(filter.getDataType().toString()),
                                                                Dimension.TimeGrain.fromValue(filter.getTimeGrain().toString()),
                                                                false, aliasCount);
                        dimensions.add(dimension);
                    }
                    countCurrentSelection++;
                }

                // User selections
                List<String> userSelections = filter.getUserSelection();
                if (userSelections != null || "tillDate".equals(filter.getFilterType())) {
                    userSelcetionFilter.add(filter);
                    userSelection = true;

                }
            
            }

            if (dimensions.isEmpty()) {
                return null;
            }
            if (countCurrentSelection != 1) {
                throw new BadRequestException("Error: Only one column can be current Selection!");
            }
            // Create Query object
            Query query = new Query();
            query.setFilterPanels(null);
            query.setDimensions(dimensions);
            query.setMeasures(new ArrayList<>());
            query.setFields(null);
            if (vendorName.equals("postgresql") || vendorName.equals("redshift")) {

               selectQuery = SelectClausePostgres.buildSelectClause(query, vendorName,ds, aliasMap);
            } else if (vendorName.equals("mysql")) {
                selectQuery = SelectClauseMysql.buildSelectClause(query, vendorName,ds, aliasMap);
            } else if (vendorName.equals("sqlserver")) {
                selectQuery = SelectClauseSqlserver.buildSelectClause(query, vendorName,ds, aliasMap);
            } else if (vendorName.equals("databricks")) {
                selectQuery = SelectClauseDatabricks.buildSelectClause(query, vendorName,ds, aliasMap);

            } else if (vendorName.equals("duckdb")) {
                selectQuery = SelectClauseMotherduck.buildSelectClause(query, vendorName,ds, aliasMap);

            } else if (vendorName.equals("bigquery")) {
                selectQuery = SelectClauseBigquery.buildSelectClause(query, vendorName,ds, aliasMap);
            } else if (vendorName.equals("oracle")) {
                selectQuery = SelectClauseOracle.buildSelectClause(query, vendorName,ds, aliasMap);
            } else if (vendorName.equals("snowflake")) {
                selectQuery = SelectClauseSnowflake.buildSelectClause(query, vendorName,ds, aliasMap);

            } else if (vendorName.equals("motherduck")) {
                selectQuery = SelectClauseMotherduck.buildSelectClause(query, vendorName,ds, aliasMap);

            } else if (vendorName.equals("db2")) {
                selectQuery = SelectClauseDB2.buildSelectClause(query, vendorName,ds, aliasMap);

            } else if (vendorName.equals("teradata")) {
                selectQuery = SelectClauseTeraData.buildSelectClause(query, vendorName,ds, aliasMap);

            } else {
                throw new BadRequestException("Error: DB vendor Name is wrong!");
            }


            // Append selected columns to the final query
            finalQuery.append(String.join(", ", selectQuery.getSelectList()));
            finalQuery.append(" FROM ").append(fromQuery);

            // Create FilterPanels from allFilters
            panel.setFilters(userSelcetionFilter);
            panel.setPanelName("panel_" + fromQuery);
            panel.setShouldAllConditionsMatch(true);

            // Build WHERE clause using the panel

            if(userSelection&&isUserSelectionAll){
            whereClause = WhereClause.buildWhereClause(Collections.singletonList(panel), vendorName,ds);
                finalQuery.append(whereClause);
            }
            System.out.println("Generated  Query: " + finalQuery.toString());

            return finalQuery.toString();
        } catch (IllegalArgumentException e) {
            System.err.println("IllegalArgumentException: " + e.getMessage());
            throw new BadRequestException("Invalid input provided. " + e.getMessage());
        } catch (BadRequestException e) {
            System.err.println("BadRequestException: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            throw new BadRequestException("An unexpected error occurred while building the query.");
        }
    }
}
