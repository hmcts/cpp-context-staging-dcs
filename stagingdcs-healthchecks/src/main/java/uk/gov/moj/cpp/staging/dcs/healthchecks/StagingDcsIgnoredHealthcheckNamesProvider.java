package uk.gov.moj.cpp.staging.dcs.healthchecks;

import static java.util.Arrays.asList;
import static uk.gov.justice.services.healthcheck.healthchecks.EventStoreHealthcheck.EVENT_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.ViewStoreHealthcheck.VIEW_STORE_HEALTHCHECK_NAME;

import uk.gov.justice.services.healthcheck.api.DefaultIgnoredHealthcheckNamesProvider;

import java.util.List;

import javax.enterprise.inject.Specializes;

@Specializes
public class StagingDcsIgnoredHealthcheckNamesProvider extends DefaultIgnoredHealthcheckNamesProvider {

    public StagingDcsIgnoredHealthcheckNamesProvider() {
        // This constructor is required by CDI.
    }

    @Override
    public List<String> getNamesOfIgnoredHealthChecks() {
        return asList(
                EVENT_STORE_HEALTHCHECK_NAME,
                VIEW_STORE_HEALTHCHECK_NAME
        );
    }
}