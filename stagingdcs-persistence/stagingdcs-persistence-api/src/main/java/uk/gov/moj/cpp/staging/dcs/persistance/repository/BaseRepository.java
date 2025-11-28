package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapperFactory;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.sql.DataSource;

public class BaseRepository {
    @Inject
    protected PreparedStatementWrapperFactory preparedStatementWrapperFactory;

    @Inject
    protected ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;

    protected DataSource dataSource;

    @PostConstruct
    protected void initialiseDataSource() {
        dataSource = viewStoreJdbcDataSourceProvider.getDataSource();
    }

    protected void executeUpdate(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing update: %s, with params: %s", query, params.toString()), e);
        }
    }

    protected int executeDelete(final String query, final List<UUID> params) {
        int rowsDeleted = 0;
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addIds(ps, params);
            rowsDeleted = ps.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing delete: %s, with params: %s", query, params.toString()), e);
        }

        return rowsDeleted;
    }

    protected static void addParams(final PreparedStatementWrapper ps, final List<Object> params) throws SQLException {
        for (int i = 1; i <= params.size(); i++) {
            Object object = params.get(i - 1);
            if (object instanceof ZonedDateTime) {
                final ZonedDateTime zonedTime = (ZonedDateTime) object;
                ps.setTimestamp(i, Timestamp.from(zonedTime.toInstant()));
            } else {
                ps.setObject(i, object);
            }
        }
    }

    protected static void addIds(final PreparedStatementWrapper ps, final List<UUID> ids) throws SQLException {
        for (int i = 1; i <= ids.size(); i++) {
            ps.setObject(i, ids.get(i - 1));
        }
    }

    protected UUID getUUID(ResultSet resultSet, String columnLabel) throws SQLException {
        return ofNullable(resultSet.getString(columnLabel)).map(UUID::fromString).orElse(null);
    }
}
