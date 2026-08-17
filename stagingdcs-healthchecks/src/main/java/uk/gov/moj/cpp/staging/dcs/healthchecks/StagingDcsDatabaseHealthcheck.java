package uk.gov.moj.cpp.staging.dcs.healthchecks;

import uk.gov.justice.services.healthcheck.api.Healthcheck;
import uk.gov.justice.services.healthcheck.api.HealthcheckResult;
import uk.gov.justice.services.healthcheck.utils.database.TableChecker;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;
import javax.sql.DataSource;

import org.slf4j.Logger;

public class StagingDcsDatabaseHealthcheck implements Healthcheck {
    public static final String STAGING_DCS_HEALTHCHECK_NAME = "stagingdcs-healthcheck";
    public static final List<String> STAGING_DCS_TABLE_NAMES = Collections.emptyList();
    @Inject
    private ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;
    @Inject
    private TableChecker tableChecker;
    @Inject
    private Logger logger;

    public String getHealthcheckName() {
        return STAGING_DCS_HEALTHCHECK_NAME;
    }

    public String healthcheckDescription() {
        return "Checks connectivity to the stagingdcs database and that all tables are available";
    }

    public HealthcheckResult runHealthcheck() {
        DataSource eventStoreDataSource = this.viewStoreJdbcDataSourceProvider.getDataSource();

        try {
            return this.tableChecker.checkTables(STAGING_DCS_TABLE_NAMES, eventStoreDataSource);
        } catch (SQLException e) {
            this.logger.error("Healthcheck for stagingdcs database failed.", e);
            return HealthcheckResult.failure(String.format("Exception thrown accessing stagingdcs database. %s: %s", e.getClass().getName(), e.getMessage()));
        }
    }
}
