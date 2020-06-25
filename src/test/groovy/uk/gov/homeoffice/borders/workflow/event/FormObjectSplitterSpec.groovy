package uk.gov.homeoffice.borders.workflow.event

import spock.lang.Specification

class FormObjectSplitterSpec extends Specification {
    def formSplitter = new FormObjectSplitter()

    def 'can split a form variable'() {
        given: 'a json string'
        def json = '''{
                       "businessKey": "TEST123",
                        "eventType": {
                            "detection": true
                        },
                        "form": {
                            "name": "testFormName"
                        },
                        "testForm": {
                            "submit": true,
                            "test": "apples",
                            "form": {
                               "name": "testForm"
                            }
                        },
                        "testForm2": {
                            "submit": true,
                             "test": "bananas",
                            "form": {
                                "name": "testForm2"
                            }
                        }
                      }'''

        when: 'split is invoked'
        def forms = formSplitter.split(json)

        then: 'there should be 3 forms'
        forms.size() == 3
    }

    def 'can split a form variable in array'() {
        given: 'a json string'
        def json = '''{
                       "businessKey": "TEST123",
                        "eventType": {
                            "detection": true
                        },
                        "form": {
                            "name": "testFormName"
                        },
                        "forms" : [
                            {"testForm": {
                                "submit": true,
                                "test": "apples",
                                "form": {
                                   "name": "testForm"
                                }
                            }},
                            {"testForm2": {
                                "submit": true,
                                 "test": "bananas",
                                "form": {
                                    "name": "testForm2"
                                }
                            }}
                        ]
                      }'''

        when: 'split is invoked'
        def forms = formSplitter.split(json)

        then: 'there should be 3 forms'
        forms.size() == 3
    }
}
