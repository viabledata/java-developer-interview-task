package uk.gov.homeoffice.borders.workflow.event

import com.amazonaws.auth.AWS4Signer
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.apache.http.HttpHost
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.history.HistoricProcessInstance
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.client.RestHighLevelClient
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification
import uk.gov.homeoffice.borders.workflow.cases.AWSRequestSigningApacheInterceptor

import static com.github.tomakehurst.wiremock.client.WireMock.*

class FormToAWSESUploaderSpec extends Specification {

    def static wmPort = 8010

    @ClassRule
    @Shared
    WireMockRule wireMockRule = new WireMockRule(wmPort)

    private FormToAWSESUploader uploader
    private RestHighLevelClient restHighLevelClient
    private RuntimeService runtimeService

    def setup() {
        runtimeService = Mock()
        AWSCredentials credentials = new BasicAWSCredentials('accessKey','secretAccessKey')
        final AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        AWS4Signer signer = new AWS4Signer()
        signer.setRegionName('eu-west-2')
        signer.setServiceName('es');


        RestHighLevelClient restHighLevelClient = new RestHighLevelClient(
                RestClient.builder(new HttpHost(
                        '127.0.0.1', 8010, 'http'
                )).setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder
                                .addInterceptorFirst(new AWSRequestSigningApacheInterceptor('es',
                                        signer, credentialsProvider)
                                )
                    }
                }))


        uploader = new FormToAWSESUploader(restHighLevelClient, runtimeService)
    }


    def cleanup() {
        if (restHighLevelClient != null) {
            restHighLevelClient.close()
        }
    }

    def 'can upload to ES'() {
        given: 'form data'
        def form = '''{
                        "test": "test" ,
                        "conversionRate": 1,
                          "conversionRate2": "1.00",
                          "conversionRate3" : 2.400,
                          "nested": {
                             "conversionRate3" : 2.400
                          },
                          "array2": [134, 1.00, 233.33],
                          "array": [
                          {
                              "conversionRate3" : 2.400
                          }
                          ],
                          "shiftDetailsContext" : {
                            "email" : "test"
                          },
                        "form": {
                          "name": "testEaB",
                          "submissionDate" : "2020-04-16T07:38:19.384Z"
                        }
                      }'''

        HistoricProcessInstance processInstance = Mock()
        processInstance.getBusinessKey() >> 'DEV-20200804-2222'

        and:
        stubFor(put("/20200804/_doc/%2FDEV-20200804-2222%2FtestForm%2Femail%2F29129121.json?timeout=1m")
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson('''
                                            {
                                                  "submittedBy": "test",
                                                  "data": "{\\"test\\":\\"test\\",\\"shiftDetailsContext\\":{\\"email\\":\\"test\\"},\\"form\\":{\\"name\\":\\"testEaB\\",\\"submissionDate\\":\\"2020-04-16T07:38:19.384Z\\"},\\"array\\":[{\\"conversionRate3\\":\\"2.4\\"}],\\"conversionRate3\\":\\"2.4\\",\\"array2\\":[\\"134\\",\\"1.0\\",\\"233.33\\"],\\"conversionRate2\\":\\"1.00\\",\\"conversionRate\\":\\"1\\",\\"nested\\":{\\"conversionRate3\\":\\"2.4\\"}}",
                                                  "formName": "testEaB",
                                                  "businessKey": "DEV-20200804-2222",
                                                  "submissionDate": "202004107T073819"
                                                }

                                            ''', true, true))

                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                        {
                                          "_index" : "businessKey",
                                          "_type" : "_doc",
                                          "_id" : "/DEV-20200804-2222/testForm/email/29129121.json",
                                          "_version" : 1,
                                          "result" : "created",
                                          "_shards" : {
                                            "total" : 2,
                                            "successful" : 2,
                                            "failed" : 0
                                          },
                                          "_seq_no" : 26,
                                          "_primary_term" : 4
                                        }
                                        """)))

        when:
        uploader.upload(form, "/DEV-20200804-2222/testForm/email/29129121.json", processInstance, 'executionId')

        then:
        true
    }
}
