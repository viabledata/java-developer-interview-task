package uk.gov.homeoffice.borders.workflow;

import org.springframework.data.domain.Pageable;

public class PageHelper {

    public int calculatePageNumber(Pageable pageable) {
        if (pageable.getPageNumber() == 0) {
            return 0;
        }
        return pageable.getPageNumber() * pageable.getPageSize();
    }
}
