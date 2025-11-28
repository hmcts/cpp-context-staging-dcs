package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.nCopies;
import static java.util.List.of;
import static java.util.Optional.ofNullable;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

public class DcsCaseDetailRepository extends BaseRepository{

    private static final String SELECT_STATUS_QUERY="SELECT * FROM dcs_case_detail WHERE case_id = ?";
    private static final String SELECT_CASE_DEFENDANT_LINK_QUERY = "SELECT * FROM dcs_case_detail WHERE case_id = ? and defendant_id = ?";
    private static final String SELECT_ALL_CASE_DEFENDANT_BY_DEFENDANT_ID = "SELECT * FROM dcs_case_detail WHERE defendant_id = ?";
    private static final String INSERT = "INSERT INTO dcs_case_detail (id, case_id, case_urn, case_referral_id, defendant_id, defendant_referral_id, dcs_defendant_status, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_STATUS_BY_ID = "UPDATE dcs_case_detail SET dcs_defendant_status = ?, updated_at =?  WHERE id = ?";
    private static final String UPDATE_STATUS_BY_ID_LIST = "UPDATE dcs_case_detail SET dcs_defendant_status = ?, updated_at =?  WHERE id IN (%s)";
    private static final String DELETE_CASE_DETAIL = "DELETE FROM dcs_case_detail WHERE case_id = ? and defendant_id = ?";

    private static final String UPDATE_TRANSACTION_BY_ID = "UPDATE dcs_case_detail SET case_referral_id = ?, defendant_referral_id = ?, dcs_defendant_status = ?, updated_at =?  WHERE id = ?";

    public List<DcsCaseDetailEntity> findByCaseId(final UUID caseId) {
        return executeQuery(SELECT_STATUS_QUERY, of(caseId));
    }

    public List<DcsCaseDetailEntity> findByDefendantId(final UUID defendantId) {
        return executeQuery(SELECT_ALL_CASE_DEFENDANT_BY_DEFENDANT_ID, of(defendantId));
    }

    public DcsCaseDetailEntity findByCaseIdDefendantId(final UUID caseId, final UUID defendantId) {
        return executeQuery(SELECT_CASE_DEFENDANT_LINK_QUERY, of(caseId, defendantId)).stream().findFirst().orElse(null);
    }

    public void updateStatusById(final String status, final UUID id) {
        executeUpdate(UPDATE_STATUS_BY_ID, List.of(status, now().toOffsetDateTime(), id));
    }

    public void updateTransactionById(final UUID caseReferralId, final UUID defendantReferralId, final String status, final UUID id) {
        executeUpdate(UPDATE_TRANSACTION_BY_ID, List.of(caseReferralId, defendantReferralId, status, now().toOffsetDateTime(), id));
    }

    public void saveDcsCaseDetail(final DcsCaseDetailEntity dcsCaseDetailEntity) {
        final List<Object> params = Lists.newArrayList(dcsCaseDetailEntity.getId(),
                dcsCaseDetailEntity.getCaseId(),
                dcsCaseDetailEntity.getCaseUrn(),
                dcsCaseDetailEntity.getCaseRefId(),
                dcsCaseDetailEntity.getDefendantId(),
                dcsCaseDetailEntity.getDefendantRefId(),
                dcsCaseDetailEntity.getDcsDefendantStatus(),
                ofNullable(dcsCaseDetailEntity.getCreatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null),
                ofNullable(dcsCaseDetailEntity.getUpdatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null));
        executeUpdate(INSERT, params);
    }

    public void updateDcsCaseDetailStatusByIds(final String status, final ZonedDateTime updatedAt, final List<UUID> ids) {
        final String finalUpdateString = String.format(UPDATE_STATUS_BY_ID_LIST, preparePlaceHolders(ids.size()));
        List paramList = new ArrayList();
        paramList.add(status);
        paramList.add(updatedAt);
        paramList.addAll(ids);
        executeUpdate(finalUpdateString, paramList);
    }

    public void deleteByCaseIdDefendantId (final UUID caseId, final UUID defendantId) {
        executeDelete(DELETE_CASE_DETAIL, of(caseId, defendantId));
    }


    private List<DcsCaseDetailEntity> executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps,params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                    return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<DcsCaseDetailEntity> mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final List<DcsCaseDetailEntity> entities = Lists.newArrayList();
        while (resultSet.next()) {
            final DcsCaseDetailEntity entity = new DcsCaseDetailEntity();
            entity.setId(getUUID(resultSet,"id"));
            entity.setCaseId(getUUID(resultSet, "case_id"));
            entity.setDefendantId(getUUID(resultSet,"defendant_id"));
            entity.setCaseUrn(resultSet.getString("case_urn"));
            entity.setCaseRefId(getUUID(resultSet, "case_referral_id"));
            entity.setDefendantRefId(getUUID(resultSet, "defendant_referral_id"));
            entity.setDcsDefendantStatus(resultSet.getString("dcs_defendant_status"));
            entities.add(entity);
        }
        return entities;
    }

    private static String preparePlaceHolders(int length) {
        return String.join(",", nCopies(length, "?"));
    }
}
