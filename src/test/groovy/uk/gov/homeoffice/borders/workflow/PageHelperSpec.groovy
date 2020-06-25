package uk.gov.homeoffice.borders.workflow

import org.springframework.data.domain.PageRequest
import spock.lang.Specification

class PageHelperSpec extends Specification {

    def pageHelper = new PageHelper()

    def 'returns 0 is page number is 0'() {
        given:
        def pageable = PageRequest.of(0,1)

        when:
        def result = pageHelper.calculatePageNumber(pageable)

        then:
        result == 0
    }

    def 'returns non zero page'() {
        given:
        def pageable = PageRequest.of(1,20)

        when:
        def result = pageHelper.calculatePageNumber(pageable)

        then:
        result == 20
    }
}
