package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapperFactory;
import uk.gov.justice.services.jdbc.persistence.ViewStoreJdbcDataSourceProvider;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsDefendantEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DcsDefendantRepositorytest {
    @Mock
    private PreparedStatementWrapperFactory preparedStatementWrapperFactory;
    @Mock
    private ViewStoreJdbcDataSourceProvider viewStoreJdbcDataSourceProvider;
    @Mock
    private DataSource dataSource;
    @Mock
    private PreparedStatementWrapper preparedStatementWrapper;
    @Mock
    private ResultSet resultSet;

    @Mock
    private UtcClock utcClock;

    @InjectMocks
    private DcsDefendantRepository dcsDefendantRepository;

    @BeforeEach
    void setUp() throws Exception {
        try (AutoCloseable autoCloseable = openMocks(this)) {
            Mockito.when(viewStoreJdbcDataSourceProvider.getDataSource()).thenReturn(dataSource);
            dcsDefendantRepository.initialiseDataSource();
            when(preparedStatementWrapperFactory.preparedStatementWrapperOf(any(DataSource.class), anyString())).thenReturn(preparedStatementWrapper);
        }
    }

    @Test
    void testSaveDefendant() throws SQLException {
        DcsDefendantEntity dcsDefendantEntity = new DcsDefendantEntity();
        dcsDefendantEntity.setForename("First");
        dcsDefendantEntity.setSurname("Last");
        dcsDefendantEntity.setDefendantId(UUID.randomUUID());
        dcsDefendantEntity.setInterpreterInformation("Interpreter Info");
        dcsDefendantEntity.setBailStatus("Remand");
        dcsDefendantRepository.saveDefendant(dcsDefendantEntity);
        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }

    @Test
    void testUpdateDefenceRepresentationDetails() throws SQLException {
        dcsDefendantRepository.updateDefenceRepresentationDetails("OrgName", StringUtils.EMPTY, UUID.randomUUID());
        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }
}
