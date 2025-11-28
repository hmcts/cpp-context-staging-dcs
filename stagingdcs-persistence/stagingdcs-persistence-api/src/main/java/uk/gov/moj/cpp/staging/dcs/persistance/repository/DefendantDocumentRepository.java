package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;

import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DefendantDocumentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

public class DefendantDocumentRepository extends BaseRepository {

    private static final String INSERT = "INSERT INTO defendant_document (case_id, material_id, defendant_id) VALUES (?, ?, ?)";

    private static final String SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY =
            "SELECT * FROM defendant_document WHERE case_id = ? AND material_id = ? AND defendant_id = ?";


    public List<DefendantDocumentEntity> findByCaseIdMaterialIdAndDefendantId(final UUID caseId, final UUID materialId, final UUID defendantId) {
        return executeQuery(SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY, List.of(caseId, materialId, defendantId));
    }

    public void save(final DefendantDocumentEntity defendantDocumentEntity) {
        final List<Object> params = Lists.newArrayList(
                defendantDocumentEntity.getCaseId(),
                defendantDocumentEntity.getMaterialId(),
                defendantDocumentEntity.getDefendantId()
        );
        executeUpdate(INSERT, params);
    }

    private List<DefendantDocumentEntity> executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<DefendantDocumentEntity> mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final List<DefendantDocumentEntity> entities = Lists.newArrayList();
        while (resultSet.next()) {
            final DefendantDocumentEntity entity = new DefendantDocumentEntity();
            entity.setCaseId(getUUID(resultSet, "case_id"));
            entity.setMaterialId(getUUID(resultSet, "material_id"));
            entity.setDefendantId(getUUID(resultSet, "defendant_id"));
            entities.add(entity);
        }
        return entities;
    }

}
