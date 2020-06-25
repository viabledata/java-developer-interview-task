package uk.gov.homeoffice.borders.workflow

import spock.lang.Specification
import uk.gov.homeoffice.borders.workflow.exception.ForbiddenException
import uk.gov.homeoffice.borders.workflow.exception.GlobalExceptionHandler
import uk.gov.homeoffice.borders.workflow.exception.ResourceNotFound

class GlobalExceptionHandlerSpec extends Specification {

    GlobalExceptionHandler underTest = new GlobalExceptionHandler()


    def 'can handle resource not found exception'() {
        given:
        def notFound = new ResourceNotFound("Not found")

        when:
        def result = underTest.handleResourceNotFoundException(notFound)

        then:
        result.code == 404
        result.message == 'Not found'
    }
    def 'can handle forbidden exception'() {
        given:
        def forbidden = new ForbiddenException("Not allowed")

        when:
        def result = underTest.handleForbiddenException(forbidden)

        then:
        result.code == 403
        result.message == 'Not allowed'
    }

    def 'can handle illegal exception'() {
        given:
        def illegal = new IllegalArgumentException("Illegal")

        when:
        def result = underTest.handIllegalArgumentException(illegal)

        then:
        result.code == 400
        result.message == 'Illegal'
    }

    def 'can handle exception'() {
        given:
        def exception = new Exception("server problem")

        when:
        def result = underTest.handleException(exception)

        then:
        result.code == 500
        result.message == 'server problem'
    }
}
