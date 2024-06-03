package com.silzila.service;

import com.silzila.exception.ExpectationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silzila.VizApplication;
import com.silzila.exception.BadRequestException;
import com.silzila.exception.RecordNotFoundException;
import com.silzila.helper.OracleDbJksRequestProcess;
import com.silzila.helper.ResultSetToJson;
import com.silzila.payload.request.DBConnectionRequest;
import com.silzila.payload.response.MetadataColumn;
import com.silzila.payload.response.MetadataDatabase;
import com.silzila.payload.response.MetadataTable;
import com.silzila.domain.entity.DBConnection;
import com.silzila.dto.OracleDTO;
import com.silzila.helper.CustomQueryValidator;

@Service
public class ConnectionPoolService {

    private static final Logger logger = LogManager.getLogger(ConnectionPoolService.class);

    final String SILZILA_DIR = System.getProperty("user.home") + "/" +
            "silzila-uploads";
    // private static final String SILZILA_DIR = "F:\\Silzila\\Oracle DB";

    HikariDataSource dataSource = null;
    HikariConfig config = new HikariConfig();

    Connection connection = null;
    Statement statement = null;
    ResultSet resultSet = null;

    private static Map<String, HikariDataSource> connectionPool = new HashMap<>();
    private static Map<String, DBConnection> connectionDetails = new HashMap<>();

    @Autowired
    DBConnectionService dbConnectionService;

    @Autowired
    CustomQueryValidator customQueryValidator;


    // creates connection pool & gets vendor name
    public String getVendorNameFromConnectionPool(String id, String userId)
            throws RecordNotFoundException, SQLException {
        String vendorName;
        if (connectionDetails.containsKey(id)) {
            vendorName = connectionDetails.get(id).getVendor();
        } else {
            createConnectionPool(id, userId);
            vendorName = connectionDetails.get(id).getVendor();
        }
        return vendorName;
    }

    // creates connection pool if not created already for a connection
    public void createConnectionPool(String id, String userId) throws RecordNotFoundException, SQLException {
        HikariDataSource dataSource = null;
        HikariConfig config = new HikariConfig();

        if (!connectionPool.containsKey(id)) {
            DBConnection dbConnection = dbConnectionService.getDBConnectionWithPasswordById(id, userId);
            String fullUrl = "";

            // BigQuery - token file path to be sent in URL
            if (dbConnection.getVendor().equals("bigquery")) {
                String password = dbConnection.getPasswordHash();
                String tempPath = null;
                // Create a path for the temporary JSON file
                try {
                    // Get the current timestamp
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                    String timestamp = dateFormat.format(new Date());

                    // Create a temporary file with a timestamp in the filename
                    Path tempFilePath = Files.createTempFile("tempfile_" + timestamp, ".json");

                    // Write the password to the temporary file
                    // Replace this with the actual password
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFilePath.toFile()))) {
                        writer.write(password);
                    }
                    tempPath = tempFilePath.toString();
                    // Print the path of the temporary file
                    logger.info("Temporary token file created for bigquery: " + tempPath);

                } catch (IOException e) {
                    // Handle exception if file creation fails
                    e.printStackTrace();
                }
                fullUrl = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;OAuthType=0" +
                        ";ProjectId=" + dbConnection.getProjectId() +
                        ";OAuthServiceAcctEmail=" + dbConnection.getClientEmail() +
                        ";OAuthPvtKeyPath=" + tempPath +
                        ";";
                dataSource = new HikariDataSource();
                dataSource.setMinimumIdle(1);
                dataSource.setMaximumPoolSize(3);
                dataSource.setDataSourceClassName("com.simba.googlebigquery.jdbc.DataSource");
                dataSource.addDataSourceProperty("url", fullUrl);
            }

            // SQL Server is handled differently
            else if (dbConnection.getVendor().equals("sqlserver")) {
                fullUrl = "jdbc:sqlserver://" + dbConnection.getServer() + ":" + dbConnection.getPort()
                        + ";databaseName=" + dbConnection.getDatabase() + ";user=" + dbConnection.getUsername()
                        + ";password=" + dbConnection.getPasswordHash() + ";encrypt=true;trustServerCertificate=true;";

                dataSource = new HikariDataSource();
                dataSource.setMinimumIdle(1);
                dataSource.setMaximumPoolSize(3);
                dataSource.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
                dataSource.addDataSourceProperty("url", fullUrl);
            }
            // for Databricks
            else if (dbConnection.getVendor().equals("databricks")) {
                fullUrl = "jdbc:databricks://" + dbConnection.getServer() + ":"
                        + dbConnection.getPort() + "/" + dbConnection.getDatabase()
                        + ";transportMode=http;ssl=1;httpPath=" +
                        dbConnection.getHttpPath() + ";AuthMech=3;UID=token;PWD=" + dbConnection.getPasswordHash()
                        + ";EnableArrow=0";
                config.setJdbcUrl(fullUrl);
                config.setDriverClassName("com.databricks.client.jdbc.Driver");
                config.addDataSourceProperty("minimulIdle", "1");
                config.addDataSourceProperty("maximumPoolSize", "2");
                dataSource = new HikariDataSource(config);
            } else if (dbConnection.getVendor().equals("oracle")) {
                String url = "jdbc:oracle:thin:@(description= (retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port="
                        + dbConnection.getPort() +
                        ")(host=" + dbConnection.getServer() + "))(connect_data=(service_name="
                        + dbConnection.getDatabase()
                        + "))(security=(ssl_server_dn_match=yes)))";

                config.setJdbcUrl(url);
                config.setUsername(dbConnection.getUsername());
                config.setPassword(dbConnection.getPasswordHash());
                config.setMaximumPoolSize(5);
                // SSL properties
                config.addDataSourceProperty("javax.net.ssl.keyStore",
                        SILZILA_DIR + "/jks_Collections/store/" + dbConnection.getKeystoreFileName());
                config.addDataSourceProperty("javax.net.ssl.keyStorePassword", dbConnection.getKeystorePassword());
                config.addDataSourceProperty("javax.net.ssl.trustStore",
                        SILZILA_DIR + "/jks_Collections/store/" + dbConnection.getTruststoreFileName());
                config.addDataSourceProperty("javax.net.ssl.trustStorePassword", dbConnection.getTruststorePassword());

                dataSource = new HikariDataSource(config);
            }
            // snowflake
            else if (dbConnection.getVendor().equals("snowflake")){
                String port = "";
                if (dbConnection.getPort() != null) {
                    port = ":" + dbConnection.getPort();
                }
                if (dbConnection.getDatabase() != null) {
                    config.addDataSourceProperty("db", dbConnection.getDatabase());
                }
                if (dbConnection.getWarehouse() != null) {
                    config.addDataSourceProperty("warehouse", dbConnection.getWarehouse());
                }
                fullUrl = "jdbc:" + dbConnection.getVendor() + "://" + dbConnection.getServer() + port + "/";
                config.setJdbcUrl(fullUrl);
                config.setDriverClassName("net.snowflake.client.jdbc.SnowflakeDriver");
                config.addDataSourceProperty("user", dbConnection.getUsername());
                config.addDataSourceProperty("password", dbConnection.getPasswordHash());
                config.addDataSourceProperty("minimulIdle", "1");
                config.addDataSourceProperty("maximumPoolSize", "2");
                dataSource = new HikariDataSource(config);
            }
            //motherduck
            else if (dbConnection.getVendor().equals("motherduck")) {
                fullUrl = "jdbc:duckdb:md:" + dbConnection.getDatabase() +  "?motherduck_token=" + dbConnection.getPasswordHash();
                config.setJdbcUrl(fullUrl);
                dataSource = new HikariDataSource(config);
            }
            // for Postgres & MySQL
            else {
                // dbConnection.getPasswordHash() now holds decrypted password
                fullUrl = "jdbc:" + dbConnection.getVendor() + "://" + dbConnection.getServer() + ":"
                        + dbConnection.getPort() + "/" + dbConnection.getDatabase();
                config.setJdbcUrl(fullUrl);
                config.setUsername(dbConnection.getUsername());
                config.setPassword(dbConnection.getPasswordHash());
                config.addDataSourceProperty("minimulIdle", "1");
                config.addDataSourceProperty("maximumPoolSize", "3");
                config.setConnectionTimeout(300000);
                config.setIdleTimeout(120000);
                dataSource = new HikariDataSource(config);
            }
            // connection = dataSource.getConnection();
            connectionPool.put(id, dataSource);
            connectionDetails.put(id, dbConnection);
        }
    }

    // STUB - not needed
    public JSONArray checkSqlServer() throws SQLException, RecordNotFoundException {
        String connectionUrl = "jdbc:sqlserver://3.7.39.222:1433;databaseName=landmark;user=balu;password=Marina!1234;encrypt=false";
        logger.info("Connection String ==========" + connectionUrl);
        try {
            Connection con = DriverManager.getConnection(connectionUrl);
            Statement statement = con.createStatement();
            String sql = "SELECT * from pos.category";
            ResultSet rs = statement.executeQuery(sql);
            // if (rs.wasNull()) {
            // throw new RecordNotFoundException("no result!");
            // }
            // while (rs.next()) {
            // System.out.println("--------" + rs.getString("category"));
            // }
            JSONArray jsonArray = ResultSetToJson.convertToJson(rs);
            /* DB NAMES */
            DatabaseMetaData databaseMetaData = con.getMetaData();
            ResultSet resultSet = databaseMetaData.getCatalogs();
            while (resultSet.next()) {
                MetadataDatabase metadataDatabase = new MetadataDatabase();
                metadataDatabase.setDatabase(resultSet.getString("TABLE_CAT"));
                logger.info("Schema loop ======== " + resultSet.getString("TABLE_CAT"));
            }
            /* SCHEMA NAMES */
            ResultSet resultSet2 = databaseMetaData.getSchemas();
            // JSONArray resultSet = ResultSetToJson.convertToJson(resultSet);
            while (resultSet2.next()) {
                String databaseName = resultSet2.getString("TABLE_CATALOG");
                String schemaName = resultSet2.getString("TABLE_SCHEM");
                logger.info("DB NAME ======== " + databaseName + " SCHEMA NAME ======= " + schemaName);
            }
            con.close();
            // System.out.println("Stringigy JSON ===========\n " + jsonArray.toString());
            return jsonArray;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // generates SQL and runs in DB and gives response based on
    // user drag & drop columns
    public JSONArray runQuery(String id, String userId, String query) throws RecordNotFoundException, SQLException {
        try (Connection _connection = connectionPool.get(id).getConnection();
                PreparedStatement pst = _connection.prepareStatement(query);
                ResultSet rs = pst.executeQuery();) {
            // statement = _connection.createStatement();
            // resultSet = statement.executeQuery(query);
            JSONArray jsonArray = ResultSetToJson.convertToJson(rs);
            // statement.close();
            return jsonArray;
        } catch (Exception e) {
            logger.warn("runQuery Exception ----------------");
            logger.warn("error: " + e.toString());
            throw e;
        }
    }

       public List<Map<String,String>> getColumForCustomQuery(String id, String userId, String query) throws RecordNotFoundException, SQLException, ExpectationFailedException {
        if(customQueryValidator.customQueryValidator(query)) {
            createConnectionPool(id, userId);
            try {
                try (Connection _connection = connectionPool.get(id).getConnection();
                     PreparedStatement pst = _connection.prepareStatement(query);
                     ResultSet rs = pst.executeQuery();) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int count = rsmd.getColumnCount();
                    List<Map<String, String>> columnList = new ArrayList<Map<String, String>>();
                    for (int i = 1; i <= count; i++) {
                        Map<String, String> columnDataTypeMap = new HashMap<>();
                        columnDataTypeMap.put("columnName", rsmd.getColumnName(i));
                        columnDataTypeMap.put("dataType", rsmd.getColumnTypeName(i));
                        columnList.add(columnDataTypeMap);
                    }
                    return columnList;
                } catch (Exception e) {
                    logger.warn("runQuery Exception ----------------");
                    logger.warn("error: " + e.toString());
                    throw e;
                }
            }catch (Exception e){
                throw new ExpectationFailedException("Wrong query!!Please check your query format");
            }

        }else {
            throw new ExpectationFailedException("Wrong query!!CustomQuery is only allowed with SELECT");
        }
    }
    public JSONArray getSampleRecordsForCustomQuery(String dBConnectionId, String userId, String query,Integer recordCount) throws RecordNotFoundException, SQLException, ExpectationFailedException {
        if (customQueryValidator.customQueryValidator(query)) {
            String vendorName = getVendorNameFromConnectionPool(dBConnectionId, userId);
            String queryWithLimit="";
            try{
                createConnectionPool(dBConnectionId, userId);
                if(recordCount<=100) {
                    if (vendorName.equals("oracle")) {
                        queryWithLimit = "select * from( " + query + ") WHERE ROWNUM <= " + recordCount;
                    } else if (vendorName.equals("sqlserver")) {
                        queryWithLimit = "WITH CTE AS ( " + query + ") SELECT TOP " + recordCount + " * FROM CTE;";
                    } else {
                        queryWithLimit = "select * from( " + query + ") AS CQ limit " + recordCount;
                    }
                }else {
                    if (vendorName.equals("oracle")) {
                        queryWithLimit = "select * from( " + query + ") WHERE ROWNUM <= 100";
                    } else if (vendorName.equals("sqlserver")) {
                        queryWithLimit = "WITH CTE AS ( " + query + ") SELECT TOP 100  * FROM CTE;";
                    }
                    else {
                        queryWithLimit = "select * from( " + query + ") AS CQ limit 100";
                    }
                }
            JSONArray jsonArray = runQuery(dBConnectionId, userId, queryWithLimit);
            return jsonArray;
            }catch (Exception e) {
                throw new ExpectationFailedException("Wrong query!!Please check your query format");
            }
        }else {
            throw new ExpectationFailedException("Wrong query!!CustomQuery is only allowed with SELECT");
        }
    }
    // Metadata discovery - Get Database names
    public ArrayList<String> getDatabase(String id, String userId)
            throws RecordNotFoundException, SQLException {
        // first create connection pool to query DB
        // when connection id is not available, RecordNotFoundException will be throws
        createConnectionPool(id, userId);

        String vendorName = getVendorNameFromConnectionPool(id, userId);

        ArrayList<String> schemaList = new ArrayList<String>();

        try (Connection _connection = connectionPool.get(id).getConnection();) {
            DatabaseMetaData databaseMetaData = _connection.getMetaData();

            if (vendorName.equals("mysql") || vendorName.equals("oracle")) {
                ResultSet resultSet = databaseMetaData.getSchemas();
                while (resultSet.next()) {
                    String databaseName = resultSet.getString("TABLE_SCHEM");
                    // append iterated result set into list
                    schemaList.add(databaseName);
                }
            }

            // get database (catalog) names from metadata object
            else {
                ResultSet resultSet = databaseMetaData.getCatalogs();
                while (resultSet.next()) {
                    String databaseName = resultSet.getString("TABLE_CAT");
                    // append iterated result set into list
                    schemaList.add(databaseName);
                }
            }
            return schemaList;
        } catch (Exception e) {
            throw e;
        }
    }

    // Metadata discovery - Get Schema names
    public List<String> getSchema(String id, String userId, String databaseName)
            throws RecordNotFoundException, SQLException, BadRequestException {
        // first create connection pool to query DB
        String vendorName = getVendorNameFromConnectionPool(id, userId);

        List<String> schemaList = new ArrayList<>();
        try {
            /*
             * SQL Server is handled differently as we can't get schema names for other DB's
             * in the same server through JDBC Metadata.
             * So, getting schema names by running Query.
             * Note: DB Name is passed to the query.
             */
            if (vendorName.equals("sqlserver")) {
                // DB Name is must as SQL Server may contain many DB's in single server
                if (databaseName == null || databaseName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database name is not provided");
                }
                String query = """
                        select s.name as schema_name
                        from %s.sys.schemas s
                        inner join sys.sysusers u
                        on u.uid = s.principal_id
                        where u.issqluser = 1
                        and u.name not in ('sys', 'guest', 'INFORMATION_SCHEMA')""".formatted(databaseName);
                try (Connection _connection = connectionPool.get(id).getConnection();
                        PreparedStatement pst = _connection.prepareStatement(query);
                        ResultSet rs = pst.executeQuery();) {
                    JSONArray jsonArray = ResultSetToJson.convertToJson(rs);
                    // get list of schema names from result
                    schemaList = new ArrayList<>();
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject rec = jsonArray.getJSONObject(i);
                            // schema_name is the key in the JSON Object
                            String schema = rec.getString("schema_name");
                            schemaList.add(schema);
                        }
                    }
                    return schemaList;
                }
            }
            // Databricks & Snowflake
            else if (vendorName.equals("databricks")|| vendorName.equals("snowflake")) {
                if (databaseName == null || databaseName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database name is not provided");
                }
                try (Connection _connection = connectionPool.get(id).getConnection();) {
                    DatabaseMetaData databaseMetaData = _connection.getMetaData();
                    // get list of schema names from result
                    ResultSet resultSet = databaseMetaData.getSchemas(databaseName, null);
                    while (resultSet.next()) {
                        String schemaName = resultSet.getString("TABLE_SCHEM");
                        schemaList.add(schemaName);
                    }
                    return schemaList;
                }
            }
            // for bigquery
            else if (vendorName.equals("bigquery") || vendorName.equals("motherduck")) {
                if (databaseName == null || databaseName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database name is not provided");
                }
                try (Connection _connection = connectionPool.get(id).getConnection();) {
                    DatabaseMetaData databaseMetaData = _connection.getMetaData();
                    ResultSet resultSet2 = databaseMetaData.getSchemas(databaseName, null);
                    while (resultSet2.next()) {
                        // TABLE_CATALOG is the DB name and we don't need
                        // String dbName = resultSet2.getString("TABLE_CATALOG");
                        String schemaName = resultSet2.getString("TABLE_SCHEM");
                        schemaList.add(schemaName);
                    }
                    return schemaList;
                }
            }
            // for Postgres & MySQL
            else {
                try (Connection _connection = connectionPool.get(id).getConnection();) {

                    DatabaseMetaData databaseMetaData = _connection.getMetaData();

                    // get database & schema names from metadata object
                    // Postgres will not show other databases in the server and MySQL doesn't have
                    // Schema. So, either DB or schema will be populated for Postgres and MySQL
                    // and shemas and can query cross DB
                    ResultSet resultSet = databaseMetaData.getSchemas();
                    // iterate result and add row to object and append object to list
                    while (resultSet.next()) {
                        // TABLE_CATALOG is the DB name and we don't need
                        // String dbName = resultSet.getString("TABLE_CATALOG");
                        String schemaName = resultSet.getString("TABLE_SCHEM");
                        schemaList.add(schemaName);
                    }
                    return schemaList;
                }

            }
        } catch (Exception e) {
            throw e;
        }
    }

    public MetadataTable getTable(String id, String userId, String databaseName, String schemaName)
            throws RecordNotFoundException, SQLException, BadRequestException {

        // first create connection pool to query DB
        String vendorName = getVendorNameFromConnectionPool(id, userId);

        try (Connection _connection = connectionPool.get(id).getConnection();) {
            DatabaseMetaData databaseMetaData = _connection.getMetaData();
            // this object will hold list of tables and list of views
            MetadataTable metadataTable = new MetadataTable();
            ResultSet resultSetTables = null;
            ResultSet resultSetViews = null;

            /*
             * based on database dialect, we pass either DB name or schema name at different
             * position in the funciton
             */

            // for SQL Server DB
            if (vendorName.equalsIgnoreCase("sqlserver")) {
                // throw error if db name or schema name is not passed
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // ADD Table Names
                resultSetTables = databaseMetaData.getTables(databaseName, schemaName, null, null);
                while (resultSetTables.next()) {
                    // fetch Tables
                    if (resultSetTables.getString("TABLE_TYPE").equals("TABLE")) {
                        String tableName = resultSetTables.getString("TABLE_NAME");
                        metadataTable.getTables().add(tableName);
                    }
                    // fetch Views
                    else if (resultSetTables.getString("TABLE_TYPE").equals("VIEW")) {
                        String viewName = resultSetTables.getString("TABLE_NAME");
                        metadataTable.getViews().add(viewName);
                    }
                }
                resultSetTables.close();

            }
            // for Databricks $ Snowflake
            else if (vendorName.equalsIgnoreCase("databricks") || vendorName.equalsIgnoreCase("snowflake")) {
                // throw error if db name or schema name is not passed
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // Add Tables
                resultSetTables = databaseMetaData.getTables(databaseName, schemaName, null, new String[] { "TABLE" });
                // Add Views
                resultSetViews = databaseMetaData.getTables(databaseName, schemaName, null, new String[] { "VIEW" });
                while (resultSetTables.next()) {
                    // fetch Tables
                    String tableName = resultSetTables.getString("TABLE_NAME");
                    metadataTable.getTables().add(tableName);
                }
                while (resultSetViews.next()) {
                    // fetch Views
                    String tableName = resultSetViews.getString("TABLE_NAME");
                    metadataTable.getViews().add(tableName);
                }
                resultSetTables.close();
                resultSetViews.close();

            }
            // for bigquery
            else if (vendorName.equalsIgnoreCase("bigquery")) {
                // throw error if db name or schema name is not passed
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // Add Tables
                resultSetTables = databaseMetaData.getTables(databaseName, schemaName, null, new String[] { "TABLE" });
                // Add Views
                resultSetViews = databaseMetaData.getTables(databaseName, schemaName, null, new String[] { "VIEW" });
                while (resultSetTables.next()) {
                    // TABLE_CATALOG is the DB name and we don't need
                    // String dbName = resultSet3.getString("TABLE_CATALOG");
                    String tableName = resultSetTables.getString("TABLE_NAME");
                    metadataTable.getTables().add(tableName);
                }
                while (resultSetViews.next()) {
                    // TABLE_CATALOG is the DB name and we don't need
                    // String dbName = resultSet4.getString("TABLE_CATALOG");
                    String tableName = resultSetViews.getString("TABLE_NAME");
                    metadataTable.getViews().add(tableName);
                }
                resultSetTables.close();
                resultSetViews.close();

            } // for motherduck
            else if (vendorName.equals("motherduck")) {
                // throw error if db name or schema name is not passed
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // Add Tables
                resultSetTables = databaseMetaData.getTables(databaseName, schemaName, null, null);
                while (resultSetTables.next()) {
                    // TABLE_CATALOG is the DB name and we don't need
                    // String dbName = resultSet3.getString("TABLE_CATALOG");
                    String tableName = resultSetTables.getString("TABLE_NAME");
                    metadataTable.getTables().add(tableName);
                }
                resultSetTables.close();
            }
            // postgres & MySql are handled the same but different from SQL Server
            else {
                // for POSTGRESQL DB
                // throw error if schema name is not passed
                if (vendorName.equalsIgnoreCase("postgresql") || vendorName.equalsIgnoreCase("redshift") || vendorName
                        .equalsIgnoreCase("oracle")) {
                    if (schemaName == null || schemaName.trim().isEmpty()) {
                        throw new BadRequestException("Error: Schema name is not provided!");
                    }
                    // ADD Table Names
                    resultSetTables = databaseMetaData.getTables(null, schemaName, null,
                            new String[] { "TABLE" });
                    // ADD View Names
                    resultSetViews = databaseMetaData.getTables(null, schemaName, null, new String[] { "VIEW" });
                }
                // for MYSQL DB
                else if (vendorName.equalsIgnoreCase("mysql")) {
                    // throw error if database name is not passed
                    if (databaseName == null || databaseName.trim().isEmpty()) {
                        throw new BadRequestException("Error: Database name is not provided!");
                    }
                    // ADD Table Names
                    resultSetTables = databaseMetaData.getTables(databaseName, null, null,
                            new String[] { "TABLE" });
                    // ADD View Names
                    resultSetViews = databaseMetaData.getTables(databaseName, null, null,
                            new String[] { "VIEW" });
                }

                // iterate table names and add it to List
                while (resultSetTables.next()) {
                    String tableName = resultSetTables.getString("TABLE_NAME");
                    metadataTable.getTables().add(tableName);
                }
                // iterate view names and add it to List
                while (resultSetViews.next()) {
                    String viewName = resultSetViews.getString("TABLE_NAME");
                    metadataTable.getViews().add(viewName);
                }
                resultSetTables.close();
                resultSetViews.close();
            }
            return metadataTable;
        } catch (Exception e) {
            throw e;
        }
    }

    // Metadata discovery - Get Column names
    public ArrayList<MetadataColumn> getColumn(String id, String userId, String databaseName, String schemaName,
            String tableName)
            throws RecordNotFoundException, SQLException, BadRequestException {

        // first create connection pool to query DB
        String vendorName = getVendorNameFromConnectionPool(id, userId);

        // metadataColumns list will contain the final result
        ArrayList<MetadataColumn> metadataColumns = new ArrayList<MetadataColumn>();

        try (Connection _connection = connectionPool.get(id).getConnection();) {

            DatabaseMetaData databaseMetaData = _connection.getMetaData();
            ResultSet resultSet = null;

            // based on database dialect, we pass either DB name or schema name at different
            // position in the funciton for POSTGRESQL DB
            if (vendorName.equals("postgresql") || vendorName.equals("redshift") || vendorName
                    .equalsIgnoreCase("oracle")) {
                // schema name is must for postgres
                if (schemaName == null || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Schema name is not provided!");
                }
                // get column names from the given schema and Table name
                resultSet = databaseMetaData.getColumns(null, schemaName, tableName, null);
            }
            // for MYSQL DB
            else if (vendorName.equals("mysql")) {
                // DB name is must for MySQL
                if (databaseName == null || databaseName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database name is not provided!");
                }
                // get column names from the given schema and Table name
                resultSet = databaseMetaData.getColumns(databaseName, null, tableName, null);

            }
            // for SQL Server DB
            else if (vendorName.equals("sqlserver")) {
                // DB name & schema name are must for SQL Server
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // get column names from the given schema and Table name
                resultSet = databaseMetaData.getColumns(databaseName, schemaName, tableName, null);

            }
            // for Databricks & Snowflake
            else if (vendorName.equals("databricks") || vendorName.equals("snowflake")) {
                // DB name & schema name are must for Databricks & Snowflake
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // get column names from the given schema and Table name
                resultSet = databaseMetaData.getColumns(databaseName, schemaName, tableName, null);
            }
            // for Bigquery & Motherduck
            else if (vendorName.equals("bigquery") || vendorName.equals("motherduck")) {
                // DB name & schema name are must for Bigquery & Motherduck
                if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                        || schemaName.trim().isEmpty()) {
                    throw new BadRequestException("Error: Database & Schema names are not provided!");
                }
                // get column names from the given schema and Table name
                resultSet = databaseMetaData.getColumns(databaseName, schemaName, tableName, null);
            }
            // iterate table names and add it to List
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                String dataType = resultSet.getString("DATA_TYPE");
                MetadataColumn metadataColumn = new MetadataColumn(columnName, dataType);
                metadataColumns.add(metadataColumn);
            }
            return metadataColumns;
        } catch (Exception e) {
            throw e;
        }
    }



    // Metadata discovery - Get Sample Records of table
    public JSONArray getSampleRecords(String id, String userId, String databaseName, String schemaName,
            String tableName, Integer recordCount)
            throws RecordNotFoundException, SQLException, BadRequestException {
        // set fall back record count
        if (recordCount == null || recordCount > 250) {
            recordCount = 250;
        }
        // first create connection pool to query DB
        String vendorName = getVendorNameFromConnectionPool(id, userId);
        String query = "";

        // based on database dialect, we pass different SELECT * Statement
        // for POSTGRESQL DB
        if (vendorName.equals("postgresql") || vendorName.equals("redshift")) {
            // schema name is must for postgres & redshift
            if (schemaName == null || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Schema name is not provided!");
            }
            // construct query
            query = "SELECT * FROM " + schemaName + "." + tableName + " LIMIT " + recordCount;
        }
        // for BIGQUERY DB
        else if (vendorName.equals("bigquery")) {
            // schema name is must for Bigquery
            if (schemaName == null || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Schema name is not provided!");
            }
            // construct query
            query = "SELECT * FROM `" + databaseName + "." + schemaName + "." + tableName + "` LIMIT " + recordCount;
        }
        // for MYSQL DB
        else if (vendorName.equals("mysql") || vendorName.equals("motherduck")) {
            // DB name is must for MySQL & Motherduck
            if (databaseName == null || databaseName.trim().isEmpty()) {
                throw new BadRequestException("Error: Database name is not provided!");
            }
            // construct query
            query = "SELECT * FROM " + databaseName + "." + tableName + " LIMIT " + recordCount;

        }
        // for SQL Server DB
        else if (vendorName.equals("sqlserver")) {
            // DB name & schema name are must for SQL Server
            if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                    || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Database & Schema names are not provided!");
            }
            // construct query
            query = "SELECT TOP " + recordCount + " * FROM " + databaseName + "." + schemaName + "." + tableName;

        }
        // for Databricks
        else if (vendorName.equals("databricks")) {
            // DB name & schema name are must for Databricks
            if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                    || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Database & Schema names are not provided!");
            }
            // construct query
            query = "SELECT * FROM " + databaseName + ".`" + schemaName + "`." + tableName + " LIMIT " + recordCount;
        }
        // oracle
        else if (vendorName.equalsIgnoreCase("oracle")) {
            if (schemaName == null || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Schema name is not provided!");
            }

            query = "SELECT * FROM " + schemaName + "." + tableName + " FETCH FIRST " + recordCount + " ROWS ONLY";
        }
        // snowflake
        else if (vendorName.equalsIgnoreCase("snowflake")) {
            // DB name & schema name are must for snowflake
            if (databaseName == null || databaseName.trim().isEmpty() || schemaName == null
                    || schemaName.trim().isEmpty()) {
                throw new BadRequestException("Error: Database & Schema names are not provided!");
            }
            // construct query
            query = "SELECT * FROM " + databaseName + "." + schemaName + "." + tableName + " LIMIT " + recordCount; 
        }
        // RUN THE 'SELECT *' QUERY
        try (Connection _connection = connectionPool.get(id).getConnection();
                PreparedStatement pst = _connection.prepareStatement(query);
                ResultSet rs = pst.executeQuery();) {
            // Connection _connection = connectionPool.get(id).getConnection();
            // statement = _connection.createStatement();
            // resultSet = statement.executeQuery(query);
            JSONArray jsonArray = ResultSetToJson.convertToJson(rs);
            // statement.close();
            return jsonArray;
        } catch (Exception e) {
            throw e;
        }
    }

    // test connect a given database connection parameters
    public void testDBConnection(DBConnectionRequest request) throws SQLException, BadRequestException {

        HikariDataSource dataSource = null;
        HikariConfig config = new HikariConfig();

        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        String tempPath = null;

        // SQL Server is hadled differently
        if (request.getVendor().equals("sqlserver")) {
            String fullUrl = "jdbc:sqlserver://" + request.getServer() + ":" + request.getPort()
                    + ";databaseName=" + request.getDatabase() + ";user=" + request.getUsername()
                    + ";password=" + request.getPassword() + ";encrypt=true;trustServerCertificate=true;";

            dataSource = new HikariDataSource();
            dataSource.setMinimumIdle(1);
            dataSource.setMaximumPoolSize(1);
            dataSource.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
            dataSource.addDataSourceProperty("url", fullUrl);
        }
        // snowflake
        else if (request.getVendor().equals("snowflake")) {
            String port = "";
            if (request.getPort() != null) {
                port = ":" + request.getPort();
            }
            if (request.getDatabase() != null) {
                config.addDataSourceProperty("db", request.getDatabase());
            }
            if (request.getWarehouse() != null) {
                config.addDataSourceProperty("warehouse", request.getWarehouse());
            }
            String fullUrl = "jdbc:" + request.getVendor() + "://" + request.getServer() + port + "/";
            config.setJdbcUrl(fullUrl);
            config.setDriverClassName("net.snowflake.client.jdbc.SnowflakeDriver");
            config.addDataSourceProperty("user", request.getUsername());
            config.addDataSourceProperty("password", request.getPassword());
            config.addDataSourceProperty("minimulIdle", "1");
            config.addDataSourceProperty("maximumPoolSize", "2");
            dataSource = new HikariDataSource(config);
        }
        // Databricks
        else if (request.getVendor().equals("databricks")) {
            String fullUrl = "jdbc:databricks://" + request.getServer() + ":"
                    + request.getPort() + "/" + request.getDatabase() + ";transportMode=http;ssl=1;httpPath="
                    + request.getHttpPath() + ";AuthMech=3;UID=token;PWD=" + request.getPassword() + ";EnableArrow=0";
            logger.info(fullUrl);
            config.setJdbcUrl(fullUrl);
            config.setDriverClassName("com.databricks.client.jdbc.Driver");
            config.addDataSourceProperty("minimulIdle", "1");
            config.addDataSourceProperty("maximumPoolSize", "2");
            dataSource = new HikariDataSource(config);
        }
        // Bigquery
        else if (request.getVendor().equals("bigquery")) {
            String projectId = null;
            String clientEmail = null;
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode actualObj = mapper.readTree(request.getPassword());

                projectId = actualObj.get("project_id").asText();
                clientEmail = actualObj.get("client_email").asText();

                if (projectId.isEmpty() || clientEmail.isEmpty()) {
                    throw new RuntimeException("Project ID or Client Email not found in the token.");
                }

                logger.info("Project ID: " + projectId);
                logger.info("Client Email: " + clientEmail);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Create a path for the temporary JSON file
            try {
                // Get the current timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                String timestamp = dateFormat.format(new Date());

                // Create a temporary file with a timestamp in the filename
                Path tempFilePath = Files.createTempFile("tempfile_" + timestamp, ".json");

                // Write the password to the temporary file
                // Replace this with the actual password
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFilePath.toFile()))) {
                    writer.write(request.getPassword());
                }
                tempPath = tempFilePath.toString();
                // Print the path of the temporary file
                logger.info("Temporary token file created for bigquery: " + tempPath);

            } catch (IOException e) {
                // Handle exception if file creation fails
                e.printStackTrace();
            }
            String fullUrl = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;OAuthType=0" +
                    ";ProjectId=" + projectId +
                    ";OAuthServiceAcctEmail=" + clientEmail +
                    ";OAuthPvtKeyPath="
                    + tempPath +
                    ";";

            dataSource = new HikariDataSource();
            dataSource.setDataSourceClassName("com.simba.googlebigquery.jdbc.DataSource");
            dataSource.setMinimumIdle(1);
            dataSource.setMaximumPoolSize(3);
            dataSource.addDataSourceProperty("url", fullUrl);
            dataSource.addDataSourceProperty("OAuthType", 0);
            dataSource.addDataSourceProperty("ProjectId", projectId);
            dataSource.addDataSourceProperty("OAuthServiceAcctEmail",
                    clientEmail);
            dataSource.addDataSourceProperty("OAuthPvtKeyFilePath",
                    tempPath);
        }
        //motherduck
        else if (request.getVendor().equals("motherduck")) {
            String fullUrl = "jdbc:duckdb:md:" + request.getDatabase() +  "?motherduck_token=" + request.getPassword();
            config.setJdbcUrl(fullUrl);
            dataSource = new HikariDataSource(config);
        }
        // Postgres & MySQL
        else {
            String fullUrl = "jdbc:" + request.getVendor() + "://" + request.getServer() + ":" + request.getPort() + "/"
                    + request.getDatabase();
            config.setJdbcUrl(fullUrl);
            config.setUsername(request.getUsername());
            config.setPassword(request.getPassword());
            config.addDataSourceProperty("minimulIdle", "1");
            config.addDataSourceProperty("maximumPoolSize", "1");
            dataSource = new HikariDataSource(config);
        }
        connection = dataSource.getConnection();
        Integer rowCount = 0;
        // run a simple query and see it record is fetched
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery("select 1");
            while (resultSet.next()) {
                rowCount++;
            }
            // return error if no record is fetched
            if (rowCount == 0) {
                throw new BadRequestException("Error: Something wrong!");
            }
        } catch (Exception e) {
            logger.warn("error: " + e.toString());
            throw e;

        } finally {
            resultSet.close();
            statement.close();
            connection.close();
            dataSource.close();
            if (request.getVendor().equals("bigquery")) {
                File tempFile = new File(tempPath);
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (deleted) {
                        logger.info("Temporary token file for bigquery deleted successfully.");
                    } else {
                        logger.warn("Failed to delete temporary token file for bigquery.");
                    }
                }
            }
        }
    }

    // STUB - test connect SQL Server with given connection parameters
    public JSONArray testSqlserverDBConnection() throws SQLException, BadRequestException {
        String fullUrl = "jdbc:sqlserver://3.7.39.222:1433;databaseName=landmark;user=balu;password=Marina!1234;encrypt=true;trustServerCertificate=true;";

        dataSource = new HikariDataSource();
        dataSource.setMaximumPoolSize(2);
        dataSource.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
        dataSource.addDataSourceProperty("url", fullUrl);
        connection = dataSource.getConnection();

        try {
            statement = connection.createStatement();
            String query = """
                    select s.name as schema_name
                    from landmark2.sys.schemas s
                    inner join sys.sysusers u
                    on u.uid = s.principal_id
                    where u.issqluser = 1
                    and u.name not in ('sys', 'guest', 'INFORMATION_SCHEMA')""";
            logger.info("------------------------------\n" + query);
            resultSet = statement.executeQuery(query); // select 1 as x, 2 as y
            JSONArray jsonArray = ResultSetToJson.convertToJson(resultSet);
            logger.info("------------------------------\n" + jsonArray.toString());
            statement.close();
            return jsonArray;

        } catch (Exception e) {
            logger.warn("runQuery Exception ----------------");
            logger.warn("error: " + e.toString());
            throw new SQLException();
        } finally {
            connection.close();
            dataSource.close();
        }
    }

    // this function is called (inside Main Function) during app shut down
    // to close all DB connections
    public void clearAllConnectionPoolsInShutDown() {
        connectionPool.forEach((id, conn) -> {
            try {
                conn.getConnection().close();
            } catch (SQLException e) {
                logger.warn("Error while closing all Connection Pools......");
                e.printStackTrace();
            }
        });
        logger.warn("clearAllConnectionPoolsInShutDown Fn Called..................");
    }

    // test oracle Connection
    public void testOracleConnection(OracleDTO oracleDTO) throws IOException, BadRequestException {

        Path path = Paths.get(SILZILA_DIR, "jks_Collections", "temptest");

        DBConnectionRequest req = OracleDbJksRequestProcess.parseOracleConnectionRequest(oracleDTO, false);

        HikariConfig config = new HikariConfig();

        String url = "jdbc:oracle:thin:@(description= (retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port="
                + req.getPort() +
                ")(host=" + req.getServer() + "))(connect_data=(service_name=" + req.getDatabase()
                + "))(security=(ssl_server_dn_match=yes)))";

        config.setJdbcUrl(url);
        config.setUsername(req.getUsername());
        config.setPassword(req.getPassword());
        config.setMaximumPoolSize(5);
        // SSL properties
        config.addDataSourceProperty("javax.net.ssl.keyStore",
                SILZILA_DIR + "/jks_Collections/temptest/" + req.getKeystore());
        config.addDataSourceProperty("javax.net.ssl.keyStorePassword", req.getKeystorePassword());
        config.addDataSourceProperty("javax.net.ssl.trustStore",
                SILZILA_DIR + "/jks_Collections/temptest/" + req.getTruststore());
        config.addDataSourceProperty("javax.net.ssl.trustStorePassword", req.getTruststorePassword());

        HikariDataSource dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection()) {
            Integer rowCount = 0;
            Statement statement = connection.createStatement();

            ResultSet data = statement.executeQuery("SELECT 1 FROM DUAL");
            while (data.next()) {
                rowCount++;
            }
            // return error if no record is fetched
            if (rowCount == 0) {
                throw new BadRequestException("Error: Something wrong!");
            }
        } catch (SQLException e) {
            logger.warn("Error in connecting to Oracle DB");
            e.printStackTrace();
        } finally {
            dataSource.close();
            // deleting the file after test the connection
            try {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                logger.warn("Error in deleting the temptest folder");
                // Handle exception if file deletion fails after testing
                e.printStackTrace();
                ;
            }
        }
    }
}
