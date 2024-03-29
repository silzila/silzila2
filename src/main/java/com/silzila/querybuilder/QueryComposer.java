package com.silzila.querybuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.silzila.payload.internals.QueryClauseFieldListMap;
import com.silzila.dto.DatasetDTO;
import com.silzila.exception.BadRequestException;
import com.silzila.helper.AilasMaker;
import com.silzila.payload.request.Dimension;
import com.silzila.payload.request.Query;
import com.silzila.payload.request.Dimension.DataType;
import com.silzila.querybuilder.override.overrideCTE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class QueryComposer {

    private static final Logger logger = LogManager.getLogger(QueryComposer.class);

    private static Boolean queryCTE = false;

    /*
     * Builds query based on Dimensions and Measures of user selection.
     * Query building is split into many sections:
     * like Select clause, Join clause, Where clause,
     * Group By clause & Order By clause
     * Different dialects will have different syntaxes.
     */
    public String composeQuery(List<Query> queries, DatasetDTO ds, String vendorName) throws BadRequestException {

        QueryClauseFieldListMap qMap = new QueryClauseFieldListMap();
        String updatedQuery = "";
        String finalQuery = "";

        Query req = queries.get(0);

        System.out.println(queryCTE);

        if (queries.size() > 1) {
            queryCTE = true;
        }
        /*
         * builds JOIN Clause of SQL - same for all dialects
         */
        String fromClause = RelationshipClauseGeneric.buildRelationship(req, ds.getDataSchema(), vendorName);
        // System.out.println("from clause ================\n" + fromClause);
        /*
         * builds SELECT Clause of SQL
         * SELECT clause is the most varying of all clauses, different for each dialect
         * select_dim_list columns are used in group_by_dim_list & order_by_dim_list
         * except that
         * select_dim_list has column alias and group_by_dim_list & order_by_dim_list
         * don't have alias
         */
        /*
         * vendorName given in buildSelectClause for windowFunction to get datas from
         * specified database,
         * because SelectClauseWindowFunction class is given common for all databases
         */
        if (vendorName.equals("postgresql") || vendorName.equals("redshift")) {
            // System.out.println("------ inside postges block");
            qMap = SelectClausePostgres.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("mysql") || vendorName.equals("duckdb")) {
            // System.out.println("------ inside mysql block");
            qMap = SelectClauseMysql.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("sqlserver")) {
            // System.out.println("------ inside sql server block");
            qMap = SelectClauseSqlserver.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("bigquery")) {
            // System.out.println("------ inside Big Query block");
            qMap = SelectClauseBigquery.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("databricks")) {
            // System.out.println("------ inside databricks block");
            qMap = SelectClauseDatabricks.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("oracle")) {
            qMap = SelectClauseOracle.buildSelectClause(req, vendorName);
        } else if (vendorName.equals("snowflake")) {
            qMap = SelectClauseSnowflake.buildSelectClause(req, vendorName);
        } else {
            throw new BadRequestException("Error: DB vendor Name is wrong!");
        }

        String selectClause = "\n\t" + qMap.getSelectList().stream().collect(Collectors.joining(",\n\t"));
        // distinct in group by and order by as SQL Server will take only unique expr in
        // group by and order by. this prevents error when dropping
        // same col twice in dimension
        String groupByClause = "\n\t" + qMap.getGroupByList().stream().distinct().collect(Collectors.joining(",\n\t"));
        String orderByClause = "\n\t" + qMap.getOrderByList().stream().distinct().collect(Collectors.joining(",\n\t"));
        String whereClause = WhereClause.buildWhereClause(req.getFilterPanels(), vendorName);
        // for bigquery only
        if (vendorName.equals("bigquery")) {
            boolean isDateOrTimestamp = false;
            boolean isMonthOrDayOfWeek = false;
            // get each dimensions from api using for-each
            for (Dimension dim : req.getDimensions()) {
                // check whether dimension's datatype match ('date','timestamp')
                if (List.of("DATE", "TIMESTAMP").contains(dim.getDataType().name()) &&
                        (dim.getTimeGrain().name().equals("MONTH") || dim.getTimeGrain().name().equals("DAYOFWEEK"))) {
                    isDateOrTimestamp = true;
                    isMonthOrDayOfWeek = true;
                    break; // Exit the loop if any dimension meets the criteria
                }
            }
            /*
             * for window functions
             * _0 just to mention window function. if selectlist contains _0, goes inside
             * _* for mentioning measure field
             */
            if (qMap.getSelectList().stream().anyMatch(column -> column.contains("_0"))) {
                List<String> filteredlist = new ArrayList<>();
                List<String> filteredSelectList = new ArrayList<>();
                // if selectlist contains _0 it will filter window function only and then we
                // replace the _0 by ""
                String filteredWindowFunction = qMap.getSelectList().stream().filter(column -> column.contains("_0"))
                        .map(column -> column.replace("_0", "")).collect(Collectors.joining(",\n\t"));
                // filter all columns except window function and then replace _* by ""
                List<String> filteredSelect = qMap.getSelectList().stream().filter(column -> !column.contains("_0"))
                        .map(column -> column.replace("_*", "")).collect(Collectors.toList());
                String filteredSelectClause = filteredSelect.stream().collect(Collectors.joining(",\n\t"));
                if (isDateOrTimestamp && isMonthOrDayOfWeek) {
                    // filter all columns except sorting fields('__'), measure field('_*') & window
                    // function('_0')
                    filteredSelectList = qMap.getSelectList().stream().filter(
                            column -> (!column.contains("__") && !column.contains("_*") && !column.contains("_0")))
                            .collect(Collectors.toList());
                } else {
                    filteredSelectList = qMap.getSelectList().stream()
                            .filter(column -> (!column.contains("_*") && !column.contains("_0")))
                            .collect(Collectors.toList());
                }
                for (int i = 0; i < filteredSelectList.size(); i++) {
                    String regex = "\\bAS\\s+(\\w+)"; // using regex to get alias after 'AS'
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(filteredSelectList.get(i));
                    while (matcher.find()) {
                        String alias = matcher.group(1);
                        filteredlist.add(alias); // aliases add into filteredlist
                        logger.info("Alias: " + alias);
                    }
                }
                String filteredSelectClauseList = "\n\t" + filteredlist.stream().collect(Collectors.joining(",\n\t"));
                finalQuery = "SELECT " + filteredSelectClauseList + ",\n\t" + filteredWindowFunction + "\nFROM ("
                        + "\nSELECT " + filteredSelectClause + "\nFROM"
                        + fromClause + whereClause + "\nGROUP BY" + groupByClause
                        + "\n) AS Tbl\nORDER BY" + orderByClause;

            }
            // if time grain month or day of week
            else if (isDateOrTimestamp && isMonthOrDayOfWeek) {
                List<String> filteredlist = new ArrayList<>();
                List<String> filteredSelectList = qMap.getSelectList().stream().filter(column -> !column.contains("__"))
                        .collect(Collectors.toList()); // remove double underscore columns('__')

                logger.info(filteredSelectList);

                for (int i = 0; i < filteredSelectList.size(); i++) {
                    String regex = "\\bAS\\s+(\\w+)"; // using regex to get alias after 'AS'
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(filteredSelectList.get(i));
                    while (matcher.find()) {
                        String alias = matcher.group(1);
                        filteredlist.add(alias); // aliases add into filteredlist
                        logger.info("Alias: " + alias);
                    }
                }
                logger.info(filteredlist);

                String filteredSelectClause = "\n\t" + filteredlist.stream().collect(Collectors.joining(",\n\t")); // convert
                                                                                                                   // arrayList
                                                                                                                   // values
                                                                                                                   // into
                                                                                                                   // String
                logger.info(filteredSelectClause);

                finalQuery = "SELECT " + filteredSelectClause + "\nFROM (" + "\nSELECT " + selectClause + "\nFROM"
                        + fromClause + whereClause + "\nGROUP BY" + groupByClause
                        + "\nORDER BY"
                        + orderByClause + "\n) AS Tbl";
            } else if (!req.getDimensions().isEmpty()) {
                finalQuery = "SELECT " + selectClause + "\nFROM" + fromClause + whereClause + "\nGROUP BY"
                        + groupByClause
                        + "\nORDER BY"
                        + orderByClause;
            } else if (!req.getMeasures().isEmpty()) {
                finalQuery = "SELECT " + selectClause + "\nFROM" + fromClause + whereClause;
            }
        } else if (!req.getDimensions().isEmpty()) {
            finalQuery = "SELECT " + selectClause + "\nFROM" + fromClause + whereClause + "\nGROUP BY"
                    + groupByClause
                    + "\nORDER BY"
                    + orderByClause;
        } else if (!req.getMeasures().isEmpty()) {
            finalQuery = "SELECT " + selectClause + "\nFROM" + fromClause + whereClause;
        }

        if (queryCTE && !req.getDimensions().isEmpty()) {

            if (!vendorName.equals("bigquery")) {
                finalQuery = "SELECT " + selectClause + "\nFROM " + fromClause + whereClause + "\nGROUP BY "
                        + groupByClause;
            }
            
        }

        if (queries.size() == 1) {
            updatedQuery = finalQuery;
        } else if (queries.size() > 1) {
            System.out.println(vendorName);
            String baseQuery = composeQuery(Collections.singletonList(queries.get(0)), ds, vendorName);
            System.out.println(baseQuery);

            String overrideCTEQuery = "";

            Map<String, Integer> aliasNumbering = new HashMap<>();

            List<Dimension> baseDimensions = queries.get(0).getDimensions();
            // for CTE override dimension
            List<String> allOverrideCTE = new ArrayList<>();
            // for clause
            List<String> selectMeasure = new ArrayList<>();
            // for table for joining
            List<String> joinTableQuery = new ArrayList<>();

            int tblNum = 2;

            for (int i = 1; i < queries.size(); i++) {
                boolean isRollupDepthEncountered = false;
                Query reqCTE = queries.get(i);

                List<Dimension> leftOverDimension = new ArrayList<>();
                List<Dimension> overrideDimensions = new ArrayList<>();
                List<Dimension> commonDimensions = new ArrayList<>();
                for (Dimension dim : baseDimensions) {
                    // Create a new Dimension object with the same properties as dim
                    Dimension newDim = new Dimension(dim.getTableId(),dim.getFieldName() , dim.getDataType(), dim.getTimeGrain(),dim.isRollupDepth());
                    commonDimensions.add(newDim);
                }

                for (Dimension dim : reqCTE.getDimensions()) {
                    if (dim.isRollupDepth()) {
                        isRollupDepthEncountered = true;
                        overrideDimensions.add(dim);
                    } else if (isRollupDepthEncountered) {
                        leftOverDimension.add(dim);
                    } else {
                        overrideDimensions.add(0, dim);
                    }
                }

                for (Dimension dim : overrideDimensions) {
                    if (!commonDimensions.contains(dim)) {
                        leftOverDimension.add(0, dim);
                    }
                }
                commonDimensions.retainAll(overrideDimensions);

                List<Dimension> combinedDimensions = new ArrayList<>();
                for (Dimension dim : leftOverDimension) {
                    // Create a new Dimension object with the same properties as dim
                    Dimension newDim = new Dimension(dim.getTableId(), dim.getFieldName(), dim.getDataType(),
                            dim.getTimeGrain(), dim.isRollupDepth());
                    combinedDimensions.add(newDim);
                }

                int j = 0;
                for (Dimension dim : commonDimensions) {
                    combinedDimensions.add(j, dim);
                    j++;
                }
                List<String> joinValues = overrideCTE.joinValues(commonDimensions, baseDimensions);
                // override base CTE
                queryCTE = true;
                System.out.println(combinedDimensions);
                reqCTE.setDimensions(combinedDimensions);
                String baseCTEquery = composeQuery(Collections.singletonList(reqCTE), ds, vendorName);

                // override query
                String overrideQuery = ", tbl" + tblNum + " AS ( " + baseCTEquery + " )";

                // setting the datatype for date and timestamp
                for (Dimension dim : combinedDimensions) {
                    dim.setDataType(DataType.TEXT);
                }

                tblNum++; // increment

                // remove leftover last dimension
                if (!leftOverDimension.isEmpty()) {
                    
                    String CTEQuery = overrideCTE.overrideCTEq(tblNum, reqCTE, leftOverDimension, combinedDimensions,
                            baseDimensions,
                            vendorName);

                    overrideQuery += CTEQuery;
                }

                // CTE expression

                allOverrideCTE.add(overrideQuery);

                tblNum += leftOverDimension.size();

                String aliasCTE = AilasMaker.aliasing("OD" + reqCTE.getMeasures().get(0).getFieldName(),
                        aliasNumbering);
                selectMeasure
                        .add(",tbl" + (tblNum -1) + "." + reqCTE.getMeasures().get(0).getFieldName() + " AS \"" + aliasCTE + "\"");

                // join clause
                String join = overrideCTE.joinCTE((tblNum -1), commonDimensions, joinValues);
                joinTableQuery.add(join);

            }
            
            // override query builder
            overrideCTEQuery = " WITH tbl1 as (" + baseQuery + ") ";
            for (String s : allOverrideCTE) {
                overrideCTEQuery += s;
            }
            overrideCTEQuery += " SELECT tbl1.* ";
            for (String s : selectMeasure) {
                overrideCTEQuery += s;
            }
            overrideCTEQuery += " \nFROM tbl1 ";
            for (String s : joinTableQuery) {
                overrideCTEQuery += s;
            }

            if (!baseDimensions.contains(null)) {
                overrideCTEQuery += " \nORDER BY ";

                int index = 0;
                for (Dimension dim : baseDimensions) {
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    overrideCTEQuery += "tbl1." + alias;
                    if (index < baseDimensions.size() - 1) {
                        overrideCTEQuery += ",";
                    }
                    index++;
                }
            }

            

            updatedQuery = overrideCTEQuery;
        }
        queryCTE = false;
        return updatedQuery;
    }
}