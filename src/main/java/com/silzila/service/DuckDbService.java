package com.silzila.service;

import java.io.IOException;
import java.util.*;

import javax.validation.Valid;

import java.nio.file.Files;
import java.nio.file.Paths;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silzila.dto.DatasetDTO;
import com.silzila.exception.BadRequestException;
import com.silzila.exception.RecordNotFoundException;
import com.silzila.payload.request.*;
import com.silzila.querybuilder.WhereClause;
import com.silzila.querybuilder.relativefilter.RelativeFilterQueryComposer;
import com.silzila.repository.DatasetRepository;
import com.silzila.repository.FileDataRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.duckdb.DuckDBConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.silzila.domain.entity.FileData;
import com.silzila.exception.ExpectationFailedException;
import com.silzila.helper.ConvertDuckDbDataType;
import com.silzila.helper.DuckDbMetadataToJson;
import com.silzila.helper.JsonValidator;
import com.silzila.helper.RelativeFilterProcessor;
import com.silzila.helper.ResultSetToJson;
import com.silzila.payload.response.FileUploadResponseDuckDb;

@Service
public class DuckDbService {

    @Autowired
    DatasetRepository datasetRepository;
    @Autowired
    FileDataRepository fileDataRepository;
    @Autowired
    DatasetAndFileDataBuffer buffer;
    @Autowired
    RelativeFilterQueryComposer relativeFilterQueryComposer;
    @Autowired
    RelativeFilterProcessor relativeFilterProcessor;

    @Value("${pepperForFlatFiles}")
    private String pepper;

    //generating random value to encrypt
    final String encryptPwd ="#VaNgaL#";


    ObjectMapper objectMapper = new ObjectMapper();

     // load dataset details in buffer. This helps faster query execution.
     public DatasetDTO loadDatasetInBuffer(String dbConnectionId,String datasetId,String workspaceId, String userId)
     throws RecordNotFoundException, JsonMappingException, JsonProcessingException, ClassNotFoundException, BadRequestException, SQLException {
        DatasetDTO dto = buffer.loadDatasetInBuffer(workspaceId,datasetId, userId);
         if(!dto.getDataSchema().getFilterPanels().isEmpty()){
             List<FilterPanel> filterPanels = relativeFilterProcessor.processFilterPanels(dto.getDataSchema().getFilterPanels(), userId, dbConnectionId, datasetId,workspaceId,this::relativeFilter);
             dto.getDataSchema().setFilterPanels(filterPanels);
         }
         return dto;
    }



    private static final Logger logger = LogManager.getLogger(DuckDbService.class);
    // holds view name of DFs used in query
    // contains user wise dataset wise list of tables
    public static HashMap<String, HashMap<String, ArrayList<String>>> views = new HashMap<String, HashMap<String, ArrayList<String>>>();

    // Home folder for saved flat files
    final String SILZILA_DIR = System.getProperty("user.home") + "/" + "silzila-uploads";

    Connection conn = null;

    // helper function to initialize InMemory Duck DB
    public void startDuckDb() throws ClassNotFoundException, SQLException {
        if (Objects.isNull(conn)) {
            Class.forName("org.duckdb.DuckDBDriver");
            // String connectionUrl = "jdbc:duckdb:" + SILZILA_DIR + "/silzila.db";
            conn = DriverManager.getConnection("jdbc:duckdb:");
        }
    }

    // read csv file and get metadata
    public FileUploadResponseDuckDb readCsv(String fileName) throws SQLException {
        // String filePath = SILZILA_DIR + "/" + fileName;
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;
        Connection conn2 = ((DuckDBConnection) conn).duplicate();

        Statement stmtRecords = conn2.createStatement();
        Statement stmtMeta = conn2.createStatement();
        Statement stmtDeleteTbl = conn2.createStatement();

        String query = "CREATE OR REPLACE TABLE tbl_" + fileName + " AS SELECT * from read_csv_auto('" + filePath
                + "',NORMALIZE_NAMES=True,SAMPLE_SIZE=200)";
        stmtRecords.execute(query);
        ResultSet rsRecords = stmtRecords.executeQuery("SELECT * FROM tbl_" + fileName + " LIMIT 200");
        ResultSet rsMeta = stmtMeta.executeQuery("DESCRIBE tbl_" + fileName);
        
        JSONArray jsonArrayRecords = ResultSetToJson.convertToJsonFlatFiles(rsRecords);
        // keep only column name & data type
        JSONArray jsonArrayMeta = DuckDbMetadataToJson.convertToJson(rsMeta);

        List<Map<String, Object>> recordList = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> metaList = new ArrayList<Map<String, Object>>();

        // Sample Records
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayRecords != null) {
            for (int i = 0; i < jsonArrayRecords.length(); i++) {
                JSONObject rec = jsonArrayRecords.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    rowObj.put(keyStr, keyValue);
                });
                recordList.add(rowObj);
            }
        }
        // Meta data - column name & data type
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayMeta != null) {
            for (int i = 0; i < jsonArrayMeta.length(); i++) {
                JSONObject rec = jsonArrayMeta.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    // DuckDB Data type -> Silzila Data Type
                    if (keyStr.equals("dataType")) {
                        String silzilaDataType = ConvertDuckDbDataType.toSilzilaDataType(keyValue.toString());
                        rowObj.put(keyStr, silzilaDataType);
                    } else {
                        rowObj.put(keyStr, keyValue);
                    }
                });
                metaList.add(rowObj);
            }
        }
        // System.out.println(jsonArrayRecords.toString());
        // System.out.println(jsonArrayMeta.toString());
        // delete the in-memory table as it's not required
        String deleteQuery = "DROP TABLE IF EXISTS tbl_" + fileName;
        stmtDeleteTbl.execute(deleteQuery);
        stmtRecords.close();
        stmtMeta.close();
        stmtDeleteTbl.close();
        conn2.close();

        FileUploadResponseDuckDb fileUploadResponseDuckDb = new FileUploadResponseDuckDb(null, fileName, metaList,
                recordList);
        return fileUploadResponseDuckDb;
    }

    // edit schema of already uploaded file
    public JSONArray readCsvChangeSchema(FileUploadRevisedInfoRequest revisedInfoRequest)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();

        // String filePath = SILZILA_DIR + "/" + fileName;
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;
        Connection conn2 = ((DuckDBConnection) conn).duplicate();

        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

      

        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
          }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provides custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provides custom timestamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }

        String query = "SELECT * from read_csv_auto('" + filePath + "', SAMPLE_SIZE=200,names=" + colMapString
                + ", types=" + dataTypeMapString + dateFormatCondition + timeStampFormatCondition + ");";

        logger.info("************************\n" + query);

        // handling the unmatched data type error
        ResultSet resultSet = null;
        try {
            resultSet = stmtRecords.executeQuery(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error: " + e.getMessage());
        }
        JSONArray jsonArray = ResultSetToJson.convertToJsonFlatFiles(resultSet);
        stmtRecords.close();
        conn2.close();

        return jsonArray;

    }

    // creating a map from column and dtype list to send as a columns parameter to
    // read_csv_auto query
    public static Map<String, String> convertToMap(ArrayList<String> keys, ArrayList<String> values) {
        if (keys.size() != values.size()) {
            throw new IllegalArgumentException("ArrayLists must have the same length");
        }

        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            map.put(keys.get(i), values.get(i));
        }
        return map;
    }

    // converting a map to string to send to column parameter
    public static String mapToString(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
        }
        if (!map.isEmpty()) {
            // Remove the trailing comma and space
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("}");
        return sb.toString();
    }

    // save CSV to Parquet

    public void writeCsvToParquet(FileUploadRevisedInfoRequest revisedInfoRequest, String userId, String encryptVal)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);

            // Replace spaces and special characters with underscores
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
        }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provides custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provides custom timestamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }

        System.out.println(encryptVal);
        //creating Encryption key to save parquet file securely
        String encryptKey= "PRAGMA add_parquet_key('key256', '"+encryptVal+"')";
        // read CSV and write as Parquet file
        final String writeFile = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + userId + "/" +"/"
                + revisedInfoRequest.getFileId() + ".parquet";
        String query = "COPY (SELECT * from read_csv_auto('" + filePath + "', names=" + colMapString + ", types="
                + dataTypeMapString + dateFormatCondition + timeStampFormatCondition + ")) TO '" + writeFile
                + "' (ENCRYPTION_CONFIG {footer_key: 'key256'});";

        stmtRecords.execute(encryptKey);

        logger.info("************************\n" + query);

        // handling the data type mismatch
        try {
            stmtRecords.execute(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error:" + e.getMessage());
        }
        stmtRecords.close();
        conn2.close();
    }

    // get sample records from Parquet file
    public JSONArray getSampleRecords(String workspaceId,String parquetFilePath,String userId,String datasetId, String tableName,String encryptVal) throws SQLException, RecordNotFoundException, JsonProcessingException, BadRequestException, ClassNotFoundException {

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = conn2.createStatement();

        String query = "";

        if(datasetId!=null) {
            //getting dataset information to fetch filter panel information
            DatasetDTO ds = loadDatasetInBuffer(null,datasetId,workspaceId, userId);
            List<FilterPanel> filterPanels = new ArrayList<>();
            String tableId = "";
            String whereClause = "";

            //iterating to filter panel list to get the particular filter panel for the table
            for (int i = 0; i < ds.getDataSchema().getFilterPanels().size(); i++) {
                if (ds.getDataSchema().getFilterPanels().get(i).getFilters().get(0).getTableName().equalsIgnoreCase(tableName)) {
                    filterPanels.add(ds.getDataSchema().getFilterPanels().get(i));
                    tableId = ds.getDataSchema().getFilterPanels().get(i).getFilters().get(0).getTableId();

                }

            }

            //generating where clause from the given filter panel
            whereClause = WhereClause.buildWhereClause(filterPanels, "duckdb");

            //creating Encryption key to save parquet file securely
            String encryptKey = "PRAGMA add_parquet_key('key256', '" + encryptVal + "')";
            stmtRecords.execute(encryptKey);
            //checking whether the dataset has filter panel or not
            if (ds.getDataSchema().getFilterPanels().isEmpty()) {
                query = "SELECT * from read_parquet('" + parquetFilePath + "',encryption_config = {footer_key: 'key256'}) LIMIT 200;";
            } else {
                query = "SELECT * from read_parquet('" + parquetFilePath + "',encryption_config = {footer_key: 'key256'})" + " AS " + tableId + "\n" + whereClause + " LIMIT 200;";
            }
        }
        else {
            //creating Encryption key to save parquet file securely
            String encryptKey = "PRAGMA add_parquet_key('key256', '" + encryptVal + "')";
            stmtRecords.execute(encryptKey);
            query = "SELECT * from read_parquet('" + parquetFilePath + "',encryption_config = {footer_key: 'key256'}) LIMIT 200;";

        }
        logger.info("************************\n" + query);

        ResultSet resultSet = stmtRecords.executeQuery(query);
        JSONArray jsonArray = ResultSetToJson.convertToJson(resultSet);
        stmtRecords.close();
        conn2.close();

        return jsonArray;
    }

    // get sample records from Parquet file
    public List<Map<String, Object>> getColumnMetaData(String parquetFilePath,String encryptVal) throws SQLException {

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtMeta = conn2.createStatement();
        Statement stmtRecords = conn2.createStatement();


        //creating Encryption key to save parquet file securely
        String encryptKey= "PRAGMA add_parquet_key('key256', '"+encryptVal+"')";
        stmtRecords.execute(encryptKey);

        String query = "DESCRIBE SELECT * from read_parquet('" + parquetFilePath + "',encryption_config = {footer_key: 'key256'}) LIMIT 1;";
        logger.info("************************\n" + query);

        ResultSet rsMeta = stmtMeta.executeQuery(query);
        // keep only column name & data type
        JSONArray jsonArrayMeta = DuckDbMetadataToJson.convertToJson(rsMeta);
        List<Map<String, Object>> metaList = new ArrayList<Map<String, Object>>();

        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayMeta != null) {
            for (int i = 0; i < jsonArrayMeta.length(); i++) {
                JSONObject rec = jsonArrayMeta.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    // DuckDB Data type -> Silzila Data Type
                    if (keyStr.equals("dataType")) {
                        String silzilaDataType = ConvertDuckDbDataType.toSilzilaDataType(keyValue.toString());
                        rowObj.put(keyStr, silzilaDataType);
                    } else {
                        rowObj.put(keyStr, keyValue);
                    }
                });
                metaList.add(rowObj);
            }
        }
        stmtMeta.close();
        conn2.close();
        return metaList;
    }

    // create DF for flat files
    public void createViewForFlatFiles(String userId, List<Table> tableObjList, List<FileData> fileDataList,String encryptVal)
            throws SQLException, ClassNotFoundException {
        // System.out.println("Table Obj ============\n" + tableObjList.toString());
        // System.out.println("File Data List ============\n" +
        // fileDataList.toString());
        // iterate file table list and create DF & SQL view if not already created
        for (int i = 0; i < tableObjList.size(); i++) {
            /*
             * check if the view name is already existing else create
             */
            String flatFileId = tableObjList.get(i).getFlatFileId();
            // create user
            if (!views.containsKey(userId)) {
                HashMap<String, ArrayList<String>> userHashMap = new HashMap<String, ArrayList<String>>();
                views.put(userId, userHashMap);
            }
            // create empty view list for the flatFileId for the user
            if (!views.get(userId).containsKey(flatFileId)) { // flatFileId
                ArrayList<String> viewList = new ArrayList<>();
                views.get(userId).put(flatFileId, viewList);
            }
            // add view name for the flatFileId
            String viewName = "vw_" + tableObjList.get(i).getAlias().replaceAll("[^a-zA-Z0-9_]", "_") + "_"
                    + flatFileId.substring(0, 8);
            if (!views.get(userId).get(flatFileId).contains(viewName)) {
                // iterate flat file list and get flat file name corresponding to file id
                for (int j = 0; j < fileDataList.size(); j++) {
                    if (flatFileId.equals(fileDataList.get(j).getId())) {
                        final String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + userId
                                + "/" + fileDataList.get(j).getFileName();
                        String salt= fileDataList.get(j).getSaltValue();

                        // create view on DF and maintain the view name to know if view is already there
                        startDuckDb();
                        Connection conn2 = ((DuckDBConnection) conn).duplicate();
                        Statement stmt = conn2.createStatement();

                        //creating Encryption key to save parquet file securely
                        String encryptKey= "PRAGMA add_parquet_key('key256', '"+salt+encryptVal+"')";
                        stmt.execute(encryptKey);

                        String query = "CREATE OR REPLACE VIEW " + viewName + " AS (SELECT * FROM read_parquet('"+filePath+"', encryption_config = {footer_key: 'key256'}))";
                        logger.info("View creating query ==============\n" + query);
                        stmt.execute(query);
                        stmt.close();
                        conn2.close();
                        views.get(userId).get(flatFileId).add(viewName);
                        logger.info("Views list ================ \n" + views);
                    }
                }

            }

        }
    }

    // get sample records from Parquet file
    public JSONArray runQuery(String query) throws SQLException {

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = conn2.createStatement();

        ResultSet resultSet = stmtRecords.executeQuery(query);
        JSONArray jsonArray = ResultSetToJson.convertToJson(resultSet);
        stmtRecords.close();
        conn2.close();

        return jsonArray;
    }

    public FileUploadResponseDuckDb readExcel(String fileName, String sheetName)
            throws SQLException, ExpectationFailedException {

        if(sheetName.equalsIgnoreCase(""))
        {
            throw new ExpectationFailedException("Could not upload because SHEETNAME is NULL") ;
        }
        // String filePath = SILZILA_DIR + "/" + fileName;
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;

        Connection conn2 = ((DuckDBConnection) conn).duplicate();

        Statement stmtInstallLoad = conn2.createStatement();
        Statement stmtRecords = conn2.createStatement();
        Statement stmtMeta = conn2.createStatement();
        Statement stmtDeleteTbl = conn2.createStatement();
        Statement stmtCopyQuery = conn2.createStatement();

        // to install and load spatial since duckDB wouldn't support Excel without
        // extension
        stmtInstallLoad.execute("INSTALL spatial;");
        stmtInstallLoad.execute("LOAD spatial;");

        String query = "CREATE OR REPLACE TABLE tbl_" + fileName + " AS SELECT * from st_read('" + filePath
                + "',layer ='" + sheetName + "')";
        try {
            stmtRecords.execute(query);
        }
        catch (SQLException e)
        {
            if(e.getMessage().contains("not recognized as a supported file format")) {
                throw new ExpectationFailedException("Sorry!!! You are trying to upload unsupported file format ");
            } else if (e.getMessage().contains("Binder Error: Layer")) {
                throw new ExpectationFailedException("Please check the SheetName, '"+sheetName+"' is not exist" );
            }
        }

        ResultSet rsRecords = stmtRecords.executeQuery("SELECT * FROM tbl_" + fileName + " LIMIT 200");
        ResultSet rsMeta = stmtMeta.executeQuery("DESCRIBE tbl_" + fileName);

        JSONArray jsonArrayRecords = ResultSetToJson.convertToJsonFlatFiles(rsRecords);
        // keep only column name & data type
        JSONArray jsonArrayMeta = DuckDbMetadataToJson.convertToJson(rsMeta);

        List<Map<String, Object>> recordList = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> metaList = new ArrayList<Map<String, Object>>();

        // Sample Records
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayRecords != null) {
            for (int i = 0; i < jsonArrayRecords.length(); i++) {
                JSONObject rec = jsonArrayRecords.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    rowObj.put(keyStr, keyValue);
                });
                recordList.add(rowObj);
            }
        }
        // Meta data - column name & data type
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayMeta != null) {
            for (int i = 0; i < jsonArrayMeta.length(); i++) {
                JSONObject rec = jsonArrayMeta.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    // DuckDB Data type -> Silzila Data Type
                    if (keyStr.equals("dataType")) {
                        String silzilaDataType = ConvertDuckDbDataType.toSilzilaDataType(keyValue.toString());
                        rowObj.put(keyStr, silzilaDataType);
                    } else {
                        rowObj.put(keyStr, keyValue);
                    }
                });
                metaList.add(rowObj);
            }
        }
        // System.out.println(jsonArrayRecords.toString());
        // System.out.println(jsonArrayMeta.toString());
        // delete the in-memory table as it's not required

        // copying excel file to csv for further operation
        String copyQuery = "COPY (SELECT * FROM tbl_" + fileName + ") TO '" + filePath
                + ".csv' (FORMAT csv,HEADER, DELIMITER ',')";
        logger.info("************************\n" + copyQuery);
        stmtCopyQuery.execute(copyQuery);

        String deleteQuery = "DROP TABLE IF EXISTS tbl_" + fileName;
        stmtDeleteTbl.execute(deleteQuery);

        stmtRecords.close();
        stmtMeta.close();
        stmtDeleteTbl.close();
        conn2.close();

        FileUploadResponseDuckDb fileUploadResponseDuckDb = new FileUploadResponseDuckDb(null, fileName, metaList,
                recordList);

        // FileUploadResponseDuckDb a= changeSchemaForExcel(FileUploadRevisedInfoRequest
        // revisedInfoRequest ,FileUploadResponseDuckDb fileUploadResponseDuckDb);

        return fileUploadResponseDuckDb;
    }

    public void writeExcelToParquet(FileUploadRevisedInfoRequest revisedInfoRequest, String userId,String encryptVal)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);

            // Replace spaces and special characters with underscores
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
        }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provides custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provides custom timestamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }

        //creating Encryption key to save parquet file securely
        String encryptKey= "PRAGMA add_parquet_key('key256', '"+encryptVal+"')";
        stmtRecords.execute(encryptKey);

               // read CSV and write as Parquet file
        final String writeFile = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + userId + "/" + "/"
                +  revisedInfoRequest.getFileId() + ".parquet";
        String query = "COPY (SELECT * from read_csv_auto('" + filePath + "', names=" + colMapString + ", types="
                + dataTypeMapString + dateFormatCondition + timeStampFormatCondition + ")) TO '" + writeFile
                + "' (ENCRYPTION_CONFIG {footer_key: 'key256'});";

        logger.info("************************\n" + query);
        try {
            stmtRecords.execute(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error:" + e.getMessage());
        }
        stmtRecords.close();
        conn2.close();
    }


    public FileUploadResponseDuckDb readJson(String fileName) throws SQLException, ExpectationFailedException, IOException {

        // String filePath = SILZILA_DIR + "/" + fileName;
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;
        String jsonStr = new String(Files.readAllBytes(Paths.get(filePath))).trim();
        Connection conn2 = ((DuckDBConnection) conn).duplicate();

         JsonValidator.validate(jsonStr);

        Statement stmtRecords = conn2.createStatement();
        Statement stmtMeta = conn2.createStatement();
        Statement stmtDeleteTbl = conn2.createStatement();
        // checking for correct format and do the operation on json
        try{
            String query = "CREATE OR REPLACE TABLE tbl_" + fileName + " AS SELECT * from read_json_auto('" + filePath
                    + "',SAMPLE_SIZE=200)";
            stmtRecords.execute(query);
        } catch (SQLException e) {
            if(e.getMessage().contains("Invalid Input Error: Malformed JSON")) {
                throw new ExpectationFailedException("Sorry!!! Invalid JSON format");
            }

        }

        ResultSet rsRecords = stmtRecords.executeQuery("SELECT * FROM tbl_" + fileName + " LIMIT 200");
        ResultSet rsMeta = stmtMeta.executeQuery("DESCRIBE tbl_" + fileName);

        JSONArray jsonArrayRecords = ResultSetToJson.convertToJsonFlatFiles(rsRecords);
        // keep only column name & data type
        JSONArray jsonArrayMeta = DuckDbMetadataToJson.convertToJson(rsMeta);

        List<Map<String, Object>> recordList = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> metaList = new ArrayList<Map<String, Object>>();

        // Sample Records
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayRecords != null) {
            for (int i = 0; i < jsonArrayRecords.length(); i++) {
                JSONObject rec = jsonArrayRecords.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    rowObj.put(keyStr, keyValue);
                });
                recordList.add(rowObj);
            }
        }
        // Meta data - column name & data type
        // convert JsonArray -> List of JsonObject -> List of Map(String, Object)
        if (jsonArrayMeta != null) {
            for (int i = 0; i < jsonArrayMeta.length(); i++) {
                JSONObject rec = jsonArrayMeta.getJSONObject(i);
                Map<String, Object> rowObj = new HashMap<String, Object>();
                rec.keySet().forEach(keyStr -> {
                    Object keyValue = rec.get(keyStr);
                    // DuckDB Data type -> Silzila Data Type
                    if (keyStr.equals("dataType")) {
                        String silzilaDataType = ConvertDuckDbDataType.toSilzilaDataType(keyValue.toString());
                        rowObj.put(keyStr, silzilaDataType);
                    } else {
                        rowObj.put(keyStr, keyValue);
                    }
                });
                metaList.add(rowObj);
            }
        }
        // System.out.println(jsonArrayRecords.toString());
        // System.out.println(jsonArrayMeta.toString());
        // delete the in-memory table as it's not required
        String deleteQuery = "DROP TABLE IF EXISTS tbl_" + fileName;
        stmtDeleteTbl.execute(deleteQuery);
        stmtRecords.close();
        stmtMeta.close();
        stmtDeleteTbl.close();
        conn2.close();

        FileUploadResponseDuckDb fileUploadResponseDuckDb = new FileUploadResponseDuckDb(null, fileName, metaList,
                recordList);

        return fileUploadResponseDuckDb;

    }

    // edit schema of already uploaded file
    public JSONArray readJsonChangeSchema(FileUploadRevisedInfoRequest revisedInfoRequest)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();

        // String filePath = SILZILA_DIR + "/" + fileName;
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;
        Connection conn2 = ((DuckDBConnection) conn).duplicate();

        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
        }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provieds custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provieds custom timestamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }
        // creating a map to send values to columns parameter
        Map<String, String> map = convertToMap(columnList, dataTypeList);
        // Converting a map to string to pass correct format to column
        String columnsMapString = mapToString(map);

        String query = "SELECT * from read_json_auto('" + filePath
                + "', SAMPLE_SIZE=200, ignore_errors=true, format='auto', columns=" + columnsMapString
                + dateFormatCondition + timeStampFormatCondition + ");";

        logger.info("************************\n" + query);
        ResultSet resultSet = null;
        // handling data type mismatch
        try {
            resultSet = stmtRecords.executeQuery(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error: " + e.getMessage());
        }
        JSONArray jsonArray = ResultSetToJson.convertToJsonFlatFiles(resultSet);
        stmtRecords.close();
        conn2.close();

        return jsonArray;
    }

    public void writeJsonToParquet(FileUploadRevisedInfoRequest revisedInfoRequest, String userId,String encryptVal)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();
        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;

        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);

            // Replace spaces and special characters with underscores
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
        }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provides custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provides custom timestamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }

        // creating a map to send values to columns parameter
        Map<String, String> map = convertToMap(columnList, dataTypeList);
        // Converting a map to string to pass correct format to column
        String columnsMapString = mapToString(map);

        //creating Encryption key to save parquet file securely
        String encryptKey= "PRAGMA add_parquet_key('key256', '"+encryptVal+"')";
        stmtRecords.execute(encryptKey);

        // read CSV and write as Parquet file
        final String writeFile = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + userId + "/" + "/"
                + revisedInfoRequest.getFileId() + ".parquet";
        String query = "COPY (SELECT * from read_json_auto('" + filePath
                + "',ignore_errors=true, format='auto', columns=" + columnsMapString + dateFormatCondition
                + timeStampFormatCondition + ")) TO '" + writeFile + "' (ENCRYPTION_CONFIG {footer_key: 'key256'});";

        // handling data type mismatch
        try {
            stmtRecords.execute(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error:" + e.getMessage());
        }

        logger.info("************************\n" + query);

        stmtRecords.close();
        conn2.close();
    }

    public JSONArray changeSchmaforExcel(FileUploadRevisedInfoRequest revisedInfoRequest)
            throws SQLException, ExpectationFailedException {

        String fileName = revisedInfoRequest.getFileId();

        String filePath = System.getProperty("user.home") + "/" + "silzila-uploads" + "/" + "tmp" + "/" + fileName;
        Connection conn2 = ((DuckDBConnection) conn).duplicate();

        Statement stmtRecords = conn2.createStatement();

        // put column name & data type into separate list
        ArrayList<String> columnList = new ArrayList<String>();
        ArrayList<String> dataTypeList = new ArrayList<String>();

       
        for (int i = 0; i < revisedInfoRequest.getRevisedColumnInfos().size(); i++) {
            FileUploadRevisedColumnInfo col = revisedInfoRequest.getRevisedColumnInfos().get(i);
            String colName = col.getFieldName().replaceAll("[^a-zA-Z0-9]", "_");
            String silzilaDataType = col.getDataType().name().toLowerCase();
            String duckDbDataType = ConvertDuckDbDataType.toDuckDbDataType(silzilaDataType);
            String columnString = "'" + colName + "'";
            String dataTypeString = "'" + duckDbDataType + "'";
            columnList.add(columnString);
            dataTypeList.add(dataTypeString);
         }
        // build stringified list of columns
        String colMapString = "[" + String.join(", ", columnList) + "]";
        // build stringified list of data types
        String dataTypeMapString = "[" + String.join(", ", dataTypeList) + "]";
        logger.info("====================\n" + colMapString);
        logger.info("====================\n" + dataTypeMapString);

        // when user provides custom data format
        String dateFormatCondition = "";
        if (revisedInfoRequest.getDateFormat() != null && !revisedInfoRequest.getDateFormat().trim().isEmpty()) {
            dateFormatCondition = ", dateformat='" + revisedInfoRequest.getDateFormat().trim() + "'";
        }
        // when user provides custom time stamp format
        String timeStampFormatCondition = "";
        if (revisedInfoRequest.getTimestampFormat() != null
                && !revisedInfoRequest.getTimestampFormat().trim().isEmpty()) {
            timeStampFormatCondition = ", timestampformat='" + revisedInfoRequest.getTimestampFormat().trim() + "'";
        }

        String query = "SELECT * from read_csv_auto('" + filePath + "', SAMPLE_SIZE=200,names=" + colMapString
                + ", types=" + dataTypeMapString + dateFormatCondition + timeStampFormatCondition + ");";

        logger.info("************************\n" + query);
        ResultSet resultSet = null;

        // handling data type mismatch error
        try {
            resultSet = stmtRecords.executeQuery(query);
        } catch (SQLException e) {
            throw new ExpectationFailedException("you are trying for unmatched data type. Error: " + e.getMessage());
        }
        JSONArray jsonArray = ResultSetToJson.convertToJsonFlatFiles(resultSet);
        stmtRecords.close();
        conn2.close();

        return jsonArray;

    }

    public JSONObject runSyncQuery(String query) throws SQLException {
        // Duplicate connection for running the query
        Connection conn2 = ((DuckDBConnection) conn).duplicate();
        Statement stmtRecords = null;
        JSONObject jsonObject = null;

        try {
            // Create statement and execute query
            stmtRecords = conn2.createStatement();
            ResultSet resultSet = stmtRecords.executeQuery(query);

            // Convert ResultSet to JSONObject
            jsonObject = ResultSetToJson.convertToArray(resultSet);
        } catch (SQLException e) {
            // Handle SQL exception here
            e.printStackTrace();
            throw e; // Optionally rethrow the exception
        } finally {
            // Close statement and connection in finally block to ensure they are closed even if an error occurs
            if (stmtRecords != null) {
                stmtRecords.close();
            }
            if (conn2 != null) {
                conn2.close();
            }
        }
        return jsonObject;
    }

    public JSONArray relativeFilter(String userId, String dBConnectionId, String datasetId,String workspaceId,
    @Valid RelativeFilterRequest relativeFilter)
    throws RecordNotFoundException, BadRequestException, SQLException, ClassNotFoundException,
    JsonMappingException, JsonProcessingException {

            // Load dataset into memory buffer
            DatasetDTO ds = null;
            if (datasetId != null) {
                DatasetDTO bufferedDataset = buffer.getDatasetDetailsById(datasetId);
                ds = (bufferedDataset != null) ? bufferedDataset : loadDatasetInBuffer(dBConnectionId, datasetId,workspaceId, userId);
            }
            // Initialize variables
            JSONArray anchorDateArray;
            String query;
            // Check if dataset is flat file data or not
                // Get the table ID from the filter request
                String tableId = relativeFilter.getFilterTable().getTableId();

                ColumnFilter columnFilter = relativeFilter.getFilterTable();

                // Find the table object in the dataset schema 
                // Datasetfilter -> create a table object
                Table tableObj = ds!= null ? ds.getDataSchema().getTables().stream()
                        .filter(table -> table.getId().equals(tableId))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Error: table id is not present in Dataset!")):new Table(columnFilter.getTableId(), columnFilter.getFlatFileId(), null, null, null, columnFilter.getTableId()  , null, null, false, null);
                // Load file names from file IDs and load the files as views
                createViewForFlatFiles(userId, Collections.singletonList(tableObj),buffer.getFileDataByUserId(userId), encryptPwd+pepper);
                // Compose anchor date query for DuckDB and run it
                String anchorDateQuery = relativeFilterQueryComposer.anchorDateComposeQuery("duckdb", ds, relativeFilter);
                anchorDateArray = runQuery(anchorDateQuery);

                // Compose main query for DuckDB
                query = relativeFilterQueryComposer.composeQuery("duckdb", ds, relativeFilter, anchorDateArray);

            // Execute the main query and return the result
            JSONArray jsonArray = runQuery(query);

            return jsonArray;
}



}
