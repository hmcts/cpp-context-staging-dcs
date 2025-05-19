package uk.gov.moj.cpp.staging.dcs.healthchecks;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.healthcheck.healthchecks.EventStoreHealthcheck.EVENT_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.ViewStoreHealthcheck.VIEW_STORE_HEALTHCHECK_NAME;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class StagingDcsIgnoredHealthcheckNamesProviderTest {


    @InjectMocks
    private StagingDcsIgnoredHealthcheckNamesProvider stagingDcsIgnoredHealthcheckNamesProvider;

    @Test
    void shouldIgnoreEventStoreAndViewStoreHealthchecks() throws Exception {

        final List<String> namesOfIgnoredHealthChecks = stagingDcsIgnoredHealthcheckNamesProvider.getNamesOfIgnoredHealthChecks();

        assertThat(namesOfIgnoredHealthChecks.size(), is(2));
        assertThat(namesOfIgnoredHealthChecks, hasItems(EVENT_STORE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(VIEW_STORE_HEALTHCHECK_NAME));
    }
}