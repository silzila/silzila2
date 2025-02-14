package com.silzila.querybuilder.syncFilterOption;


import com.silzila.dto.DatasetDTO;
import com.silzila.exception.BadRequestException;
import com.silzila.payload.request.ColumnFilter;
import com.silzila.payload.request.Filter;
import com.silzila.querybuilder.RelationshipClauseGeneric;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SyncFilterOptionsQueryComposer {

    private static final Logger logger = LogManager.getLogger(SyncFilterOptionsQueryComposer.class);

    // Main method to compose the query based on column filters, dataset, vendor name, and user ID
    public static String composeQuery(List<Filter> cf, DatasetDTO ds, String vendorName, String userId) throws BadRequestException {
        logger.info("----------- SyncFilterOptionsQueryComposer calling......");

        // Check if column filters list is null or empty, and throw an exception if it is
        if (cf == null || cf.isEmpty()) {
            throw new BadRequestException("Column filters cannot be null or empty.");
        }

        String finalQuery;

        // Retrieve all table IDs from column filters
        List<String> allIds = allTableIds(cf);

        // Check if the vendor is DuckDB, which is used for flat file processing
        if (vendorName.equals("duckdb")) {
            logger.info("------ inside DuckDB block for flat file processing");

            // Build the 'FROM'
            String fromQuery = RelationshipClauseGeneric.buildRelationship(allIds, ds.getDataSchema(), vendorName);

            // Build the final query
            finalQuery = SyncFilterQuery.getSyncFilterOptions(cf, fromQuery, vendorName,ds);

            // Validate the query, ensuring it is not  null
            if (finalQuery == null ) {
                return null;
            }

        } else {
            // For other database vendors, generate the 'FROM' part of the query
            String fromQuery = RelationshipClauseGeneric.buildRelationship(allIds, ds.getDataSchema(), vendorName);

            // Check if the 'FROM' query part is valid
            if (fromQuery == null || fromQuery.isEmpty()) {
                throw new BadRequestException("No Tables found for vendor " + vendorName);
            }

            // Check the vendor type and build the final query accordingly
            if (vendorName!=null||!vendorName.isEmpty() ) {

                finalQuery = SyncFilterQuery.getSyncFilterOptions(cf, fromQuery, vendorName,ds);
                if (finalQuery==null) {
                    return null;
                }
            } else {
                // Throw an exception if the vendor name does not match any known types
                throw new BadRequestException("Error: DB vendor Name is wrong!");
            }
        }

        return finalQuery;
    }

    // Method to retrieve all unique table IDs from the list of column filters
    public static List<String> allTableIds(List<Filter> cf) {
        if (cf.isEmpty()) return new ArrayList<>();

        Set<String> allTableIds = new HashSet<>();
        for (Filter c : cf) {
            allTableIds.add(c.getTableId());
        }
        List<String> listOfIds = new ArrayList<>(allTableIds);
        return listOfIds;
    }
}
