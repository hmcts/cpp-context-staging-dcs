package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;

import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDocumentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

public class CaseDocumentRepository extends BaseRepository {

    private static final String INSERT = "INSERT INTO case_document (case_id, material_id) VALUES (?, ?)";

    private static final String SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY =
            "SELECT * FROM case_document WHERE case_id = ? AND material_id = ?";


    public List<CaseDocumentEntity> findByCaseIdAndMaterialId(final UUID caseId, final UUID materialId) {
        return executeQuery(SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY, List.of(caseId, materialId));
    }

    public void save(final CaseDocumentEntity caseDocumentEntity) {
        final List<Object> params = Lists.newArrayList(
                caseDocumentEntity.getCaseId(),
                caseDocumentEntity.getMaterialId()
        );
        executeUpdate(INSERT, params);
    }

    private List<CaseDocumentEntity> executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<CaseDocumentEntity> mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final List<CaseDocumentEntity> entities = Lists.newArrayList();
        while (resultSet.next()) {
            final CaseDocumentEntity entity = new CaseDocumentEntity();
            entity.setCaseId(getUUID(resultSet, "case_id"));
            entity.setMaterialId(getUUID(resultSet, "material_id"));
            entities.add(entity);
        }
        return entities;
    }

}
