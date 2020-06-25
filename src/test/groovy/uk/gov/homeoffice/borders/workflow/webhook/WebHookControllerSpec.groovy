package uk.gov.homeoffice.borders.workflow.webhook

import org.camunda.bpm.engine.HistoryService
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat
import org.camunda.spin.json.SpinJsonNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.homeoffice.borders.workflow.BaseSpec

import static org.camunda.spin.Spin.S
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WebHookControllerSpec extends BaseSpec {

    @Autowired
    private RepositoryService repositoryService

    @Autowired
    private HistoryService historyService

    @Autowired
    private JacksonJsonDataFormat formatter


    def 'can message on web-hook'() {

        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("messageWorkflow")
                .startEvent()
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'Hello Message'")
                .intermediateCatchEvent()
                .message("messageWaiting")
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'After Message'")
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("messageWorkflow.bpmn", modelInstance).deploy()
        def processInstance = runtimeService.startProcessInstanceByKey('messageWorkflow', businessKey)


        when: 'A message web-hook post has been peformed'
        def eventPayload = '''{"event": "pdf-generated",
                          "data": {
                             "location": "http://s3/location/myfile.pdf"
                          }
                        }'''

        def result = mvc.perform(post("/v1/api/workflow/web-hook/${businessKey}/message/messageWaiting?variableName=testVariableMessage")
                .content(eventPayload)
                .contentType(MediaType.APPLICATION_JSON))

        then: 'Response should be succesful'
        result.andExpect(status().is2xxSuccessful())

        and: 'process instance should be completed'
        def history = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult()
        history.state == "COMPLETED"
        history.endTime != null

        and: 'completed process instance should have the event payload as variable'
        def variableInstance = historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstance.id)
                .variableName("testVariableMessage").singleResult()

        SpinJsonNode payloadAsSpin = S(eventPayload, formatter)
        SpinJsonNode variableAsSpin = S(variableInstance.value, formatter)

        variableAsSpin.prop("event").stringValue() == payloadAsSpin.prop('event').stringValue()
        variableAsSpin
                .prop("data")
                .prop("location")
                .stringValue() == payloadAsSpin
                .prop("data")
                .prop("location").stringValue()

    }

    def 'throws bad request if payload is empty'() {
        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("simple")
                .startEvent()
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("simple.bpmn", modelInstance).deploy()
        runtimeService.startProcessInstanceByKey('simple', businessKey)

        when: 'Web hook post with no body'
        def result = mvc.perform(post("/v1/api/workflow/web-hook/${businessKey}/message/messageWaiting?variableName=testVariableMessage")
                .content('')
                .contentType(MediaType.APPLICATION_JSON))

        then: 'Response should be a bad request'
        result.andExpect(status().is4xxClientError())

    }

    def 'throws not found if business key does not relate to a running process instance'() {
        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("messageWorkflow")
                .startEvent()
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'Hello Message'")
                .intermediateCatchEvent()
                .message("messageWaiting")
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'After Message'")
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("messageWorkflow.bpmn", modelInstance).deploy()
        runtimeService.startProcessInstanceByKey('messageWorkflow', businessKey)

        when: 'A message web-hook post has been peformed'
        def eventPayload = '''{"event": "pdf-generated",
                          "data": {
                             "location": "http://s3/location/myfile.pdf"
                          }
                        }'''
        def result = mvc.perform(post("/v1/api/workflow/web-hook/invalidBusinessKey/message/messageWaiting?variableName=testVariableMessage")
                .content(eventPayload)
                .contentType(MediaType.APPLICATION_JSON))

        then: 'Response should be 404'
        result.andExpect(status().isNotFound())
    }


}
