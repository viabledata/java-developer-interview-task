package uk.gov.homeoffice.borders.workflow.pdf

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.model.SendRawEmailResult
import com.github.tomakehurst.wiremock.client.WireMock
import io.findify.s3mock.S3Mock
import io.vavr.Tuple2
import org.camunda.bpm.engine.HistoryService
import org.camunda.bpm.engine.ManagementService
import org.camunda.bpm.engine.OptimisticLockingException
import org.camunda.bpm.engine.RuntimeService

import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.engine.task.Task
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import spock.util.concurrent.PollingConditions
import uk.gov.homeoffice.borders.workflow.BaseSpec
import uk.gov.homeoffice.borders.workflow.process.ProcessApplicationService
import uk.gov.homeoffice.borders.workflow.process.ProcessStartDto

import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.http.Response.response

class PdfServiceSpec extends BaseSpec {

    @Autowired
    AmazonS3 amazonS3Client

    @Autowired
    PdfService pdfService

    @Autowired
    ProcessApplicationService applicationService

    @Autowired
    RuntimeService runtimeService

    @Autowired
    HistoryService historyService

    @Autowired
    AmazonSimpleEmailService emailService

    @Autowired
    ManagementService managementService


    static S3Mock api = new S3Mock.Builder().withPort(8323).withInMemoryBackend().build()


    def setupSpec() {
        if (api != null) {
            api.start()
        }
    }

    def cleanupSpec() {
        if (api != null) {
            api.shutdown()
        }
    }


    def 'can make a request to generate pdf'() {
        def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)

        given:
        amazonS3Client.createBucket("test-cop-case")

        def user = logInUser()
        def token = new TestingAuthenticationToken(user, "test")
        token.setAuthenticated(true)
        SecurityContextHolder.setContext(new SecurityContextImpl(token))

        and:
        amazonS3Client.putObject(new PutObjectRequest("test-cop-case", "BF-20200120-000/formEaB/xx@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        and:
        wireMockStub.stub {
            request {
                method 'POST'
                url '/pdf'
            }
            response {
                status: 200
                headers {
                    "Content-Type" "application/json"
                }
            }
        }


        when:
        def processDto = new ProcessStartDto()
        processDto.setBusinessKey("BF-20200120-000")
        processDto.setProcessKey("generatePDFExample")
        processDto.setVariableName("exampleForm")
        processDto.setData('''{
            "test" : "test",
            "businessKey": "BF-20200120-000",
            "form" : {
               "name" : "formEaB",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "formVersionId": "formVersionId"
            }
        }''')
        Tuple2<ProcessInstance, List<Task>> result = applicationService.createInstance(processDto, user)

        then:
        conditions.eventually {
            def instance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(result._1().id).singleResult()
            assert instance.getEndTime() != null
        }

    }

    def 'raises an incident if pdf request fails'() {
        def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)
        given:
        amazonS3Client.createBucket("test-cop-case")

        def user = logInUser()
        def token = new TestingAuthenticationToken(user, "test")
        token.setAuthenticated(true)
        SecurityContextHolder.setContext(new SecurityContextImpl(token))

        and:
        amazonS3Client.putObject(new PutObjectRequest("test-cop-case", "BF-20200120-000/formEaB/xx@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        and:
        wireMockRule.stubFor(WireMock.post(WireMock.urlMatching("/pdf")).willReturn(
                aResponse().withStatus(500))
        )

        when:
        def processDto = new ProcessStartDto()
        processDto.setBusinessKey("BF-20200120-000")
        processDto.setProcessKey("generatePDFExample")
        processDto.setVariableName("exampleForm")
        processDto.setData('''{
            "test" : "test",
            "businessKey": "BF-20200120-000",
            "form" : {
               "name" : "formEaB",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "versionId": "formVersionId"
            }
        }''')
        Tuple2<ProcessInstance, List<Task>> result = applicationService.createInstance(processDto, user)

        then:
        conditions.eventually {
            def incidents = runtimeService.createIncidentQuery()
            .processInstanceId(result._1().id).list()
            assert incidents.size() != 0
        }
    }

    def 'can email pdf'()  {
        given:
        amazonS3Client.createBucket("pdf-attachments")

        def user = logInUser()
        def token = new TestingAuthenticationToken(user, "test")
        token.setAuthenticated(true)
        SecurityContextHolder.setContext(new SecurityContextImpl(token))

        and:
        amazonS3Client.putObject(new PutObjectRequest("pdf-attachments", "data.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        when:
        def processDto = new ProcessStartDto()
        processDto.setBusinessKey("BF-20200120-000")
        processDto.setProcessKey("sendSES")
        processDto.setVariableName("exampleForm")
        processDto.setData('''{
            "test" : "test",
            "businessKey": "BF-20200120-000",
            "form" : {
               "name" : "formEaB",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "versionId": "formVersionId"
            }
        }''')
        applicationService.createInstance(processDto, user)
        TimeUnit.SECONDS.sleep(5)
        then:
        1 * emailService.sendRawEmail(_) >> new SendRawEmailResult().withMessageId('messageId')

    }

    def 'can raise an incident if failed to send SES'() {
        def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)

        given:
        amazonS3Client.createBucket("pdf-attachments")

        def user = logInUser()
        def token = new TestingAuthenticationToken(user, "test")
        token.setAuthenticated(true)
        SecurityContextHolder.setContext(new SecurityContextImpl(token))

        and:
        amazonS3Client.putObject(new PutObjectRequest("pdf-attachments", "data.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        when:
        def processDto = new ProcessStartDto()
        processDto.setBusinessKey("BF-20200120-000")
        processDto.setProcessKey("sendSES")
        processDto.setVariableName("exampleForm")
        processDto.setData('''{
            "test" : "test",
            "businessKey": "BF-20200120-000",
            "form" : {
               "name" : "formEaB",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "versionId": "formVersionId"
            }
        }''')

        emailService.sendRawEmail(_) >> { throw new RuntimeException("Failed") }
        def instance = applicationService.createInstance(processDto, user)

        then:
        conditions.eventually {
            runtimeService.createIncidentQuery().processInstanceId(instance._1().id).count() > 0
        }
    }
}
