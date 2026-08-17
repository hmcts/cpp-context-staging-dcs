package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.List.of;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.TransactionMetadataEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.pojos.SearchCriteria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.google.common.collect.Lists;

public class TransactionMetadataRepository extends BaseRepository {

    private static final String SELECT_QUERY_BY_TRANSACTION_REF="SELECT * FROM transaction_metadata WHERE transaction_ref_id = ?";

    private static final String INSERT = "INSERT INTO transaction_metadata (id, transaction_ref_id, case_id, defendant_id, created_at, updated_at, transaction_status, transaction_type, material_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS_BY_TRANSACTION_REF = "UPDATE transaction_metadata SET transaction_status = ?, updated_at =?  WHERE transaction_ref_id = ?";

    private static final String SELECT_BY_CASE_ID_AND_DEFENDANT_ID_QUERY =
            "SELECT * FROM transaction_metadata WHERE case_id = ? AND defendant_id = ? ORDER BY updated_at DESC";

    private static final String SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY =
            "SELECT * FROM transaction_metadata WHERE case_id = ? AND material_id = ? ORDER BY updated_at DESC";

    private static final String SELECT_BY_CASE_ID_AND_MATERIAL_ID_DEFENDANT_ID_QUERY =
            "SELECT * FROM transaction_metadata WHERE case_id = ? AND material_id = ?  AND defendant_id = ? ORDER BY updated_at DESC";

    public static final String CASE_ID = "caseId";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String MATERIAL_ID = "materialId";
    public static final String TRANSACTION_STATUS = "transactionStatus";
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String UPDATED_AT = "updatedAt";

    @PersistenceContext(unitName = "stagingdcs")
    private EntityManager entityManager;


    public List<TransactionMetadataEntity> getTransactionMetadataByCriteria(final SearchCriteria searchCriteria) {
        final CriteriaBuilder cBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<TransactionMetadataEntity> cQuery = cBuilder.createQuery(TransactionMetadataEntity.class);

        final Root<TransactionMetadataEntity> transactionMetadataEntityRoot = cQuery.from(TransactionMetadataEntity.class);
        final List<Predicate> predicates = getAllPredicates(searchCriteria, cBuilder, transactionMetadataEntityRoot);

        cQuery.where(predicates.toArray(new Predicate[0]));
        cQuery.orderBy(cBuilder.desc(transactionMetadataEntityRoot.get(DEFENDANT_ID)));

        final TypedQuery<TransactionMetadataEntity> typedQuery = entityManager.createQuery(cQuery)
                .setFirstResult(searchCriteria.getOffset())
                .setMaxResults(searchCriteria.getLimit());

        return typedQuery.getResultList();

    }

    public Long getTransactionMetadataCountByCriteria(final SearchCriteria searchCriteria) {
        final CriteriaBuilder cBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Long> cQuery = cBuilder.createQuery(Long.class);

        final Root<TransactionMetadataEntity> transactionMetadataEntityRoot = cQuery.from(TransactionMetadataEntity.class);
        final List<Predicate> predicates = getAllPredicates(searchCriteria, cBuilder, transactionMetadataEntityRoot);

        cQuery.where(predicates.toArray(new Predicate[0]));
        cQuery.select(cBuilder.count(transactionMetadataEntityRoot));
        return entityManager.createQuery(cQuery).getSingleResult();
    }

    public List<TransactionMetadataEntity> findByCaseIdAndDefendantId(final UUID caseId, final UUID defendantId) {
        return executeQuery(SELECT_BY_CASE_ID_AND_DEFENDANT_ID_QUERY, List.of(caseId, defendantId));
    }

    public List<TransactionMetadataEntity> findByCaseIdAndMaterialId(final UUID caseId, final UUID materialId) {
        return executeQuery(SELECT_BY_CASE_ID_AND_MATERIAL_ID_QUERY, List.of(caseId, materialId));
    }

    public List<TransactionMetadataEntity> findByCaseIdMaterialIdAndDefendantId(final UUID caseId, final UUID materialId, final UUID defendantId) {
        return executeQuery(SELECT_BY_CASE_ID_AND_MATERIAL_ID_DEFENDANT_ID_QUERY, List.of(caseId, materialId, defendantId));
    }

    public List<TransactionMetadataEntity> findByTransactionReferenceId(final UUID transactionReferenceId) {
        return executeQuery(SELECT_QUERY_BY_TRANSACTION_REF, of(transactionReferenceId));
    }

    public void save(final TransactionMetadataEntity transactionMetadataEntity) {
        final List<Object> params = Lists.newArrayList(
                transactionMetadataEntity.getId(),
                transactionMetadataEntity.getTransactionRefId(),
                transactionMetadataEntity.getCaseId(),
                transactionMetadataEntity.getDefendantId(),
                ofNullable(transactionMetadataEntity.getCreatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null),
                ofNullable(transactionMetadataEntity.getUpdatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null),
                transactionMetadataEntity.getTransactionStatus(),
                transactionMetadataEntity.getTransactionType(),
                transactionMetadataEntity.getMaterialId()
        );
        executeUpdate(INSERT, params);
    }

    public void updateStatusByTransactionReferenceId(String status, UUID transactionRef) {
        executeUpdate(UPDATE_STATUS_BY_TRANSACTION_REF, List.of(status, now().toOffsetDateTime(), transactionRef));
    }

    private List<TransactionMetadataEntity> executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private List<TransactionMetadataEntity> mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final List<TransactionMetadataEntity> entities = Lists.newArrayList();
        while (resultSet.next()) {
            final TransactionMetadataEntity entity = new TransactionMetadataEntity();
            entity.setId(getUUID(resultSet, "id"));
            entity.setTransactionRefId(getUUID(resultSet, "transaction_ref_id"));
            entity.setCaseId(getUUID(resultSet, "case_id"));
            entity.setMaterialId(getUUID(resultSet, "material_id"));
            entity.setDefendantId(getUUID(resultSet, "defendant_id"));
            entity.setCreatedAt(ZonedDateTime.ofInstant(resultSet.getTimestamp("created_at").toInstant(), ZoneId.systemDefault()));
            entity.setUpdatedAt(ZonedDateTime.ofInstant(resultSet.getTimestamp("updated_at").toInstant(), ZoneId.systemDefault()));
            entity.setTransactionStatus(resultSet.getString("transaction_status"));
            entity.setTransactionType(resultSet.getString("transaction_type"));
            entities.add(entity);
        }
        return entities;
    }

    private List<Predicate> getAllPredicates(final SearchCriteria searchCriteria, final CriteriaBuilder cBuilder, final Root<TransactionMetadataEntity> transactionMetadataEntityRoot) {
        final List<Predicate> predicates = new ArrayList<>();


        predicates.add(cBuilder.equal(transactionMetadataEntityRoot.get(CASE_ID), searchCriteria.getCaseId()));

        if (nonNull(searchCriteria.getDefendantId())) {
            predicates.add(cBuilder.equal(transactionMetadataEntityRoot.get(DEFENDANT_ID), searchCriteria.getDefendantId()));
        }

        if (nonNull(searchCriteria.getMaterialId())) {
            predicates.add(cBuilder.equal(transactionMetadataEntityRoot.get(MATERIAL_ID), searchCriteria.getMaterialId()));
        }

        if (nonNull(searchCriteria.getTransactionStatus())) {
            predicates.add(cBuilder.equal(transactionMetadataEntityRoot.get(TRANSACTION_STATUS), searchCriteria.getTransactionStatus()));
        }

        if (nonNull(searchCriteria.getTransactionType())) {
            predicates.add(cBuilder.equal(transactionMetadataEntityRoot.get(TRANSACTION_TYPE), searchCriteria.getTransactionType()));
        }
        addDateCriteriaToPredicate(predicates, searchCriteria, cBuilder, transactionMetadataEntityRoot);
        return predicates;
    }

    private void addDateCriteriaToPredicate(final List<Predicate> predicates, final SearchCriteria searchCriteria, final CriteriaBuilder cBuilder, final Root<TransactionMetadataEntity> transactionMetadataEntityRoot) {

        Path<ZonedDateTime> dateUpdatedPath = transactionMetadataEntityRoot.get(UPDATED_AT);
        if (nonNull(searchCriteria.getFromDate())) {
            ZonedDateTime fromZonedDateTime = searchCriteria.getFromDate().atStartOfDay(ZoneId.systemDefault());
            predicates.add(cBuilder.greaterThanOrEqualTo(dateUpdatedPath, fromZonedDateTime));
        }
        if (nonNull(searchCriteria.getToDate())) {
            ZonedDateTime toZonedDateTime = searchCriteria.getToDate().plusDays(1).atStartOfDay(ZoneId.systemDefault());
            predicates.add(cBuilder.lessThanOrEqualTo(dateUpdatedPath, toZonedDateTime));
        }
    }

}
