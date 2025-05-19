package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;

import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapperFactory;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.sql.DataSource;

public class DcsCaseDetailRepository {
    @Inject
    private PreparedStatementWrapperFactory preparedStatementWrapperFactory;

    @Inject
    private ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;

    private DataSource dataSource;

    private static final String SELECT_STATUS_QUERY="SELECT * FROM dcs_case_detail WHERE case_id = ?";

    @PostConstruct
    private void initialiseDataSource() {
        dataSource = viewStoreJdbcDataSourceProvider.getDataSource();
    }

    public DcsCaseDetailEntity findByCaseId(final UUID caseId) {
        return executeQuery(SELECT_STATUS_QUERY, caseId);
    }

    private DcsCaseDetailEntity executeQuery(final String query, final Object param) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            ps.setObject(1, param);
            try (final ResultSet resultSet = ps.executeQuery()) {
                DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
                while (resultSet.next()) {
                    dcsCaseDetailEntity.setCaseId(getUUID(resultSet, "case_Id"));
                    dcsCaseDetailEntity.setDcsCaseStatus(resultSet.getString("dcs_case_status"));
                }
                return dcsCaseDetailEntity;
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, param), e);
        }
    }

    private UUID getUUID(ResultSet resultSet, String columnLabel) throws SQLException {
        String value = resultSet.getString(columnLabel);
        return value != null ? UUID.fromString(value) : null;
    }
}
