package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.util.List.of;

import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDefendantOffencesEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

public class CaseDefendantOffencesRepository extends BaseRepository{

    private static final String SELECT_QUERY="SELECT * FROM case_defendant_offences WHERE case_id = ? and defendant_id = ?";
    private static final String SELECT_QUERY_ALL_BY_CASE_ID ="SELECT * FROM case_defendant_offences WHERE case_id = ?";

    private static final String INSERT_OFFENCE = "INSERT INTO case_defendant_offences (id, case_id, defendant_id, offence_id) " +
            "VALUES (?, ?, ?, ?)";

    private static final String DELETE_OFFENCE = "DELETE FROM case_defendant_offences WHERE case_id = ? and defendant_id = ?";
    private static final String DELETE_OFFENCE_BY_CASE_ID = "DELETE FROM case_defendant_offences WHERE case_id = ?";

    public void deleteAllByCaseId (final UUID caseId) {
        executeDelete(DELETE_OFFENCE_BY_CASE_ID, of(caseId));
    }

    public List<CaseDefendantOffencesEntity> findByCaseIdDefendantId(final UUID caseId, final UUID defendantId) {
        return executeQuery(SELECT_QUERY, of(caseId,defendantId));
    }

    public List<CaseDefendantOffencesEntity> findAllByCaseId(final UUID caseId) {
        return executeQuery(SELECT_QUERY_ALL_BY_CASE_ID, of(caseId));
    }

    public void saveCaseDefendantOffence(final CaseDefendantOffencesEntity caseDefendantOffencesEntity) {
        final List<Object> params = Lists.newArrayList(caseDefendantOffencesEntity.getId(),
                caseDefendantOffencesEntity.getCaseId(),
                caseDefendantOffencesEntity.getDefendantId(),
                caseDefendantOffencesEntity.getOffenceId());
        executeUpdate(INSERT_OFFENCE, params);
    }

    public void deleteCaseDefendantOffence(final CaseDefendantOffencesEntity caseDefendantOffencesEntity) {
        final List<Object> params = Lists.newArrayList(caseDefendantOffencesEntity.getId(),
                caseDefendantOffencesEntity.getCaseId(),
                caseDefendantOffencesEntity.getDefendantId(),
                caseDefendantOffencesEntity.getOffenceId());
        executeUpdate(DELETE_OFFENCE, params);
    }

    public int deleteCaseDefendantOffences(final List<UUID> idsToDelete) {
        final String placeholders = String.join(",", Collections.nCopies(idsToDelete.size(), "?"));
        final String sql = "DELETE FROM case_defendant_offences WHERE id IN (" + placeholders + ")";

        int rowsDeleted =  executeDelete(sql, idsToDelete);
        return rowsDeleted;
    }


    private List<CaseDefendantOffencesEntity> executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps,params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                    return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<CaseDefendantOffencesEntity> mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final List<CaseDefendantOffencesEntity> entities = Lists.newArrayList();
        while (resultSet.next()) {
            final CaseDefendantOffencesEntity entity = new CaseDefendantOffencesEntity();
            entity.setId(getUUID(resultSet,"id"));
            entity.setCaseId(getUUID(resultSet, "case_id"));
            entity.setDefendantId(getUUID(resultSet,"defendant_id"));
            entity.setOffenceId(getUUID(resultSet,"offence_id"));
            entities.add(entity);
        }
        return entities;
    }
}
