package uk.gov.homeoffice.borders.workflow

import org.camunda.bpm.engine.IdentityService
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification
import uk.gov.homeoffice.borders.workflow.exception.ForbiddenException
import uk.gov.homeoffice.borders.workflow.identity.PlatformUser
import uk.gov.homeoffice.borders.workflow.shift.ShiftUserMethodArgumentResolver
import uk.gov.homeoffice.borders.workflow.security.WorkflowAuthentication

class PlatformUserMethodArgumentResolverSpec extends Specification {

    def identityService = Mock(IdentityService)

    def shiftUserMethodArgumentResolver = new ShiftUserMethodArgumentResolver(identityService)

    def 'can get user'() {
        given:
        def user = new PlatformUser()
        user.id ='id'
        user.email = 'email'
        user.teams = []
        def workflowAuthentication = new WorkflowAuthentication(user)
        identityService.getCurrentAuthentication() >> workflowAuthentication

        when:
        def result = shiftUserMethodArgumentResolver.resolveArgument(Mock(MethodParameter),Mock(ModelAndViewContainer),Mock(NativeWebRequest),Mock(WebDataBinderFactory))

        then:
        result
        result == user
    }

    def 'exception thrown if no user found'() {
        given:
        def workflowAuthentication = new WorkflowAuthentication("test", [])
        identityService.getCurrentAuthentication() >> workflowAuthentication

        when:
        shiftUserMethodArgumentResolver.resolveArgument(Mock(MethodParameter),Mock(ModelAndViewContainer),Mock(NativeWebRequest),Mock(WebDataBinderFactory))

        then:
        thrown ForbiddenException
    }

    def 'exception thrown if no authentication found'() {
        given:
        identityService.getCurrentAuthentication() >> null

        when:
        shiftUserMethodArgumentResolver.resolveArgument(Mock(MethodParameter),Mock(ModelAndViewContainer),Mock(NativeWebRequest),Mock(WebDataBinderFactory))

        then:
        thrown ForbiddenException
    }
}
