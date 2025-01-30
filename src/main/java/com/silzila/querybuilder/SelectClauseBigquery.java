package com.silzila.querybuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.silzila.payload.internals.QueryClauseFieldListMap;
import com.silzila.exception.BadRequestException;
import com.silzila.helper.AilasMaker;
import com.silzila.payload.request.Dimension;
import com.silzila.payload.request.Measure;
import com.silzila.payload.request.Query;


public class SelectClauseBigquery {
    private static final Logger logger = LogManager.getLogger(SelectClauseBigquery.class);

    /* SELECT clause for MySQL dialect */
    public static QueryClauseFieldListMap buildSelectClause(Query req, String vendorName, Map<String,Integer>... aliasnumber) throws BadRequestException {
        logger.info("SelectClauseBigquery calling ***********");

        Map<String, Integer> aliasNumbering = new HashMap<>();
         // aliasing for only measure  override 
         Map<String,Integer> aliasNumberingM = new HashMap<>();

         if (aliasnumber != null && aliasnumber.length > 0) {
             Map<String, Integer> aliasNumber = aliasnumber[0];
             aliasNumber.forEach((key, value) -> aliasNumberingM.put(key, value));
         }  
        List<String> selectList = new ArrayList<>();
        List<String> selectDimList = new ArrayList<>();
        List<String> selectMeasureList = new ArrayList<>();
        List<String> groupByDimList = new ArrayList<>();
        List<String> orderByDimList = new ArrayList<>();
        List<String> windowFnList = new ArrayList<>();

        Map<String, String> timeGrainMap = Map.of("YEAR", "YEAR", "QUARTER", "QUARTER",
                "MONTH", "MONTH", "DATE", "DATE", "DAYOFWEEK", "DAYOFWEEK", "DAYOFMONTH", "DAY");

        /*
         * --------------------------------------------------------
         * ---------------- Iterate List of Dim Fields ------------
         * --------------------------------------------------------
         * Dim fields are added in Group by and Order by clause. Some Dims (like month)
         * require extra index for sorting.
         * 
         * So, Group by and Order by clause are added at column level and Select clause
         * is added at the end of Dim/Measure
         */
        for (int i = 0; i < req.getDimensions().size(); i++) {
            Dimension dim = req.getDimensions().get(i);
            // If the base dimension goes up to order_date_2 and the measure is order_date, it should be order_date_3.
            // If the overridden dimension includes additional order_date values, we want to keep the measure as order_date_3.
            if(aliasnumber != null && aliasnumber.length > 0){
                
                for(String key : aliasNumberingM.keySet()){

                    for(String key1 : aliasNumbering.keySet()){
                        if (aliasNumbering.containsKey(key) && aliasNumberingM.containsKey(key1)) {
                            if (key.equals(req.getMeasures().get(0).getFieldName())
                                    && key.equals(key1)
                                    && aliasNumbering.get(key).equals(aliasNumberingM.get(key1))) {
                                aliasNumbering.put(key, aliasNumbering.get(key) + 1);
                            }
                        } else {
                            // Handle the case where keys are not present in the maps
                            System.out.println("One of the keys is missing in the maps: " + key + ", " + key1);
                        }
                }
                }
               
            }
            String field = "";

            // for non Date fields, Keep column as is
            if (List.of("TEXT", "BOOLEAN", "INTEGER", "DECIMAL").contains(dim.getDataType().name())) {
                field = dim.getTableId() + "." + dim.getFieldName();
                String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                selectDimList.add(field + " AS " + alias);
                groupByDimList.add(alias);
                orderByDimList.add(alias);
            }
            // for date fields, need to Parse as year, month, etc.. to aggreate
            else if (List.of("DATE", "TIMESTAMP").contains(dim.getDataType().name())) {
                //if time grain is null then assign default value 'year'
                // if (dim.getTimeGrain() == null || dim.getTimeGrain().isBlank()) {
                // dim.setTimeGrain("year");
                // }

                // checking ('year', 'quarter', 'month', 'yearmonth', 'yearquarter',
                // 'dayofweek', 'date', 'dayofmonth')
                // year -> 2015
                if (dim.getTimeGrain().name().equals("YEAR")) {
                    field = "EXTRACT(YEAR FROM " + dim.getTableId() + "." + dim.getFieldName() + ")";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                }
                // quarter name -> Q3
                else if (dim.getTimeGrain().name().equals("QUARTER")) {
                    field = "CONCAT('Q', EXTRACT(QUARTER FROM " + dim.getTableId() + "." + dim.getFieldName() + "))";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                }
                // month name -> August
                // for month, need to give month number also for column sorting
                // which should be available in group by list but not in select list
                else if (dim.getTimeGrain().name().equals("MONTH")) {
                    String sortingFfield = "EXTRACT(MONTH FROM " + dim.getTableId() + "." + dim.getFieldName() + ")";
                    field = "FORMAT_DATE('%B', DATE(" + dim.getTableId() + "." + dim.getFieldName() + "))";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(sortingFfield + " AS __" + alias);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add("__" + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add("__" + alias);
                }
                // yearquarter name -> 2015-Q3
                else if (dim.getTimeGrain().name().equals("YEARQUARTER")) {
                    field = "CONCAT(EXTRACT(YEAR FROM " + dim.getTableId() + "." + dim.getFieldName()
                            + "), '-Q', EXTRACT(QUARTER FROM " + dim.getTableId() + "." + dim.getFieldName() + "))";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                }
                // yearmonth name -> 2015-08
                else if (dim.getTimeGrain().name().equals("YEARMONTH")) {
                    field = "FORMAT_DATE('%Y-%m', DATE(" + dim.getTableId() + "." + dim.getFieldName() + "))";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                }
                // date -> 2022-08-31
                else if (dim.getTimeGrain().name().equals("DATE")) {
                    field = "DATE(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                }
                // day Name -> Wednesday
                // for day of week, also give day of week number for column sorting
                // which should be available in group by list but not in select list
                else if (dim.getTimeGrain().name().equals("DAYOFWEEK")) {
                    String sortingFfield = "EXTRACT(DAYOFWEEK FROM " + dim.getTableId() + "." + dim.getFieldName() + ")";
                    field = "FORMAT_DATE('%A', DATE(" + dim.getTableId() + "." + dim.getFieldName() + "))";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(sortingFfield + " AS __" + alias);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add("__" + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add("__" + alias);
                }
                // day of month -> 31
                else if (dim.getTimeGrain().name().equals("DAYOFMONTH")) {
                    field = "EXTRACT(DAY FROM " + dim.getTableId() + "." + dim.getFieldName() + ")";
                    String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
                    selectDimList.add(field + " AS " + alias);
                    groupByDimList.add(alias);
                    orderByDimList.add(alias);
                } else {
                    throw new BadRequestException("Error: Dimension " + dim.getFieldName() +
                            " should have timegrain!");
                }
            }
        }
        ;

        /*
         * --------------------------------------------------------
         * ------------- Iterate List of Measure Fields -----------
         * --------------------------------------------------------
         */
        for (int i = 0; i < req.getMeasures().size(); i++) {
            Measure meas = req.getMeasures().get(i);

            //if aggr is null then throw error
            // if (meas.getAggr() == null || meas.getAggr().isBlank()) {
            // throw new BadRequestException(
            // "Error: Aggregation is not specified for measure " + meas.getFieldName());
            // }

            // if text field in measure then use
            // Text Aggregation Methods like COUNT
            // checking ('count', 'countnn', 'countn', 'countu')
            String field = "";
            String windowFn = "";
            if (List.of("TEXT", "BOOLEAN").contains(meas.getDataType().name())) {
                // checking ('count', 'countnn', 'countn', 'countu')
                if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTU")) {
                    field = "COUNT(DISTINCT " + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException(
                            "Error: Aggregation is not correct for measure " + meas.getFieldName());
                }
            }

            // for date fields, parse to year, month, etc.. and then
            // aggregate the field for Min & Max only
            else if (List.of("DATE", "TIMESTAMP").contains(meas.getDataType().name())) {

                // if date fields don't have time grain, then throw error
                if (Objects.isNull(meas.getTimeGrain())) {
                    throw new BadRequestException(
                            "Error: Date/Timestamp measure should have timeGrain");
                }

                List<String> aggrList = List.of("MIN", "MAX");
                List<String> timeGrainList = List.of("YEAR", "QUARTER", "MONTH",  "DAYOFMONTH", "DAYOFWEEK");
                // checking Aggregations: ('min', 'max', 'count', 'countnn', 'countn', 'countu')
                // checking Time Grains: ('year', 'quarter', 'month', 'yearmonth',
                // 'yearquarter', 'dayofmonth')

                if (aggrList.contains(meas.getAggr().name()) && timeGrainList.contains(meas.getTimeGrain().name())) {
                    field = meas.getAggr().name() + "(EXTRACT(" + timeGrainMap.get(meas.getTimeGrain().name())
                            + " FROM " + meas.getTableId() + "." + meas.getFieldName() + "))";
                }
                else if (aggrList.contains(meas.getAggr().name()) && meas.getTimeGrain().name().equals("DATE")) {
                    field = meas.getAggr().name() + "(DATE(" + meas.getTableId() + "." + meas.getFieldName() + "))";
                }
                /*
                 * countu is a special case & we can use time grain for this measure
                 */
                else if (meas.getAggr().name().equals("COUNTU") && timeGrainList.contains(meas.getTimeGrain().name())) {
                    field = "COUNT(DISTINCT(EXTRACT(" + timeGrainMap.get(meas.getTimeGrain().name()) + " FROM "
                             + meas.getTableId() + "." + meas.getFieldName() + ")))";
                } 
                // checking ('date')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("DATE")) {
                    field = "COUNT(DISTINCT(DATE(" + meas.getTableId() + "." + meas.getFieldName() + ")))";
                }

                // checking ('yearquarter')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("YEARQUARTER")) {
                    field = "COUNT(DISTINCT(CONCAT(EXTRACT(YEAR FROM " + meas.getTableId() + "." + meas.getFieldName()
                            + "), '-Q', EXTRACT(QUARTER FROM " + meas.getTableId() + "." + meas.getFieldName() + "))))";
                }
                // checking ('yearmonth')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("YEARMONTH")) {
                    field = "COUNT(DISTINCT(FORMAT_DATE('%Y-%m', DATE(" + meas.getTableId() + "." + meas.getFieldName()
                            + "))))";
                }

                /*
                 * for simple count & variants, time grain is not needed
                 */
                else if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException("Error: Measure " + meas.getFieldName() +
                            " should have timegrain!");
                }
            }
            // for number fields, do aggregation
            else if (List.of("INTEGER", "DECIMAL").contains(meas.getDataType().name())) {
                if (List.of("SUM", "AVG", "MIN", "MAX").contains(meas.getAggr().name())) {
                    field = meas.getAggr().name() + "(" + meas.getTableId() + "." + meas.getFieldName()
                            + ")";
                } else if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTU")) {
                    field = "COUNT(DISTINCT " + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException(
                            "Error: Aggregation is not correct for Numeric field " + meas.getFieldName());
                }
            }
            /*  if windowFn not null it will execute window function for bigquery
             * here we given field name as alias
             */
            if(meas.getWindowFn()[0] != null){
                String alias = AilasMaker.aliasing(meas.getFieldName(), aliasNumbering);
                windowFn = SelectClauseWindowFunction.windowFunction(meas, req, alias, vendorName);
                // if aliasnumber is not null, to maintain alias sequence for measure field
            if(aliasnumber != null && aliasnumber.length > 0){
                alias= AilasMaker.aliasing(meas.getFieldName(), aliasNumberingM);
                }
                selectMeasureList.add(field + " AS _*" + alias);
                selectMeasureList.add(windowFn + " AS _0" + alias);
            } else{
            String alias = AilasMaker.aliasing(meas.getFieldName(), aliasNumbering);
            // if aliasnumber is not null, to maintain alias sequence for measure field
            if(aliasnumber != null && aliasnumber.length > 0){
                alias= AilasMaker.aliasing(meas.getFieldName(), aliasNumberingM);
                }
            selectMeasureList.add(field + " AS " + alias);
            }
        }
        ;

        selectList.addAll(selectDimList);
        selectList.addAll(selectMeasureList);
        QueryClauseFieldListMap qFieldListMap = new QueryClauseFieldListMap(selectList, groupByDimList,
                orderByDimList);
        return qFieldListMap;
    }
}

