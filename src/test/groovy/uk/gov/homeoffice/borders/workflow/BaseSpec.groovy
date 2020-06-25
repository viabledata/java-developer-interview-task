package uk.gov.homeoffice.borders.workflow

import com.amazonaws.auth.AWS4Signer
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import com.google.common.base.Supplier
import org.apache.http.HttpHost
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.camunda.bpm.engine.AuthorizationService
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.TaskService
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.client.RestHighLevelClient
import org.junit.ClassRule
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.*
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.mock.DetachedMockFactory
import spock.mock.MockingApi
import uk.gov.homeoffice.borders.workflow.cases.AWSRequestSigningApacheInterceptor
import uk.gov.homeoffice.borders.workflow.config.CorrelationIdInterceptor
import uk.gov.homeoffice.borders.workflow.identity.PlatformUser
import uk.gov.homeoffice.borders.workflow.identity.Team
import uk.gov.homeoffice.borders.workflow.security.WorkflowAuthentication
import uk.gov.service.notify.NotificationClient
import static com.github.tomakehurst.wiremock.client.WireMock.*


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ['keycloak.enabled=false', 'spring.datasource.name=testdbB', 'spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration'])
@ActiveProfiles('test,local')
@AutoConfigureMockMvc
@TestPropertySource(properties = [
        'keycloak.auth-server-url=http://localhost:9000/auth',
        'keycloak.public-client=false',
        'keycloak.realm=myRealm',
        'keycloak.resource=client_id',
        'keycloak.bearer-only=true',
        'keycloak.ssl-required=external',
        'keycloak.use-resource-role-mappings=true',
        'keycloak.principal-attribute=preferred_username',
        'keycloak.enable-basic-auth=true',
        'keycloak.credentials.secret=very_secret',
        'camunda.bpm.process-engine-name=borders',
        'camunda.bpm.database.type=h2',
        'spring.datasource.driver-class-name=org.h2.Driver',
        'spring.datasource.password=',
        'spring.datasource.username=sa',
        'spring.datasource.url=jdbc:h2:mem:testdbB;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false',
        'logging.level.org.springframework.web.socket=ERROR',
        'server.port=8080',
        'encryption.passphrase=secret',
        'encryption.salt=a9v5n38s',
        'redis.url=localhost',
        'redis.port=6379',
        'redis.token=token',
        'public-ui.cop-url=http://localhost:8080',
        'public-ui.protocol=http://',
        'public-ui.text-protocol=awb://',
        'cors.origins=*',
        'api-ref.url=http://localhost:8000',
        'form-api.url=http://localhost:8000',
        'api-cop.url=http://localhost:8000',
        'api-cop.connect-timeout=5000',
        'api-cop.read-timeout=5000',
        'aws.region=eu-west-2',
        'aws.case-bucket-name=test-events',
        'aws.bucket-name-prefix=test',
        'aws.credentials.access-key=accessKey',
        'aws.credentials.secret-key=secretAccessKeyo',
        'gov.notify.api.key=XXXX',
        'gov.notify.api.notification.email-template-id=XXXX',
        'gov.notify.api.notification.sms-template-id=XXXX',
        'gov.notify.template.enhance.mah-fast-parcel=a6dddd77-2dac-4e84-9fb7-8b1a8b44c6ce',
        'gov.notify.template.enhance.bfnih=a7c8bfea-f0bf-468d-8221-82ec3e1cba14',
        'gov.notify.template.enhance.intel-referral=ad1f65c9-be3e-460e-9554-9f23a24859f3',
        'gov.notify.template.role.review=dbcfb612-a56e-4bab-ad4a-8f7df2cdb1de',
        'gov.notify.template.role.rejected=9debbbd8-810d-47ae-98c9-0254eefe3cf8',
        'gov.notify.template.role.approved=aa377bde-6182-4a68-b420-bea8ffbfd620',
        'gov.notify.template.cash=70053053-04f4-4038-944a-b332e15a8b93',
        'gov.notify.template.ien=92d322b7-0587-4b50-bd92-bb6b1456dcd7',
        'gov.notify.template.sams=4862f857-deb2-4071-b637-e6cbdd26b9b3',
        'gov.notify.template.national-security=742019ac-11b4-470b-9e2f-194f3d486d53',
        'gov.notify.template.national-security-outcomes=8003ec6c-d68d-49d6-83e6-1c2790d6a676',
        'gov.notify.emails.enhance.bfnih=test@localhost.com',
        'gov.notify.emails.enhance.mah-fast-parcel=test@localhost.com',
        'gov.notify.emails.sams=test@localhost.com',
        'gov.notify.emails.national-security=test@localhost.com',
        'pdf.generator.aws.s3.pdf.bucketname=pdf-attachments',
        'teams.enhance.bfnih=TEST',
        'teams.enhance.mah-fast-parcel=TEST'])
abstract class BaseSpec extends Specification {

    @Autowired
    public MockMvc mvc

    @Autowired
    public ObjectMapper objectMapper

    @Autowired
    public RuntimeService runtimeService

    @Autowired
    public TaskService taskService

    @Autowired
    public NotificationClient notificationClient

    @Autowired
    public IdentityService identityService

    @Autowired
    public AuthorizationService authorizationService;


    def static wmPort = 8000

    @ClassRule
    @Shared
    WireMockRule wireMockRule = new WireMockRule(wmPort)

    public wireMockStub = new WireMockGroovy(wmPort)


    def stubKeycloak() {
        stubFor(post('/realms/myRealm/protocol/openid-connect/token')
                .withHeader('Content-Type', equalTo('application/x-www-form-urlencoded;charset=UTF-8'))
                .withHeader('Authorization', equalTo('Basic Y2xpZW50X2lkOnZlcnlfc2VjcmV0'))
                .withRequestBody(equalTo('grant_type=client_credentials'))
                .willReturn(aResponse()
                .withHeader('Content-Type', 'application/json')
                .withBody("""
                                        {
                                            'access_token': 'MY_SECRET_TOKEN'
                                        }
                                        """)))
    }


    def setup() {
        stubKeycloak()

    }

    def cleanup() {
        def instances = runtimeService.createProcessInstanceQuery().list() as ProcessInstance[]
        instances.each {
            it ->
                it.processInstanceId
                if (runtimeService.createProcessInstanceQuery().processInstanceId(it.processInstanceId).count() != 0) {
                    runtimeService.deleteProcessInstance(it.processInstanceId, 'testclean', false, true)
                }
        }

    }

    PlatformUser logInUser() {
        def user = new PlatformUser()
        user.id = 'test'
        user.email = 'test'

        def shift = new PlatformUser.ShiftDetails()
        shift.roles = ['custom_role']
        user.shiftDetails = shift

        def team = new Team()
        user.teams = []
        team.code = 'teamA'
        user.teams << team
        user.roles = ['custom_role']
        identityService.getCurrentAuthentication() >> new WorkflowAuthentication(user)
        user
    }

    @lombok.Data
    class Data {
        String assignee
        String candidateGroup
        String name
        String description
        String candidateUser
        String form

    }

    @Configuration
    static class StubConfig {
        def detachedMockFactory = new DetachedMockFactory()


        @Bean
        MockPostProcessor mockPostProcessor() {
            new MockPostProcessor(
                    [
                            'identityService'   : detachedMockFactory.Stub(IdentityService),
                            'notificationClient': detachedMockFactory.Mock(NotificationClient)
                    ]
            )
        }
    }

    static class MockPostProcessor implements BeanFactoryPostProcessor {
        private final Map<String, Object> mocks

        MockPostProcessor(Map<String, Object> mocks) {
            this.mocks = mocks
        }

        void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            mocks.each { name, mock ->
                beanFactory.registerSingleton(name, mock)
            }
        }
    }

    @Configuration
    @EnableGlobalMethodSecurity(
            prePostEnabled = true
    )
    static class TestConfig extends WebSecurityConfigurerAdapter {


        @Bean
        @Primary
        AmazonSimpleEmailService amazonSimpleEmailService() {
            return  new DetachedMockFactory().Mock(AmazonSimpleEmailService)
        }

        @Bean
        @Primary
        AmazonS3 awsS3Client() {
            final BasicAWSCredentials credentials = new BasicAWSCredentials('accessKey', 'secretAccessKey')

            def amazonS3 = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration('http://localhost:8323',
                    'eu-west-2'))
                    .enablePathStyleAccess()
                    .build()

            return amazonS3
        }


        @Bean(destroyMethod = 'close')
        @Primary
        RestHighLevelClient client() {
            AWSCredentials credentials = new BasicAWSCredentials('accessKey','secretAccessKey')
            final AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
            AWS4Signer signer = new AWS4Signer()
            signer.setRegionName('eu-west-2')
            signer.setServiceName('es');


            RestHighLevelClient restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(
                            '127.0.0.1', 8000, 'http'
                    )).setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder
                                    .addInterceptorFirst(new AWSRequestSigningApacheInterceptor('es',
                                            signer, credentialsProvider)
                                    )
                        }
                    }))
            return restHighLevelClient;
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        @Primary
        RestTemplate keycloakRestTemplate(RestTemplateBuilder builder, CorrelationIdInterceptor interceptor) {
            final RestTemplate restTemplate = builder.build()
            restTemplate.getInterceptors().add(interceptor)
            return restTemplate
        }


        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.csrf().disable().cors().and().authorizeRequests().anyRequest().permitAll()
        }
    }
}
