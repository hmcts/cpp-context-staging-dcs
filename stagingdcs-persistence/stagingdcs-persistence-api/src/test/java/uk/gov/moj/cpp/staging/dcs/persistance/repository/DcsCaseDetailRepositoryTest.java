package uk.gov.moj.cpp.staging.dcs.persistance.repository;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
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
class DcsCaseDetailRepositoryTest {

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
    private DcsCaseDetailRepository dcsCaseDetailRepository;

    private static final UUID CASE_ID = randomUUID();
    private static final UUID DEFENDANT_ID = randomUUID();
    private static final String COLUMN_CASE_ID = "case_id";
    private static final String COLUMN_DEFENDANT_ID = "defendant_id";
    private static final String COLUMN_ID = "id";


    @BeforeEach
    void setUp() throws Exception {

        try (AutoCloseable autoCloseable = openMocks(this)) {
            Mockito.when(viewStoreJdbcDataSourceProvider.getDataSource()).thenReturn(dataSource);
            dcsCaseDetailRepository.initialiseDataSource();
            when(preparedStatementWrapperFactory.preparedStatementWrapperOf(any(DataSource.class), anyString())).thenReturn(preparedStatementWrapper);
        }
    }

    @Test
    void testSaveDcsCaseDetail() throws Exception {
        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        dcsCaseDetailEntity.setCaseRefId(randomUUID());
        dcsCaseDetailEntity.setDefendantRefId(randomUUID());
        dcsCaseDetailEntity.setCreatedAt(utcClock.now());

        dcsCaseDetailRepository.saveDcsCaseDetail(dcsCaseDetailEntity);

        verify(preparedStatementWrapper, times(1)).executeUpdate();
    }

    @Test
    void testFindByCaseIdDefendantId() throws SQLException {

        when(preparedStatementWrapper.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(COLUMN_ID)).thenReturn(randomUUID().toString());
        when(resultSet.getString(COLUMN_CASE_ID)).thenReturn(CASE_ID.toString());
        when(resultSet.getString(COLUMN_DEFENDANT_ID)).thenReturn(DEFENDANT_ID.toString());

        DcsCaseDetailEntity dcsCaseDetailEntity = dcsCaseDetailRepository.findByCaseIdDefendantId(CASE_ID, DEFENDANT_ID);

        assertNotNull(dcsCaseDetailEntity);
        assertEquals(CASE_ID, dcsCaseDetailEntity.getCaseId());
        assertEquals(DEFENDANT_ID, dcsCaseDetailEntity.getDefendantId());
    }

}
