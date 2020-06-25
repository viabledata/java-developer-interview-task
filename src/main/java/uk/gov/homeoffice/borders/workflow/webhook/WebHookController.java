package uk.gov.homeoffice.borders.workflow.webhook;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import uk.gov.homeoffice.borders.workflow.exception.ResourceNotFound;

import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static org.camunda.spin.Spin.S;

@RestController
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
@RequestMapping(path = "/v1/api/workflow/web-hook")
@Api(value = "Web hook endpoint for triggering a message for a given process instance")
public class WebHookController {

    private RuntimeService runtimeService;
    private JacksonJsonDataFormat formatter;

    @Deprecated(forRemoval = true,
            since = "As business keys are not unique between process instances " +
                    "you need to use the process instance to signal the BPMN")
    @PostMapping(value = "/{businessKey}/message/{messageKey}", consumes = "application/json")
    @ApiOperation(value = "Web hook notification endpoint. Converts payload into a Spin object for Camunda",
            httpMethod = "POST")
    public void handleBusinessKeyMessage(@PathVariable
                                         @ApiParam(required = true,
                                                 name = "businessKey",
                                                 value = "Unique reference for a given process instance." +
                                                         "Note the businessKey is not the same as the process instance id")
                                                 String businessKey,
                                         @PathVariable
                                         @ApiParam(required = true,
                                                 value = "The message that is defined in the process definition",
                                                 name = "messageKey") String messageKey,
                                         @RequestParam()
                                         @ApiParam(required = true,
                                                 value = "Variable name that is required by Camunda." +
                                                         " The payload is wrapped with the variable name",
                                                 name = "variableName") String variableName,
                                         @RequestBody @NotEmpty()
                                         @ApiParam(required = true,
                                                 name = "payload",
                                                 value = "The event as JSON string") String payload) {

        log.info("Received web-hook message notification for '{}' and message key '{}'", businessKey, messageKey);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult();

        if (processInstance == null) {
            throw new ResourceNotFound(format("Process instance with business key '%s' does not exist", businessKey));
        }

        correlate(processInstance, messageKey, Collections.singletonMap(variableName, S(payload, formatter)));
    }

    @PostMapping(value = "/processInstance/{processInstanceId}/message/{messageKey}", consumes = "application/json")
    @ApiOperation(value = "Web hook notification endpoint. Converts payload into a Spin object for Camunda",
            httpMethod = "POST")
    public void handleProcessInstanceMessage(@PathVariable
                                             @ApiParam(required = true,
                                                     name = "processInstanceId",
                                                     value = "Unique reference for a given process instance." +
                                                             "Note the businessKey is not the same as the process instance id")
                                                     String processInstanceId,
                                             @PathVariable
                                             @ApiParam(required = true,
                                                     value = "The message that is defined in the process definition",
                                                     name = "messageKey") String messageKey,
                                             @RequestParam()
                                             @ApiParam(required = true,
                                                     value = "Variable name that is required by Camunda." +
                                                             " The payload is wrapped with the variable name",
                                                     name = "variableName") String variableName,
                                             @RequestBody @NotEmpty()
                                             @ApiParam(required = true,
                                                     name = "payload",
                                                     value = "The event as JSON string") String payload) {

        log.info("Received web-hook message notification for '{}' and message key '{}'", processInstanceId, messageKey);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            throw new ResourceNotFound(format("Process instance with processInstanceId id '%s' does not exist", processInstanceId));
        }

        correlate(processInstance, messageKey, Collections.singletonMap(variableName, S(payload, formatter)));

    }


    private void correlate(ProcessInstance processInstance, String messageKey, Map<String, Object> variables) {
        MessageCorrelationResult result = runtimeService
                .createMessageCorrelation(messageKey)
                .processInstanceId(processInstance.getProcessInstanceId())
                .setVariables(variables).correlateWithResult();
        log.info("Performed web-hook message correlation for {} with key {} and result {}", processInstance.getId(), messageKey,
                result.getResultType());

    }
}
