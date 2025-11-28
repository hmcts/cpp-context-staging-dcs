package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.List.of;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryException;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.google.common.collect.Lists;
import org.slf4j.Logger;

public class DcsDefendantRepository extends BaseRepository {
    @Inject
    private Logger logger;
    private static final String INSERT_PERSON_DEFENDANT = "INSERT INTO dcs_defendant (defendant_id, forename, middlename, surname, date_of_birth, interpreter_language, interpreter_information, bail_status, organisation_name, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_QUERY = "SELECT * FROM dcs_defendant WHERE defendant_id = ?";
    private static final String UPDATE_DEFENDANT = "UPDATE dcs_defendant SET forename = ?, middlename = ?, surname =? , date_of_birth = ?, updated_at = ?  WHERE defendant_id = ?";
    private static final String UPDATE_DEFENDANT_ORG = "UPDATE dcs_defendant SET organisation_name = ?, updated_at = ?  WHERE defendant_id = ?";
    private static final String UPDATE_MASTER_DEFENDANT = "UPDATE dcs_defendant SET master_defendant_id = ?, updated_at = ?  WHERE defendant_id = ?";
    private static final String UPDATE_DEFENCE_REPRESENTATION_DETAILS = "UPDATE dcs_defendant SET defence_org_name = ?, defence_org_email = ?, updated_at = ?  WHERE defendant_id = ?";

    public void saveDefendant(final DcsDefendantEntity dcsDefendantEntity) {
        final List<Object> params = Lists.newArrayList(
                dcsDefendantEntity.getDefendantId(),
                dcsDefendantEntity.getForename(),
                dcsDefendantEntity.getMiddlename(),
                dcsDefendantEntity.getSurname(),
                dcsDefendantEntity.getDateOfBirth(),
                dcsDefendantEntity.getInterpreterLanguage(),
                dcsDefendantEntity.getInterpreterInformation(),
                dcsDefendantEntity.getBailStatus(),
                dcsDefendantEntity.getOrganisationName(),
                ofNullable(dcsDefendantEntity.getCreatedAt()).map(ZonedDateTimes::toSqlTimestamp).orElse(null)
        );
        executeUpdate(INSERT_PERSON_DEFENDANT, params);
    }

    public void updateDefenceRepresentationDetails(final String orgName, final String orgEmail, final UUID defendantId) {
        executeUpdate(UPDATE_DEFENCE_REPRESENTATION_DETAILS, List.of(orgName, orgEmail, now().toOffsetDateTime(), defendantId));
    }

    public void updateDefendentDetails(final String forename, final String middlename, final String surname, final LocalDate dob, final UUID defendantId) {
        executeUpdate(UPDATE_DEFENDANT, Arrays.asList(forename, middlename, surname, dob, now().toOffsetDateTime(), defendantId));
    }

    public void updateDefendantOrg(final String orgName, final UUID defendantId) {
        executeUpdate(UPDATE_DEFENDANT_ORG, List.of(orgName, now().toOffsetDateTime(), defendantId));
    }

    public void updateMasterDefendant(final UUID masterDefendantId, final UUID defendantId) {
        executeUpdate(UPDATE_MASTER_DEFENDANT, List.of(masterDefendantId, now().toOffsetDateTime(), defendantId));
    }

    public DcsDefendantEntity findByDefendantId(final UUID defendantId) {
        return executeQuery(SELECT_QUERY, of(defendantId));
    }

    private DcsDefendantEntity executeQuery(final String query, final List<Object> params) {
        try (final PreparedStatementWrapper ps = preparedStatementWrapperFactory.preparedStatementWrapperOf(dataSource, query)) {
            addParams(ps, params);
            try (final ResultSet resultSet = ps.executeQuery()) {
                return mapResultSetToEntities(resultSet);
            }
        } catch (SQLException e) {
            throw new JdbcRepositoryException(format("Exception while executing query: %s, with params: %s", query, params), e);
        }
    }

    private DcsDefendantEntity mapResultSetToEntities(final ResultSet resultSet) throws SQLException {
        final DcsDefendantEntity entity = new DcsDefendantEntity();
        while (resultSet.next()) {
            entity.setDefendantId(getUUID(resultSet, "defendant_id"));
            entity.setForename(resultSet.getString("forename"));
            entity.setMiddlename(resultSet.getString("middlename"));
            entity.setSurname(resultSet.getString("surname"));
            if (nonNull(resultSet.getDate("date_of_birth"))) {
                entity.setDateOfBirth(resultSet.getDate("date_of_birth").toLocalDate());
            }
            entity.setOrganisationName(resultSet.getString("organisation_name"));
            entity.setBailStatus(resultSet.getString("bail_status"));
            entity.setInterpreterInformation(resultSet.getString("interpreter_information"));
            entity.setInterpreterLanguage(resultSet.getString("interpreter_language"));
            entity.setMasterDefendantId(getUUID(resultSet, "master_defendant_id"));
        }
        return entity;
    }
}
