package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapperFactory;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.CaseDefendantOffencesEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseDefendantOffencesRepositoryTest {
    @Mock
    private PreparedStatementWrapperFactory preparedStatementWrapperFactory;
    @Mock
    private ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;
    @Mock
    private DataSource dataSource;
    @Mock
    private PreparedStatementWrapper preparedStatementWrapper;
    @InjectMocks
    private CaseDefendantOffencesRepository caseDefendantOffencesRepository;

    @BeforeEach
    void setUp() throws Exception {

        try (AutoCloseable autoCloseable = openMocks(this)) {
            Mockito.when(viewStoreJdbcDataSourceProvider.getDataSource()).thenReturn(dataSource);
            caseDefendantOffencesRepository.initialiseDataSource();
            when(preparedStatementWrapperFactory.preparedStatementWrapperOf(any(DataSource.class), anyString())).thenReturn(preparedStatementWrapper);
        }
    }

    @Test
    void testSaveCaseDefendantOffence() throws SQLException {
        final CaseDefendantOffencesEntity caseDefendantOffencesEntity = new CaseDefendantOffencesEntity();
        caseDefendantOffencesEntity.setId(UUID.randomUUID());
        caseDefendantOffencesEntity.setOffenceId(UUID.randomUUID());
        caseDefendantOffencesRepository.saveCaseDefendantOffence(caseDefendantOffencesEntity);
        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }

    @Test
    void testDeleteCaseDefendantOffence() throws SQLException {
        final CaseDefendantOffencesEntity caseDefendantOffencesEntity = new CaseDefendantOffencesEntity();
        caseDefendantOffencesEntity.setId(UUID.randomUUID());
        caseDefendantOffencesEntity.setOffenceId(UUID.randomUUID());
        caseDefendantOffencesRepository.deleteCaseDefendantOffence(caseDefendantOffencesEntity);
        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }

    @Test
    void testDeleteCaseDefendantOffences() throws SQLException {
        final List<UUID> idsToDelete = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        caseDefendantOffencesRepository.deleteCaseDefendantOffences(idsToDelete);
        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }
}