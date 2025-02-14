package com.silzila.service;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.Valid;

import com.silzila.exception.ExpectationFailedException;
import com.silzila.payload.internals.QueryClauseFieldListMap;
import com.silzila.payload.request.*;
import com.silzila.payload.response.TableRelationshipResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.ibm.db2.cmx.runtime.internal.parser.EscapeLexer.sqlMode;
import com.silzila.dto.DatasetDTO;
import com.silzila.dto.DatasetNoSchemaDTO;
import com.silzila.dto.WorkspaceContentDTO;
import com.silzila.exception.BadRequestException;
import com.silzila.exception.RecordNotFoundException;
import com.silzila.domain.entity.Dataset;
import com.silzila.domain.entity.PlayBook;
import com.silzila.domain.entity.User;
import com.silzila.domain.entity.Workspace;
import com.silzila.querybuilder.QueryComposer;
import com.silzila.querybuilder.subTotalsCombination;
import com.silzila.querybuilder.CalculatedField.CalculatedFieldQueryComposer;
import com.silzila.querybuilder.filteroptions.FilterOptionsQueryComposer;
import com.silzila.querybuilder.relativefilter.RelativeFilterQueryComposer;
import com.silzila.querybuilder.syncFilterOption.SyncFilterOptionsQueryComposer;
import com.silzila.repository.DatasetRepository;
import com.silzila.repository.PlayBookRepository;
import com.silzila.helper.ColumnListFromClause;
import com.silzila.helper.CustomQueryValidator;
import com.silzila.helper.RelativeFilterProcessor;
import com.silzila.helper.UtilityService;
import com.silzila.payload.response.TableRelationshipResponse;

@Service
public class DatasetService {

    private static final Logger logger = LogManager.getLogger(DatasetService.class);

    @Autowired
    DatasetRepository datasetRepository;

    @Autowired
    ConnectionPoolService connectionPoolService;

    @Autowired
    QueryComposer queryComposer;

    @Autowired
    UserService userService;

    @Autowired
    CalculatedFieldQueryComposer calculatedFieldQueryComposer;

    @Autowired
    FilterOptionsQueryComposer filterOptionsQueryComposer;

    @Autowired
    RelativeFilterQueryComposer relativeFilterQueryComposer;

    @Autowired
    FileDataService fileDataService;

    @Autowired
    DuckDbService duckDbService;

    @Autowired
    UtilityService utilityService;

    @Autowired
    CustomQueryValidator customQueryValidator;

    @Autowired
    RelativeFilterProcessor relativeFilterProcessor;

    @Autowired
    DatasetAndFileDataBuffer buffer;

    @Autowired
    PlayBookRepository playBookRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    // HELPER FUNCTION
    // In Data Set, each table is given short, meaningful & unique alias name.
    // This alias is used in query building. eg. product AS p, sub_category as sc
    // single word table names gets first letter. If multiple tables start with
    // the same letter then numbers are suffixed.
    // eg. product AS p, process AS p2, pointofsales as p3
    // if table names have hyphen symbol, then first letters are combined
    // eg. point_of_sales AS pos, sub_category AS sc
    public DataSchema createTableAliasName(DataSchema dataSchema) {

        // create list of existing table names
        List<String> tableNameList = new ArrayList<String>();
        dataSchema.getTables().forEach(table -> tableNameList.add(table.getId()));
        // create list of alias (short name for) table names
        List<String> aliasNameList = new ArrayList<String>();
        // this suffix number helps track of how many times same alias names come and
        // appends +1 to alias name
        HashMap<String, Integer> aliasNamesWithoutSuffixNumber = new HashMap<>();

        dataSchema.getTables().forEach(table -> {
            // split alias name to list. eg. sub_category will be split into ['sub',
            // 'category']
            // split by underscore or space
            String[] splitStrings = table.getAlias().toLowerCase().split("_| ");
            String aliasName = "";

            // ################ To take one or multiple letters #################
            // if the split list has one word, get first letter of the word
            if (splitStrings.length == 1) {
                aliasName = Character.toString(splitStrings[0].charAt(0));
            }
            // for >1 word in list, get first letter of only first three words
            else {
                Integer maxLength = splitStrings.length > 3 ? 3 : splitStrings.length;
                for (int i = 0; i < maxLength; i++) {
                    aliasName += Character.toString(splitStrings[i].charAt(0));
                }
            }
            // ################ To add number suffix or not #################
            // first time an alias name comes
            if (!aliasNamesWithoutSuffixNumber.containsKey(aliasName)) {
                aliasNamesWithoutSuffixNumber.put(aliasName, 1);
                aliasNameList.add(aliasName);
            }
            // from alias_names_without_suffix_number, check how many times already there
            // and add one number
            else if (aliasNameList.contains(aliasName)) {
                aliasNamesWithoutSuffixNumber.put(aliasName, aliasNamesWithoutSuffixNumber.get(aliasName) + 1);
                aliasName = aliasName + aliasNamesWithoutSuffixNumber.get(aliasName);
                aliasNameList.add(aliasName);
            }
        });
        // change table1 & table2 to alias name in every relationship
        dataSchema.getRelationships().forEach(relationship -> {
            String table1Alias = aliasNameList.get(tableNameList.indexOf(relationship.getTable1()));
            String table2Alias = aliasNameList.get(tableNameList.indexOf(relationship.getTable2()));
            relationship.setTable1(table1Alias);
            relationship.setTable2(table2Alias);
        });
        if (dataSchema.getFilterPanels().size() != 0) {
            Map<String, String> aliasMap = new HashMap<>();
            for (int i = 0; i < tableNameList.size(); i++) {
                aliasMap.put(tableNameList.get(i), aliasNameList.get(i));
            }

            // changing tableId in every filters
            for (int i = 0; i < dataSchema.getFilterPanels().size(); i++) {
                for (int j = 0; j < dataSchema.getFilterPanels().get(i).getFilters().size(); j++) {
                    String tableId = dataSchema.getFilterPanels().get(i).getFilters().get(j).getTableId();
                    dataSchema.getFilterPanels().get(i).getFilters().get(j).setTableId(aliasMap.get(tableId));
                }
            }
        }

        // change id to alias name in every table
        for (int i = 0; i < dataSchema.getTables().size(); i++) {
            dataSchema.getTables().get(i).setId(aliasNameList.get(i));
        }

        return dataSchema;
    }

    // CREATE DATASET
    public DatasetDTO registerDataset(DatasetRequest datasetRequest, String userId, String workspaceId)
            throws JsonProcessingException, BadRequestException, ExpectationFailedException {
        User user = utilityService.getUserFromEmail(userId);

        List<Dataset> datasets = datasetRepository.findByUserIdAndDatasetName(userId, datasetRequest.getDatasetName());
        Workspace workspace = utilityService.getWorkspaceById(workspaceId);
        String datasetName = datasetRequest.getDatasetName().trim();
        // if dataset name already exists, send error
        if (!datasets.isEmpty()) {
            throw new BadRequestException("Error: Dataset Name is already taken!");
        }
        // checking whether the dataset is having custom query
        long count = datasetRequest.getDataSchema().getTables().stream().filter(table -> table.isCustomQuery()).count();
        if (count == 0) {
            DataSchema dataSchema = createTableAliasName(datasetRequest.getDataSchema());
            // datasetRequest.setDataSchema(dataSchema);
            // stringify dataschema (table + relationship section of API) to save in DB
            String jsonString = objectMapper.writeValueAsString(dataSchema);
            // crete dataset object and save in DB
            Dataset dataset = new Dataset();
            dataset.setConnectionId(datasetRequest.getConnectionId());
            dataset.setUserId(userId);
            dataset.setDatasetName(datasetName);
            dataset.setIsFlatFileData(datasetRequest.getIsFlatFileData());
            dataset.setDataSchema(jsonString);
            dataset.setWorkspace(workspace);
            dataset.setCreatedBy(user.getFirstName());

            datasetRepository.save(dataset);

            // repackage request object + dataset Id (from saved object) to return response
            DatasetDTO dto = new DatasetDTO(
                    dataset.getId(),
                    datasetRequest.getConnectionId(),
                    datasetRequest.getDatasetName(),
                    datasetRequest.getIsFlatFileData(),
                    dataSchema);
            return dto;
        }
        // if the data set contains custom query, validating the query
        else {
            Boolean isProperQuery = false;
            List<Table> tables = datasetRequest.getDataSchema().getTables();
            for (int i = 0; i < tables.size(); i++) {
                if (tables.get(i).isCustomQuery()) {
                    if (customQueryValidator
                            .customQueryValidator(datasetRequest.getDataSchema().getTables().get(i).getCustomQuery())) {
                        isProperQuery = true;
                    } else {
                        throw new ExpectationFailedException(
                                "Cannot proceed with dataset creation,CustomQuery is only allowed with SELECT clause");
                    }
                }
            }
            if (isProperQuery) {
                DataSchema dataSchema = createTableAliasName(datasetRequest.getDataSchema());
                // datasetRequest.setDataSchema(dataSchema);
                // stringify dataschema (table + relationship section of API) to save in DB
                String jsonString = objectMapper.writeValueAsString(dataSchema);
                // crete dataset object and save in DB
                Dataset dataset = new Dataset();
                dataset.setConnectionId(datasetRequest.getConnectionId());
                dataset.setUserId(userId);
                dataset.setDatasetName(datasetName);
                dataset.setIsFlatFileData(datasetRequest.getIsFlatFileData());
                dataset.setDataSchema(jsonString);
                dataset.setWorkspace(workspace);
                dataset.setCreatedBy(user.getFirstName());
                datasetRepository.save(dataset);
                // repackage request object + dataset Id (from saved object) to return response
                DatasetDTO dto = new DatasetDTO(
                        dataset.getId(),
                        datasetRequest.getConnectionId(),
                        datasetRequest.getDatasetName(),
                        datasetRequest.getIsFlatFileData(),
                        dataSchema);
                return dto;

            } else {
                throw new ExpectationFailedException(
                        "Cannot proceed with dataset creation, CustomQuery is only allowed with SELECT clause");
            }
        }
    }

    // UPDATE DATASET
    public DatasetDTO updateDataset(DatasetRequest datasetRequest, String id, String userId, String workspaceId)
            throws RecordNotFoundException, JsonProcessingException, JsonMappingException, BadRequestException,
            ExpectationFailedException {
        User user = utilityService.getUserFromEmail(userId);
        String _datasetName = datasetRequest.getDatasetName().trim();
        Dataset _dataset = datasetRepository.findByIdAndWorkspaceId(id, workspaceId).orElseThrow();
        Optional<Dataset> dOptional = datasetRepository.findByIdAndUserId(id, userId);
        // if no connection details inside optional warpper, then send NOT FOUND Error
        if (!dOptional.isPresent()) {
            throw new RecordNotFoundException("Error: No such Dataset Id exists!");
        }
        // if dataset name already exists, send error
        List<Dataset> datasets = datasetRepository.findByIdNotAndUserIdAndDatasetName(id, userId,
                datasetRequest.getDatasetName());
        if (!datasets.isEmpty()) {
            throw new BadRequestException("Error: Dataset Name is already taken!");
        }
        // rename id to table alias (short name)
        long count = datasetRequest.getDataSchema().getTables().stream().filter(table -> table.isCustomQuery()).count();

        if (count == 0) {
            DataSchema dataSchema = createTableAliasName(datasetRequest.getDataSchema());
            // stringify dataschema (table + relationship section of API) to save in DB
            String jsonString = objectMapper.writeValueAsString(dataSchema);

            _dataset.setDatasetName(_datasetName);
            _dataset.setDataSchema(jsonString);
            _dataset.setUpdatedBy(user.getFirstName());
            datasetRepository.save(_dataset);
            // repackage request object + dataset Id (from saved object) to return response
            DatasetDTO dto = new DatasetDTO(
                    _dataset.getId(),
                    _dataset.getConnectionId(),
                    _dataset.getDatasetName(),
                    _dataset.getIsFlatFileData(),
                    dataSchema);
            // add updated dataset in buffer
            buffer.addDatasetInBuffer(id, dto);
            return dto;
        } else {
            Boolean isProperQuery = null;
            List<Table> tables = datasetRequest.getDataSchema().getTables();
            for (int i = 0; i < tables.size(); i++) {
                if (tables.get(i).isCustomQuery()) {
                    if (customQueryValidator
                            .customQueryValidator(datasetRequest.getDataSchema().getTables().get(i).getCustomQuery())) {
                        isProperQuery = true;
                    } else {
                        throw new ExpectationFailedException(
                                "Cannot proceed with dataset updation,CustomQuery is only allowed with SELECT");
                    }
                }
            }
            if (isProperQuery) {
                DataSchema dataSchema = createTableAliasName(datasetRequest.getDataSchema());
                // stringify dataschema (table + relationship section of API) to save in DB
                String jsonString = objectMapper.writeValueAsString(dataSchema);

                _dataset.setDatasetName(_datasetName);
                _dataset.setDataSchema(jsonString);
                datasetRepository.save(_dataset);
                // repackage request object + dataset Id (from saved object) to return response
                DatasetDTO dto = new DatasetDTO(
                        _dataset.getId(),
                        _dataset.getConnectionId(),
                        _dataset.getDatasetName(),
                        _dataset.getIsFlatFileData(),
                        dataSchema);
                // add updated dataset in buffer
                buffer.addDatasetInBuffer(id, dto);
                return dto;
            } else {
                throw new ExpectationFailedException(
                        "Cannot proceed with dataset updation,CustomQuery is only allowed with SELECT");
            }

        }
    }

    public List<WorkspaceContentDTO> datasetDependency(String email, String workspaceId, String datasetId) {

        List<PlayBook> playbooks = playBookRepository.findByContentContaining(datasetId);

        List<String> playbookids = playbooks.stream()
                .map(PlayBook::getId)
                .collect(Collectors.toList());

        List<Object[]> dependentPlaybooks = playBookRepository.findPlayBooksWithWorkspaceAndParentDetails(playbookids);

        List<WorkspaceContentDTO> workspaceContentDTOList = dependentPlaybooks.stream()
                .map(result -> new WorkspaceContentDTO(
                        (String) result[0], // playbookId
                        (String) result[1], // playbookName
                        (String) result[2], // createdBy
                        (String) result[3], // workspaceId
                        (String) result[4], // workspaceName
                        (String) result[5], // parentWorkspaceId
                        (String) result[6] // parentWorkspaceName
                ))
                .collect(Collectors.toList());

        return workspaceContentDTOList;
    }

    // READ ALL DATASETS
    public List<DatasetNoSchemaDTO> getAllDatasets(String userId, String workspaceId)
            throws JsonProcessingException, BadRequestException {
        List<Dataset> datasets = datasetRepository.findByUserId(userId);
        utilityService.isValidWorkspaceId(workspaceId);
        // List<String> datasetIds = viewContentService.viewDatasetList(userId,
        // workspaceId);

        List<DatasetNoSchemaDTO> dsDtos = new ArrayList<>();
        datasets.forEach(ds -> {
            DatasetNoSchemaDTO dto = new DatasetNoSchemaDTO(
                    ds.getId(), ds.getConnectionId(), ds.getDatasetName(), ds.getIsFlatFileData());
            dsDtos.add(dto);
        });
        return dsDtos;
    }

    // READ ONE DATASET
    public DatasetDTO getDatasetById(String id, String userId, String workspaceId)
            throws RecordNotFoundException, JsonMappingException, JsonProcessingException {
        return buffer.getDatasetById(id, userId, workspaceId);
    }

    // DELETE DATASET
    public void deleteDataset(String id, String userId, String workspaceId) throws RecordNotFoundException {
        // fetch the specific Dataset for the user
        Optional<Dataset> datasetOptional = datasetRepository.findByIdAndWorkspaceId(id, workspaceId);
        // if no Dataset details, then send NOT FOUND Error
        if (!datasetOptional.isPresent()) {
            throw new RecordNotFoundException("Error: No such Dataset Id exists!");
        }
        // delete the record from DB
        datasetRepository.deleteById(id);
        // delete from buffer
        buffer.removeDatasetDetailsFromBuffer(id);
    }

    // load dataset details in buffer. This helps faster query execution.
    public DatasetDTO loadDatasetInBuffer(String workspaceId, String dbConnectionId, String datasetId, String userId)
            throws RecordNotFoundException, JsonMappingException, JsonProcessingException, ClassNotFoundException,
            BadRequestException, SQLException {
        DatasetDTO dto = buffer.loadDatasetInBuffer(workspaceId, datasetId, userId);
        if (!dto.getDataSchema().getFilterPanels().isEmpty()) {
            List<FilterPanel> filterPanels = relativeFilterProcessor.processFilterPanels(
                    dto.getDataSchema().getFilterPanels(), userId, dbConnectionId, datasetId, workspaceId,
                    this::relativeFilter);
            dto.getDataSchema().setFilterPanels(filterPanels);
        }
        return dto;
    }

    // RUN QUERY
    public String runQuery(String userId, String dBConnectionId, String datasetId, String workspaceId,
            Boolean isSqlOnly,
            List<Query> queries)
            throws RecordNotFoundException, SQLException, JsonMappingException, JsonProcessingException,
            BadRequestException, ClassNotFoundException, ParseException {

        // get dataset details in buffer
        DatasetDTO ds = loadDatasetInBuffer(workspaceId, dBConnectionId, datasetId, userId);

        String vendorName = "";

        // for DB based datasets, connection id is must
        if (ds.getIsFlatFileData() == false) {
            if (dBConnectionId == null || dBConnectionId.isEmpty()) {
                throw new BadRequestException("Error: DB Connection Id can't be empty!");
            }
            /*
             * load connection details in buffer.
             * create connection pool (if not) and then get vendor name.
             * SQL Dialect will be different based on vendor name
             */
            vendorName = connectionPoolService.getVendorNameFromConnectionPool(dBConnectionId, userId, workspaceId);
            // System.out.println("Dialect *****************" + vendorName);
        }

        for (Query req : queries) {
            // need at least one dim or measure or field for query execution
            if (req.getDimensions().isEmpty() && req.getMeasures().isEmpty() && req.getFields().isEmpty()) {

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Error: At least one Dimension/Measure/Field should be there!");
            }
            // relative Date filter
            // Get the first filter panel from the request
            // Assuming req.getFilterPanels() returns a list of FilterPanel objects
            List<FilterPanel> filterPanels = relativeFilterProcessor.processFilterPanels(req.getFilterPanels(), userId,
                    dBConnectionId, datasetId, workspaceId, this::relativeFilter);
            req.setFilterPanels(filterPanels);
        }
        /* DB based Dataset */
        if (ds.getIsFlatFileData() == false) {
            String query = queryComposer.composeQuery(queries, ds, vendorName);

            // for totals & subtotals only
            for (Query req : queries) {
                if (req.getSubTotal() != null && req.getSubTotal()) {
                    String result = subTotals(query, req, vendorName, ds, isSqlOnly, dBConnectionId, userId);
                    return result;
                }
            }

            logger.info("\n******* QUERY **********\n" + query);
            // when the request is just Raw SQL query Text
            if (isSqlOnly != null && isSqlOnly) {
                return query;
            }
            // when the request is for query result
            else {
                JSONArray jsonArray = connectionPoolService.runQuery(dBConnectionId, userId, query);
                return jsonArray.toString();
            }
        }

        /* Flat file based dataset, create DFs for necessary files used in query */
        // get table Id -> file Id -> file name
        else {

            // get all the table ids used in query
            List<String> tableIds = new ArrayList<String>();
            for (Query req : queries) {
                for (int i = 0; i < req.getDimensions().size(); i++) {
                    tableIds.add(req.getDimensions().get(i).getTableId());
                }
                for (int i = 0; i < req.getMeasures().size(); i++) {
                    tableIds.add(req.getMeasures().get(i).getTableId());
                }
                for (int i = 0; i < req.getFields().size(); i++) {
                    tableIds.add(req.getFields().get(i).getTableId());
                }
                for (int i = 0; i < req.getFilterPanels().size(); i++) {
                    for (int j = 0; j < req.getFilterPanels().get(i).getFilters().size(); j++) {
                        tableIds.add(req.getFilterPanels().get(i).getFilters().get(j).getTableId());
                    }
                }
            }
            // get distinct table ids
            final List<String> uniqueTableIds = tableIds.stream().distinct().collect(Collectors.toList());
            // get all file Ids (which is inside table obj)
            List<Table> tableObjList = ds.getDataSchema().getTables().stream()
                    .filter(table -> uniqueTableIds.contains(table.getId()))
                    .collect(Collectors.toList());

            logger.info("unique table id =======\n" + uniqueTableIds.toString() +
                    "\ntableObjectList ======== \n" + tableObjList.toString());
            // throw error when any requested table id is not in dataset
            if (uniqueTableIds.size() != tableObjList.size()) {
                throw new BadRequestException("Error: some table id is not present in Dataset!");
            }

            // get files names from file ids and load the files as Views
            fileDataService.getFileNameFromFileId(userId, tableObjList, workspaceId);
            // come here
            String query = queryComposer.composeQuery(queries, ds, "duckdb");

            // for totals & subtotals only
            for (Query req : queries) {
                if (req.getSubTotal() != null && req.getSubTotal()) {
                    String result = subTotals(query, req, "duckdb", ds, isSqlOnly, dBConnectionId, userId);
                    return result;
                }
            }

            logger.info("\n******* QUERY **********\n" + query);

            // when the request is just Raw SQL query Text
            if (isSqlOnly != null && isSqlOnly) {
                return query;
            }
            // when the request is for query result
            else {
                // List<JsonNode> jsonNodes = sparkService.runQuery(query);
                JSONArray jsonArray = duckDbService.runQuery(query);
                return jsonArray.toString();
            }
        }

    }

    // fo totals and subtotals
    public String subTotals(String query, Query req, String vendorName, DatasetDTO ds, Boolean isSqlOnly,
            String dBConnectionId, String userId)
            throws JsonMappingException, JsonProcessingException, ClassNotFoundException,
            BadRequestException, RecordNotFoundException, SQLException, ParseException {

        List<String> queryList = new ArrayList<>();
        queryList.add(query);
        queryList.addAll(subTotalsCombination.subTotalCombinationResults(req, vendorName, ds));

        if (Boolean.TRUE.equals(isSqlOnly)) {
            StringBuilder queries = new StringBuilder();
            queries.append("Query for Main:\n").append(queryList.get(0)).append("\n\n\n");

            for (int i = 1; i < queryList.size(); i++) {
                queries.append("Query for SubTotal:\n").append(queryList.get(i)).append("\n\n\n");
            }

            return queries.toString().trim();
        } else {
            List<List<String>> subTotalCombinations = new ArrayList<>();
            List<List<Dimension>> dimensionGroups = new ArrayList<>();

            dimensionGroups.add(req.getDimensions());
            dimensionGroups.addAll(subTotalsCombination.subTotalsCombinations(req));

            for (List<Dimension> dimensions : dimensionGroups) {
                Query dimensionQuery = new Query(dimensions, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                        null);
                QueryClauseFieldListMap qMap = subTotalsCombination.selectClauseSql(dimensionQuery, ds,vendorName);
                List<String> combination = new ArrayList<>(qMap.getGroupByList());
                subTotalCombinations.add(combination);
            }

            JSONArray finalResult = new JSONArray();
            for (int j = 0; j < queryList.size(); j++) {
                String strQuery = queryList.get(j);
                List<String> dimensions = subTotalCombinations.get(j);

                JSONArray queryResult;
                try {
                    if (dBConnectionId != null && !dBConnectionId.isEmpty()) {
                        queryResult = connectionPoolService.runQuery(dBConnectionId, userId, strQuery);
                    } else {
                        queryResult = duckDbService.runQuery(strQuery);
                    }
                } catch (Exception e) {
                    throw new SQLException("Error executing query: " + strQuery, e);
                }

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("combination", dimensions);
                jsonObject.put("result", queryResult);
                finalResult.put(jsonObject);
            }

            return finalResult.toString();
        }
    }

    // Populate filter Options
    public Object filterOptions(String userId, String dBConnectionId, String datasetId, String workspaceId,
            ColumnFilter columnFilter)
            throws RecordNotFoundException, SQLException, JsonProcessingException,
            BadRequestException, ClassNotFoundException {
        // checking for datasetId to perform filter options for dataset filter
        if (datasetId == null || datasetId.isEmpty()) {
            String vendorName = "";
            String query = "";
            JSONArray jsonArray = null;
            if (dBConnectionId == null || dBConnectionId.isEmpty()) {
                query = filterOptionsQueryComposer.composeQuery(columnFilter, null, "duckdb");
                logger.info("\n******* QUERY **********\n" + query);
                // creating table object to send it to create a view with the fileId
                Table table = new Table(columnFilter.getTableId(), columnFilter.getFlatFileId(), null, null, null,
                        columnFilter.getTableId(), null, null, false, null);
                List<Table> tableObjects = new ArrayList<>();
                tableObjects.add(table);
                // calling this to crete a view to run on top of that
                fileDataService.getFileNameFromFileId(userId, tableObjects, workspaceId);
                jsonArray = duckDbService.runQuery(query);
            } else {
                vendorName = connectionPoolService.getVendorNameFromConnectionPool(dBConnectionId, userId, workspaceId);
                query = filterOptionsQueryComposer.composeQuery(columnFilter, null, vendorName);
                logger.info("\n******* QUERY **********\n" + query);
                jsonArray = connectionPoolService.runQuery(dBConnectionId, userId, query);
            }
            return jsonArray;

        }

        else {
            String vendorName = "";
            DatasetDTO ds = loadDatasetInBuffer(workspaceId, dBConnectionId, datasetId, userId);
            // for DB based datasets, connection id is must
            if (ds.getIsFlatFileData() == false) {
                if (dBConnectionId == null || dBConnectionId.isEmpty()) {
                    throw new BadRequestException("Error: DB Connection Id can't be empty!");
                }
                /*
                 * load connection details in buffer.
                 * create connection pool (if not) and then get vendor name.
                 * SQL Dialect will be different based on vendor name
                 */
                vendorName = connectionPoolService.getVendorNameFromConnectionPool(dBConnectionId, userId, workspaceId);
                String query = filterOptionsQueryComposer.composeQuery(columnFilter, ds, vendorName);
                logger.info("\n******* QUERY **********\n" + query);
                JSONArray jsonArray = connectionPoolService.runQuery(dBConnectionId, userId, query);
                return jsonArray;
            }

            /* Flat file based dataset, create DFs for necessary files used in query */
            // get table Id -> file Id -> file name
            else {

                String tableId = columnFilter.getTableId();

                // get all file Ids (which is inside table obj)
                List<Table> tableObjList = ds.getDataSchema().getTables().stream()
                        .filter(table -> table.getId().equals(tableId))
                        .collect(Collectors.toList());

                // throw error when requested table id is not in dataset
                if (tableObjList.size() != 1) {
                    throw new BadRequestException("Error: table id is not present in Dataset!");
                }

                // get files names from file ids and load the files as Views
                fileDataService.getFileNameFromFileId(userId, tableObjList, workspaceId);
                // build query
                String query = filterOptionsQueryComposer.composeQuery(columnFilter, ds, "duckdb");
                logger.info("\n******* QUERY **********\n" + query);
                // List<JsonNode> jsonNodes = sparkService.runQuery(query);
                // return jsonNodes;
                JSONArray jsonArray = duckDbService.runQuery(query);
                return jsonArray.toString();
            }
        }

    }

    public JSONArray relativeFilter(String userId, String dBConnectionId, String datasetId, String workspaceId,
            @Valid RelativeFilterRequest relativeFilter)
            throws RecordNotFoundException, BadRequestException, SQLException, ClassNotFoundException,
            JsonMappingException, JsonProcessingException {

        return connectionPoolService.relativeFilter(userId, dBConnectionId, datasetId, workspaceId, relativeFilter);

    }

    public JSONObject syncFilterOption(String userId, List<Filter> filters, String dBConnectionId, String datasetId,
            String workspaceId)
            throws RecordNotFoundException, SQLException, JsonProcessingException, BadRequestException,
            ClassNotFoundException {

        // Check if the userId is null or empty, and throw an exception if it is invalid
        if (userId == null || userId.isEmpty()) {
            throw new BadRequestException("User ID must not be null or empty");
        }


        // Load the dataset into memory using provided connection and dataset IDs
        DatasetDTO ds = loadDatasetInBuffer(workspaceId, dBConnectionId, datasetId, userId);
        


        // if we have relative filter so we have to preprocess it
        for (Filter filter : filters) {
            if (filter.getRelativeCondition() != null) {
                relativeFilterProcessor.processFilter(filter, userId, dBConnectionId, datasetId, workspaceId,
                        this::relativeFilter);
            }
        }

        // Check if the data is not a flat file and is instead a database connection
        if (ds.getIsFlatFileData() == false && ds.getIsFlatFileData() != null) {
            // Validate that the DB connection ID is not null or empty for database queries
            if (dBConnectionId == null || dBConnectionId.isEmpty()) {
                throw new BadRequestException("Error: DB Connection Id can't be empty!");
            }

            // Retrieve the vendor name from the connection pool based on the DB connection
            // ID and user ID
            String vendorName = connectionPoolService.getVendorNameFromConnectionPool(dBConnectionId, userId,
                    workspaceId);

            // Compose the SQL query specific to the database based on column filters,
            // dataset, and vendor name
            String query = SyncFilterOptionsQueryComposer.composeQuery(filters, ds, vendorName, userId);

            // Validate that the query is not empty or null, and throw an exception if it is
            // invalid
            if ( query == null) {
               return null;
            }

            logger.info("\n******* QUERY **********\n" + query);

            // Execute the query on the database using the connection pool service and
            // return the result as a JSON object
            JSONObject jsonObject = connectionPoolService.runQueryObject(dBConnectionId, userId, query);
            return jsonObject;

        } else {
            // Process the case where the data is stored in flat files

            // Extract all valid, non-null table IDs from the column filters
            List<String> tableIds = filters.stream()
                    .map(Filter::getTableId)
                    .filter(Objects::nonNull)
                    .filter(tableId -> !tableId.isEmpty())
                    .collect(Collectors.toList());

            // Ensure that there are valid table IDs, otherwise throw an exception
            if (tableIds.isEmpty()) {
                throw new BadRequestException("Error: No table ids found in column filters!");
            }

            // Retrieve table objects from the dataset schema that match the provided table
            // IDs
            List<Table> tableObjList = ds.getDataSchema().getTables().stream()
                    .filter(table -> tableIds.contains(table.getId()))
                    .collect(Collectors.toList());

            // Check that at least one matching table object is found in the dataset, else
            // throw an exception
            if (tableObjList.size() < 1) {
                throw new BadRequestException("Error: table id is not present in Dataset!");
            }

            // Load flat files as database views in DuckDB using the file service
            fileDataService.getFileNameFromFileId(userId, tableObjList, workspaceId);

            // Build the query for DuckDB based on the column filters, dataset, and
            // specified 'duckdb' vendor type
            String query = SyncFilterOptionsQueryComposer.composeQuery(filters, ds, "duckdb", userId);

            // Ensure the DuckDB query is not empty or null, and throw an exception if
            // invalid
            if ( query == null) {
                return null;
            }

            logger.info("\n******* QUERY **********\n" + query);

            // Execute the DuckDB query
            return duckDbService.runSyncQuery(query);
        }

    }

    public JSONArray testCalculateField(String userId, String dbConnectionId, String datasetId,
            String workspaceId, List<CalculatedFieldRequest> calculatedFieldRequests, Integer recordCount)
            throws RecordNotFoundException, SQLException, JsonMappingException, JsonProcessingException,
            ClassNotFoundException, BadRequestException {

        String vendorName = !(dbConnectionId == null || dbConnectionId.equals("null") || dbConnectionId.isEmpty())
                ? connectionPoolService.getVendorNameFromConnectionPool(dbConnectionId, userId, workspaceId)
                : "duckdb";

        relativeFilterProcessor.processCalculatedFields(calculatedFieldRequests, userId, dbConnectionId,
                datasetId, workspaceId, this::relativeFilter);

        DatasetDTO ds = loadDatasetInBuffer(workspaceId, dbConnectionId, datasetId, userId);

        String query = CalculatedFieldQueryComposer.composeSampleRecordQuery(ds, vendorName, calculatedFieldRequests,
                ds.getDataSchema(), recordCount);

        logger.info("\n******* QUERY **********\n" + query);
        if (dbConnectionId == null || dbConnectionId.isEmpty()) {

            List<String> tableIds = ColumnListFromClause.getColumnListFromFieldsRequest(calculatedFieldRequests);

            List<Table> tableObjList = ds.getDataSchema().getTables().stream()
                    .filter(table -> tableIds.contains(table.getId()))
                    .collect(Collectors.toList());

            fileDataService.getFileNameFromFileId(userId, tableObjList, workspaceId);

            JSONArray jsonArray = duckDbService.runQuery(query);

            return jsonArray;
        } else {

            JSONArray jsonArray = connectionPoolService.runQuery(dbConnectionId, userId, query);
            return jsonArray;
        }

    }

    public JSONObject calculatedFieldFilterOptions(String userId, String dbConnectionId, String datasetId,
            String workspaceId, List<CalculatedFieldRequest> calculatedFieldRequest)
            throws RecordNotFoundException, SQLException, JsonMappingException, JsonProcessingException,
            ClassNotFoundException, BadRequestException {

        String vendorName = connectionPoolService.getVendorNameFromConnectionPool(dbConnectionId, userId, workspaceId);

        relativeFilterProcessor.processCalculatedFields(calculatedFieldRequest, userId, dbConnectionId, datasetId,
                workspaceId, this::relativeFilter);

        DatasetDTO ds = loadDatasetInBuffer(workspaceId, dbConnectionId, datasetId, userId);

        String query = calculatedFieldQueryComposer.composeFilterOptionsQuery(ds, vendorName, calculatedFieldRequest,
                ds.getDataSchema());

        logger.info("\n******* QUERY **********\n" + query);

        JSONObject jsonObject = connectionPoolService.runQueryObject(dbConnectionId, userId, query);

        return jsonObject;
    }
        // to get relationship between tables
    public List<TableRelationshipResponse> tablesRelationship(String userId, String workspaceId, List<String> tableIds,
            String datasetId)
            throws JsonMappingException, JsonProcessingException, ClassNotFoundException, BadRequestException,
            RecordNotFoundException, SQLException {

        DatasetDTO datasetDTO = buffer.loadDatasetInBuffer(workspaceId, datasetId, userId);

        DataSchema dataSchema = datasetDTO.getDataSchema();

        List<Relationship> relationships = dataSchema.getRelationships();

        List<TableRelationshipResponse> responses = new ArrayList<>();

        for (int i = 0; i < tableIds.size(); i++) {
            for (int j = i + 1; j < tableIds.size(); j++) {
                String table1 = tableIds.get(i);
                String table2 = tableIds.get(j);

                boolean isDirectlyRelated = false;
                String relationType = null;

                for (Relationship relationship : relationships) {
                    if ((relationship.getTable1().equals(table1) && relationship.getTable2().equals(table2))
                            || (relationship.getTable1().equals(table2) && relationship.getTable2().equals(table1))) {
                        isDirectlyRelated = true;
                        relationType = relationship.getCardinality();

                        // Adjust the table order based on the relationship type
                        if (relationType.equalsIgnoreCase("many to one") && relationship.getTable2().equals(table1)) {
                            String temp = table1;
                            table1 = table2;
                            table2 = temp;
                        } else if (relationType.equalsIgnoreCase("one to many")
                                && relationship.getTable1().equals(table2)) {
                            String temp = table1;
                            table1 = table2;
                            table2 = temp;
                        }
                        break;
                    }
                }

                // Create a relationship response
                TableRelationshipResponse response = new TableRelationshipResponse();
                response.setTable1(table1);
                response.setTable2(table2);
                response.setRelationship(isDirectlyRelated ? relationType : null);
                response.setIsDirect(isDirectlyRelated);

                responses.add(response);
            }
        }

        return responses;
    }

}