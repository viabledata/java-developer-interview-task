package uk.gov.homeoffice.borders.workflow.event;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.spin.Spin;
import org.camunda.spin.json.SpinJsonNode;
import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static java.lang.String.format;

@Slf4j
public class FormToS3Uploader {

    private RuntimeService runtimeService;
    private AmazonS3 amazonS3;

    public FormToS3Uploader(RuntimeService runtimeService, AmazonS3 amazonS3) {
        this.runtimeService = runtimeService;
        this.amazonS3 = amazonS3;

    }

    public String upload(String form,
                                  HistoricProcessInstance processInstance,
                                  String executionId,
                                  String product) {


        File scratchFile = null;
        String formName = "";
               try {
            String businessKey = processInstance.getBusinessKey();
            SpinJsonNode json = Spin.JSON(form);
            String submittedBy = json.jsonPath("$.shiftDetailsContext.email").stringValue();
            formName = json.jsonPath("$.form.name").stringValue();
            String formVersionId = json.jsonPath("$.form.formVersionId").stringValue();
            String title = json.jsonPath("$.form.title").stringValue();
            String submissionDate = json.jsonPath("$.form.submissionDate").stringValue();

            final String key = key(businessKey, formName, submittedBy, submissionDate);

            boolean dataExists = amazonS3.doesObjectExist(product, key);
            if (!dataExists) {
                scratchFile
                        = File.createTempFile(UUID.randomUUID().toString(), ".json");
                FileUtils.copyInputStreamToFile(IOUtils.toInputStream(form, "UTF-8"), scratchFile);

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.addUserMetadata("processinstanceid", processInstance.getId());
                metadata.addUserMetadata("processdefinitionid", processInstance.getProcessDefinitionId());
                metadata.addUserMetadata("formversionid", formVersionId);
                metadata.addUserMetadata("name", formName);
                metadata.addUserMetadata("title", title);
                metadata.addUserMetadata("submittedby", submittedBy);
                metadata.addUserMetadata("submissiondate", submissionDate);

                PutObjectRequest request = new PutObjectRequest(product, key, scratchFile);
                request.setMetadata(metadata);
                final PutObjectResult putObjectResult = amazonS3.putObject(request);
                log.debug("Uploaded to S3 '{}'", putObjectResult.getETag());
                return key;
            } else {
                log.info("Key already exists...so not uploading");
                return null;
            }
        } catch (IOException | AmazonServiceException e) {
            log.error("Failed to upload to S3 due to '{}'", e.getMessage());
            runtimeService.createIncident(
                    FormVariableS3PersistListener.FAILED_TO_CREATE_S3_RECORD,
                    executionId,
                    format("Failed to upload form data for %s", formName),
                    e.getMessage()

            );

        } finally {
            if (scratchFile != null && scratchFile.exists()) {
                scratchFile.delete();
            }
        }
        return null;
    }

    public static String key(String businessKey, String formName, String email, String submissionDate) {
        StringBuilder keyBuilder = new StringBuilder();
        String timeStamp = DateTime.parse(submissionDate).toString("YYYYMMDD'T'HHmmss");

        return keyBuilder.append(businessKey)
                .append("/").append(formName).append("/").append(email).append("-").append(timeStamp).append(".json")
                .toString();

    }

}
