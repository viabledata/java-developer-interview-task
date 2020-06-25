package uk.gov.homeoffice.borders.workflow.event;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.spin.Spin;
import org.camunda.spin.json.SpinJsonNode;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.StreamSupport;

import static java.lang.String.*;
import static java.lang.String.format;

@Slf4j
public class FormToAWSESUploader {

    private final RestHighLevelClient elasticsearchClient;
    private final RuntimeService runtimeService;


    public FormToAWSESUploader(RestHighLevelClient elasticsearchClient,
                               RuntimeService runtimeService) {
        this.elasticsearchClient = elasticsearchClient;
        this.runtimeService = runtimeService;
    }

    public void upload(String form,
                       String key,
                       HistoricProcessInstance processInstance,
                       String executionId) {


        String indexKey;
        if (processInstance.getBusinessKey() != null && processInstance.getBusinessKey().split("-").length == 3) {
            indexKey = processInstance.getBusinessKey().split("-")[1];
        } else {
            indexKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        IndexRequest indexRequest = new IndexRequest(indexKey).id(key);


        JSONObject indexSource = new JSONObject();
        indexSource.put("businessKey", processInstance.getBusinessKey());

        SpinJsonNode json = Spin.JSON(stringify(new JSONObject(form)).toString());
        String submittedBy = json.jsonPath("$.shiftDetailsContext.email").stringValue();
        String submissionDate = json.jsonPath("$.form.submissionDate").stringValue();
        String formName = json.jsonPath("$.form.name").stringValue();
        String timeStamp = DateTime.parse(submissionDate).toString("YYYYMMDD'T'HHmmss");

        indexSource.put("submissionDate", timeStamp);
        indexSource.put("submittedBy", submittedBy);
        indexSource.put("formName", formName);
        indexSource.put("data", json.toString());

        indexRequest.source(indexSource.toString(), XContentType.JSON);
        try {
            final IndexResponse index = elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT.toBuilder()
                    .addHeader("Content-Type", "application/json").build());
            log.info("Document uploaded result response'{}'", index.getResult().getLowercase());
        } catch (IOException e) {
            log.error("Failed to create a document in ES due to '{}'", e.getMessage());
            runtimeService.createIncident(
                    FormVariableS3PersistListener.FAILED_TO_CREATE_ES_RECORD,
                    executionId,
                    format("Failed to create ES document for %s",
                            processInstance.getBusinessKey()),
                    e.getMessage()
            );
        }
    }

    private JSONObject stringify(JSONObject o) {
        for (String key : o.keySet()) {
            Object json = o.get(key);
            if (json instanceof JSONObject) {
                stringify((JSONObject)json);
            } else if (json instanceof JSONArray) {
                JSONArray array = (JSONArray)json;

                for (int i =0; i < array.length(); i++) {
                    Object aObj = array.get(i);
                    if (aObj instanceof JSONObject) {
                        stringify((JSONObject)aObj);
                    } else {
                        array.put(i, String.valueOf(aObj));
                    }
                }
            } else {
                o.put(key, valueOf(json));
            }
        }
        return o;
    }
}
