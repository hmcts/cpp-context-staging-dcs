package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.nCopies;
import static java.util.List.of;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.justice.services.common.converter.ZonedDateTimes.fromSqlTimestamp;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionDetailEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

public class TransactionDetailRepository extends BaseRepository {

    private static final String SELECT_QUERY_BY_TRANSACTION_REF = "SELECT * FROM transaction_detail WHERE transaction_ref_id = ?";
    private static final String SELECT_QUERY_BY_TRANSACTION_REF_ID_LIST = "SELECT * FROM transaction_detail WHERE transaction_ref_id IN (%s)";

    private static final String INSERT = "INSERT INTO transaction_detail (transaction_ref_id, case_id, payload, error, created_at, transaction_status, transaction_type) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS_BY_TRANSACTION_REF = "UPDATE transaction_detail SET transaction_status = ?, error = ?, updated_at =?  WHERE transaction_ref_id = ?";

    public void save(final TransactionDetailEntity transactionDetailEntity) {
        final List<Object> params = Lists.newArrayList(
                transactionDetailEntity.getTransactionRefId(),
                transactionDetailEntity.getCaseId(),
                transactionDetailEntity.getPayload(),
                transactionDetailEntity.getError(),
                ofNullable(transactionDetailEntity.getCreatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null),
                transactionDetailEntity.getTransactionStatus(),
                transactionDetailEntity.getTransactionType()
        );
        executeUpdate(INSERT, params);
    }

    public TransactionDetailEntity findByTransactionReferenceId(final UUID transactionReferenceId) {
        return executeQuery(SELECT_QUERY_BY_TRANSACTION_REF, of(transactionReferenceId));
    }

    public List<TransactionDetailEntity> findByTransactionsByIdList(final List<UUID> transactionReferenceIdList) {
        final String finalQueryString = String.format(SELECT_QUERY_BY_TRANSACTION_REF_ID_LIST, preparePlaceHolders(transactionReferenceIdList.size()));
        return executeQueryToGetTransactionList(finalQueryString,transactionReferenceIdList);
    }

    public void updateStatusByTransactionReferenceId(final String status, final String error, final UUID transactionRef) {
        executeUpdate(UPDATE_STATUS_BY_TRANSACTION_REF, List.of(status, error, now().toOffsetDateTime(), transactionRef));
    }

    private static String preparePlaceHolders(int length) {
        return String.join(",", nCopies(length, "?"));
    }

    private List<TransactionDetailEntity> executeQueryToGetTransactionList(final String query, final List<UUID> tranRefIdList) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addIds(ps, tranRefIdList);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToTransactionEntityList(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, tranRefIdList), e);
        }
    }

    private TransactionDetailEntity executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<TransactionDetailEntity> mapResultSetToTransactionEntityList(final ResultSet resultSet) throws SQLException {
        List<TransactionDetailEntity> tranList = new ArrayList<>();
        TransactionDetailEntity transactionDetailEntity = null;
        while (resultSet.next()) {
            transactionDetailEntity = new TransactionDetailEntity();
            transactionDetailEntity.setTransactionRefId(getUUID(resultSet, "transaction_ref_id"));
            transactionDetailEntity.setCaseId(getUUID(resultSet,"case_id"));
            transactionDetailEntity.setTransactionType(resultSet.getString("transaction_type"));
            transactionDetailEntity.setTransactionStatus(resultSet.getString("transaction_status"));
            transactionDetailEntity.setPayload(resultSet.getString("payload"));
            transactionDetailEntity.setError(resultSet.getString("error"));
            transactionDetailEntity.setCreatedAt(fromSqlTimestamp(resultSet.getTimestamp("created_at")));
            final Timestamp timestamp = resultSet.getTimestamp("updated_at");
            if (nonNull(timestamp)) {
                transactionDetailEntity.setUpdatedAt(fromSqlTimestamp(timestamp));
            }
            tranList.add(transactionDetailEntity);
        }
        return tranList;
    }

    private TransactionDetailEntity mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final TransactionDetailEntity entity = new TransactionDetailEntity();
        while (resultSet.next()) {
            entity.setTransactionRefId(getUUID(resultSet,"transaction_ref_id"));
            entity.setCaseId(getUUID(resultSet,"case_id"));
            entity.setTransactionType(resultSet.getString("transaction_type"));
            entity.setTransactionStatus(resultSet.getString("transaction_status"));
            entity.setPayload(resultSet.getString("payload"));
            entity.setError(resultSet.getString("error"));
            entity.setCreatedAt(fromSqlTimestamp(resultSet.getTimestamp("created_at")));
            final Timestamp timestamp = resultSet.getTimestamp("updated_at");
            if (nonNull(timestamp)) {
                entity.setUpdatedAt(fromSqlTimestamp(timestamp));
            }
        }
        return entity;
    }
}
