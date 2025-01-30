package com.silzila.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silzila.domain.entity.Dataset;
import com.silzila.domain.entity.FileData;
import com.silzila.dto.DatasetDTO;
import com.silzila.exception.BadRequestException;
import com.silzila.exception.RecordNotFoundException;
import com.silzila.payload.request.DataSchema;
import com.silzila.repository.DatasetRepository;
import com.silzila.repository.FileDataRepository;

@Component
public class DatasetAndFileDataBuffer {

    @Autowired
    DatasetRepository datasetRepository;

    @Autowired
    FileDataRepository fileDataRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    private static Map<String, DatasetDTO> datasetDetails = new HashMap<>();

    public static HashMap<String, List<FileData>> usersFileDatas = new HashMap<String, List<FileData>>();

    public DatasetDTO loadDatasetInBuffer(String workspaceId, String datasetId, String userId)
            throws RecordNotFoundException, JsonMappingException, JsonProcessingException, ClassNotFoundException,
            BadRequestException, SQLException {
        DatasetDTO dto;
        if (datasetDetails.containsKey(datasetId)) {
            dto = datasetDetails.get(datasetId);
        } else {
            dto = getDatasetById(datasetId, userId, workspaceId);
            datasetDetails.put(datasetId, dto);
        }
        return dto;
    }

    public DatasetDTO getDatasetById(String id, String userId, String workspaceId)
            throws RecordNotFoundException, JsonMappingException, JsonProcessingException {
        Optional<Dataset> dOptional = datasetRepository.findById(id);
        // if no connection details inside optional warpper, then send NOT FOUND Error
        if (!dOptional.isPresent()) {
            throw new RecordNotFoundException("Error: No such Dataset Id exists!");
        }
        // get object from optional wrapper object
        Dataset dataset = dOptional.get();
        // initialize dataschema object and add put key values
        DataSchema dataSchema;
        // dto object holds final response with de-serialized data schema
        DatasetDTO dto = new DatasetDTO();
        try {
            // de-serialization
            dataSchema = objectMapper.readValue(dataset.getDataSchema(), DataSchema.class);
            // populating dto object
            dto.setId(dataset.getId());
            dto.setConnectionId(dataset.getConnectionId());
            dto.setDatasetName(dataset.getDatasetName());
            dto.setIsFlatFileData(dataset.getIsFlatFileData());
            dto.setDataSchema(dataSchema);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Error: Dataset schema could not be serialized!");
        }
        return dto;
    }

    public Map<String, DatasetDTO> getDatasetDetails() {
        return datasetDetails;
    }

    public void addDatasetInBuffer(String datasetId, DatasetDTO datasetDTO) {
        datasetDetails.put(datasetId, datasetDTO);
    }

    public DatasetDTO getDatasetDetailsById(String datasetId) {
        return (datasetDetails.keySet().contains(datasetId)) ? datasetDetails.get(datasetId) : null;
    }

    public void removeDatasetDetailsFromBuffer(String datasetId) {
        datasetDetails.remove(datasetId);
    }

    public List<FileData> getFileDataByUserId(String userId) {

        List<FileData> fileDataList;
        if (usersFileDatas.containsKey(userId)) {
            fileDataList = usersFileDatas.get(userId);
        } else {
            fileDataList = fileDataRepository.findByUserId(userId);
            usersFileDatas.put(userId, fileDataList);
        }

        return fileDataList;

    }

    public void removeFileDataFromBuffer(String userId) {
        usersFileDatas.remove(userId);
    }

    public void addFileDataUser(String userId, FileData fileData) {
        if (usersFileDatas.containsKey(userId)) {
            List<FileData> fileDataList = usersFileDatas.get(userId);
            fileDataList.add(fileData);
        }

        System.out.println(usersFileDatas);
    }

    public void updateFileDataUser(String userId, FileData fileData) {
        if (usersFileDatas.containsKey(userId)) {
            List<FileData> fileDataList = usersFileDatas.get(userId);
            for (int i = 0; i < fileDataList.size(); i++) {
                if (fileDataList.get(i).getId().equals(fileData.getId())) {
                    fileDataList.set(i, fileData);
                    return;
                }
            }
        }
    }

    public void deleteFileDataUser(String userId, String id) {
        if (usersFileDatas.containsKey(userId)) {
            List<FileData> fileDataList = usersFileDatas.get(userId);
            fileDataList.removeIf(fd -> fd.getId().equals(id));
        }
    }

}
