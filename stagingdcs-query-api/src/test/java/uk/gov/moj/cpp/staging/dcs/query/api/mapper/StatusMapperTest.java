package uk.gov.moj.cpp.staging.dcs.query.api.mapper;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;

import java.util.List;

import org.junit.jupiter.api.Test;

class StatusMapperTest {

    private static final String LINKED_TO_DCS = "LINKED TO DCS";
    private static final String NOT_LINKED_TO_DCS = "NOT LINKED TO DCS";
    private static final String DCS_LINK_SEVERED = "DCS LINK SEVERED";
    @Test
    void shouldMapTransactionStatus() {
        assertEquals("DCS UPDATED", StatusMapper.mapTransactionStatus(TransactionStatus.SUCCESS.toString()));
        assertEquals("DCS NOT UPDATED", StatusMapper.mapTransactionStatus(TransactionStatus.FAILED.toString()));
        assertEquals(EMPTY, StatusMapper.mapTransactionStatus(TransactionStatus.SENT.toString()));
        assertEquals(EMPTY, StatusMapper.mapTransactionStatus(null));
        assertEquals(EMPTY, StatusMapper.mapTransactionStatus(EMPTY));
    }

    @Test
    void shouldMapDefendantStatus() {
        assertEquals(LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(DcsDefendantStatus.LINKED.toString())));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(DcsDefendantStatus.PENDING.toString())));
        assertEquals(DCS_LINK_SEVERED, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(DcsDefendantStatus.UNLINKED.toString())));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(DcsDefendantStatus.FAILED.toString())));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(DcsDefendantStatus.AWAITING.toString())));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity(null)));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapDefendantStatus(createDcsCaseDetailEntity("")));
    }

    @Test
    void shouldMapCaseStatus() {
        assertEquals(LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.LINKED.toString()))));
        assertEquals(LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.LINKED.toString()),
                createDcsCaseDetailEntity(DcsDefendantStatus.UNLINKED.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.PENDING.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.UNLINKED.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.FAILED.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.AWAITING.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(DcsDefendantStatus.PENDING.toString()),
                createDcsCaseDetailEntity(DcsDefendantStatus.FAILED.toString()))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(null))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of(createDcsCaseDetailEntity(""))));
        assertEquals(NOT_LINKED_TO_DCS, StatusMapper.mapCaseStatus(List.of()));
    }

    private DcsCaseDetailEntity createDcsCaseDetailEntity(String caseStatus) {
        DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setDcsDefendantStatus(caseStatus);
        return dcsCaseDetailEntity;
    }
}
