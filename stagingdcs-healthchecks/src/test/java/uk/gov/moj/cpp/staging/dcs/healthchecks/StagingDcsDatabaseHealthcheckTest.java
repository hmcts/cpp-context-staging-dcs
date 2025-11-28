package uk.gov.moj.cpp.staging.dcs.healthchecks;

import static java.util.Optional.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.staging.dcs.healthchecks.StagingDcsDatabaseHealthcheck.STAGING_DCS_HEALTHCHECK_NAME;
import static uk.gov.moj.cpp.staging.dcs.healthchecks.StagingDcsDatabaseHealthcheck.STAGING_DCS_TABLE_NAMES;

import uk.gov.justice.services.healthcheck.api.HealthcheckResult;
import uk.gov.justice.services.healthcheck.utils.database.TableChecker;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class StagingDcsDatabaseHealthcheckTest {

    @Mock
    private ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;

    @Mock
    private TableChecker tableChecker;

    @Mock
    private Logger logger;

    @InjectMocks
    private StagingDcsDatabaseHealthcheck stagingDcsDatabaseHealthcheck;

    @Test
    void shouldReturnCorrectHealthcheckName() throws Exception {

        assertThat(stagingDcsDatabaseHealthcheck.getHealthcheckName(), is(STAGING_DCS_HEALTHCHECK_NAME));
    }

    @Test
    void shouldReturnCorrectHealthcheckDescription() throws Exception {

        assertThat(stagingDcsDatabaseHealthcheck.healthcheckDescription(), is("Checks connectivity to the stagingdcs database and that all tables are available"));
    }

    @Test
    void shouldGetListOfExpectedTablesFromDatabaseAsHealthcheck() throws Exception {

        final DataSource systemDataSource = mock(DataSource.class);
        final HealthcheckResult healthcheckResult = mock(HealthcheckResult.class);

        when(viewStoreJdbcDataSourceProvider.getDataSource()).thenReturn(systemDataSource);
        when(tableChecker.checkTables(STAGING_DCS_TABLE_NAMES, systemDataSource)).thenReturn(healthcheckResult);

        assertThat(stagingDcsDatabaseHealthcheck.runHealthcheck(), is(healthcheckResult));
    }

    @Test
    void shouldReturnHealthcheckFailureIfAccessingTheDatabaseThrowsSqlException() throws Exception {

        final SQLException sqlException = new SQLException("Oops");
        final DataSource systemDataSource = mock(DataSource.class);

        when(viewStoreJdbcDataSourceProvider.getDataSource()).thenReturn(systemDataSource);
        when(tableChecker.checkTables(STAGING_DCS_TABLE_NAMES, systemDataSource)).thenThrow(sqlException);

        final HealthcheckResult healthcheckResult = stagingDcsDatabaseHealthcheck.runHealthcheck();

        assertThat(healthcheckResult.isPassed(), is(false));
        assertThat(healthcheckResult.getErrorMessage().isPresent(), is(true));
        assertThat(healthcheckResult.getErrorMessage(), is(of("Exception thrown accessing stagingdcs database. java.sql.SQLException: Oops")));

        verify(logger).error("Healthcheck for stagingdcs database failed.", sqlException);
    }

}