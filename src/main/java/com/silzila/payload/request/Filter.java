package com.silzila.payload.request;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;

import com.databricks.client.jdbc42.internal.apache.arrow.flatbuf.Bool;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "filterType",
        "tableId",
        "tableName",
        "fieldName",
        "uid",
        "dataType",
        "shouldExclude",
        "timeGrain",
        "operator",
        "userSelection"
})
@Generated("jsonschema2pojo")
public class Filter implements Serializable {

    @JsonProperty("filterType")
    private String filterType;
    @JsonProperty("tableId")
    private String tableId;
    @JsonProperty("tableName")
    private String tableName=null;
    @JsonProperty("fieldName")
    private String fieldName;
    @JsonProperty("dataType")
    private Filter.DataType dataType;
    @JsonProperty("uid")
    private String uid = null;
    @JsonProperty("shouldExclude")
    private Boolean shouldExclude = false;
    @JsonProperty("timeGrain")
    private Filter.TimeGrain timeGrain = Filter.TimeGrain.fromValue("year");
    @JsonProperty("operator")
    private Filter.Operator operator;
    @JsonProperty("userSelection")
    private List<String> userSelection = null;
    @JsonProperty("relativeCondition")
    private RelativeCondition relativeCondition = null;
    @JsonProperty("isTillDate")
    private Boolean isTillDate = false; 
    private final static long serialVersionUID = 4876626487235075859L;
    @JsonProperty("currentSelection")
    private Boolean currentSelection=false;

    /**
     * No args constructor for use in serialization
     *
     */
    public Filter() {
    }

    /**
     *
     * @param filterType
     * @param timeGrain
     * @param fieldName
     * @param dataType
     * @param shouldExclude
     * @param tableId
     * @param userSelection
     * @param operator
     * @param relativeCondition
     */
    public Filter(String tableId, String tableName, String fieldName, Filter.DataType dataType, String uid, Boolean shouldExclude,
            Filter.TimeGrain timeGrain, Filter.Operator operator, List<String> userSelection, String filterType,
            RelativeCondition relativeCondition, Boolean  isTillDate,Boolean currentSelection) {
        super();
        this.tableId = tableId;
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.dataType = dataType;
        this.uid = uid;
        this.shouldExclude = shouldExclude;
        this.timeGrain = timeGrain;
        this.operator = operator;
        this.userSelection = userSelection;
        this.filterType = filterType;
        this.relativeCondition = relativeCondition;
        this.isTillDate = isTillDate;
        this.currentSelection=currentSelection;
    }

    @JsonProperty("filterType")
    public String getFilterType() {
        return filterType;
    }

    @JsonProperty("filterType")
    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    @JsonProperty("tableId")
    public String getTableId() {
        return tableId;
    }

    @JsonProperty("tableId")
    public void setTableId(String tableId) {
        this.tableId = tableId;
    }
    
    public Boolean getCurrentSelection() {
        return currentSelection;
    }

    public void setCurrentSelection(Boolean currentSelection) {
        this.currentSelection = currentSelection;
    }

    @JsonProperty("tableName")
    public String getTableName() {
        return tableName;
    }

    @JsonProperty("tableName")
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @JsonProperty("fieldName")
    public String getFieldName() {
        return fieldName;
    }

    @JsonProperty("fieldName")
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    @JsonProperty("dataType")
    public Filter.DataType getDataType() {
        return dataType;
    }

    @JsonProperty("dataType")
    public void setDataType(Filter.DataType dataType) {
        this.dataType = dataType;
    }

    @JsonProperty("uid")
    public String getUid() {
        return uid;
    }

    @JsonProperty("uid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @JsonProperty("shouldExclude")
    public Boolean getShouldExclude() {
        return shouldExclude;
    }

    @JsonProperty("shouldExclude")
    public void setShouldExclude(Boolean shouldExclude) {
        this.shouldExclude = shouldExclude;
    }

    @JsonProperty("timeGrain")
    public Filter.TimeGrain getTimeGrain() {
        return timeGrain;
    }

    @JsonProperty("timeGrain")
    public void setTimeGrain(Filter.TimeGrain timeGrain) {
        this.timeGrain = timeGrain;
    }

    @JsonProperty("operator")
    public Filter.Operator getOperator() {
        return operator;
    }

    @JsonProperty("operator")
    public void setOperator(Filter.Operator operator) {
        this.operator = operator;
    }

    @JsonProperty("userSelection")
    public List<String> getUserSelection() {
        return userSelection;
    }

    @JsonProperty("userSelection")
    public void setUserSelection(List<String> userSelection) {
        this.userSelection = userSelection;
    }

    @JsonProperty("relativeCondition")
    public RelativeCondition getRelativeCondition() {
        return relativeCondition;
    }

    @JsonProperty("relativeCondition")
    public void setRelativeCondition(RelativeCondition relativeCondition) {
        this.relativeCondition = relativeCondition;
    }

    public Boolean getIsTillDate() {
        return isTillDate;
    }

    public void setIsTillDate(Boolean isTillDate) {
        this.isTillDate = isTillDate;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Filter.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("filterType");
        sb.append('=');
        sb.append(((this.filterType == null) ? "<null>" : this.filterType));
        sb.append(',');
        sb.append("tableId");
        sb.append('=');
        sb.append(((this.tableId == null) ? "<null>" : this.tableId));
        sb.append(',');
        sb.append("tableName");
        sb.append('=');
        sb.append(((this.tableName == null) ? "<null>" : this.tableName));
        sb.append(',');
        sb.append("fieldName");
        sb.append('=');
        sb.append(((this.fieldName == null) ? "<null>" : this.fieldName));
        sb.append(',');
        sb.append("dataType");
        sb.append('=');
        sb.append(((this.dataType == null) ? "<null>" : this.dataType));
        sb.append(',');
        sb.append("uid");
        sb.append('=');
        sb.append(((this.uid == null) ? "<null>" : this.uid));
        sb.append(',');
        sb.append("shouldExclude");
        sb.append('=');
        sb.append(((this.shouldExclude == null) ? "<null>" : this.shouldExclude));
        sb.append(',');
        sb.append("timeGrain");
        sb.append('=');
        sb.append(((this.timeGrain == null) ? "<null>" : this.timeGrain));
        sb.append(',');
        sb.append("operator");
        sb.append('=');
        sb.append(((this.operator == null) ? "<null>" : this.operator));
        sb.append(',');
        sb.append("userSelection");
        sb.append('=');
        sb.append(((this.userSelection == null) ? "<null>" : this.userSelection));
        sb.append(',');
        sb.append("relativeCondition");
        sb.append('=');
        sb.append(((this.relativeCondition == null) ? "<null>" : this.relativeCondition));
        sb.append(',');
        sb.append("isTillDate");
        sb.append('=');
        sb.append(((this.isTillDate == null) ? "<null>" : this.isTillDate));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Generated("jsonschema2pojo")
    public enum DataType {

        TEXT("text"),
        INTEGER("integer"),
        DECIMAL("decimal"),
        BOOLEAN("boolean"),
        DATE("date"),
        TIMESTAMP("timestamp");

        private final String value;
        private final static Map<String, Filter.DataType> CONSTANTS = new HashMap<String, Filter.DataType>();

        static {
            for (Filter.DataType c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        DataType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Filter.DataType fromValue(String value) {
            Filter.DataType constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    @Generated("jsonschema2pojo")
    public enum Operator {

        IN("in"),
        EQUAL_TO("equalTo"),
        NOT_EQUAL_TO("notEqualTo"),
        CONTAINS("contains"),
        BEGINS_WITH("beginsWith"),
        ENDS_WITH("endsWith"),
        BETWEEN("between"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_OR_EQUAL_TO("greaterThanOrEqualTo"),
        LESS_THAN("lessThan"),
        LESS_THAN_OR_EQUAL_TO("lessThanOrEqualTo"),
        EXACT_MATCH("exactMatch"),
        BLANK("blank");

        private final String value;
        private final static Map<String, Filter.Operator> CONSTANTS = new HashMap<String, Filter.Operator>();

        static {
            for (Filter.Operator c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Operator(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Filter.Operator fromValue(String value) {
            Filter.Operator constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    @Generated("jsonschema2pojo")
    public enum TimeGrain {

        YEAR("year"),
        QUARTER("quarter"),
        MONTH("month"),
        YEARQUARTER("yearquarter"),
        YEARMONTH("yearmonth"),
        DATE("date"),
        DAYOFMONTH("dayofmonth"),
        DAYOFWEEK("dayofweek");

        private final String value;
        private final static Map<String, Filter.TimeGrain> CONSTANTS = new HashMap<String, Filter.TimeGrain>();

        static {
            for (Filter.TimeGrain c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        TimeGrain(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Filter.TimeGrain fromValue(String value) {
            Filter.TimeGrain constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}