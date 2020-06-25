package uk.gov.homeoffice.borders.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.delegate.DelegateExecution
import spock.lang.Specification
import uk.gov.homeoffice.borders.workflow.audit.AuditEventListener
import uk.gov.homeoffice.borders.workflow.audit.LogAuditProcessor
import uk.gov.homeoffice.borders.workflow.identity.PlatformUser
import uk.gov.homeoffice.borders.workflow.security.WorkflowAuthentication

class AuditEventListenerSpec extends Specification {

    def objectMapper = Mock(ObjectMapper)
    def logAuditEventListener = new LogAuditProcessor(objectMapper)
    def identityService = Mock(IdentityService)
    def auditEventListener = new AuditEventListener(identityService, [logAuditEventListener])


    def 'can record audit event'() {
        AuditEventListener.AuditEvent auditEvent
        given:
        def user = new PlatformUser()
        user.id ='id'
        user.email = 'email'
        user.teams = []
        def workflowAuthentication = new WorkflowAuthentication(user)
        identityService.getCurrentAuthentication() >> workflowAuthentication
        def execution = Mock(DelegateExecution)

        and:
        execution.getProcessInstanceId() >> 'processInstanceId'
        execution.getProcessBusinessKey() >> 'processBusinessKey'
        execution.getProcessDefinitionId() >> 'processDefinitionId'
        execution.getParentId() >> 'parentId'
        execution.getCurrentActivityId() >> 'currentActivityId'
        execution.getCurrentActivityName() >> 'activityName'
        execution.getCurrentTransitionId() >> 'transitionId'
        execution.getTenantId() >>'tenantId'


        when:
        auditEventListener.notify(execution)

        then:
        1 * objectMapper.writeValueAsString(_) >> { arguments -> auditEvent=arguments[0]}
        auditEvent.processInstanceId == execution.getProcessInstanceId()
        auditEvent.processBusinessKey == execution.getProcessBusinessKey()
        auditEvent.processDefinitionId == execution.getProcessDefinitionId()
        auditEvent.parentId == execution.getParentId()
        auditEvent.currentActivityId ==execution.getCurrentActivityId()
        auditEvent.currentActivityName ==execution.getCurrentActivityName()
        auditEvent.currentTransitionId ==execution.getCurrentTransitionId()
        auditEvent.tenantId == execution.getTenantId()
        auditEvent.executedBy == 'email'
    }

}
