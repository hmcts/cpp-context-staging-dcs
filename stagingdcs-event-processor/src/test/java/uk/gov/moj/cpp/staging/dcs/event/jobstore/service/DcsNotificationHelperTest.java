package uk.gov.moj.cpp.staging.dcs.event.jobstore.service;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.dcs.openapi.model.Defendant;
import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.hmcts.dcs.openapi.model.RequestFulfilledResponsePayload;
import uk.gov.hmcts.dcs.openapi.model.RequestFulfilledResponsePayloadDefendantsInner;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionType;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.DcsCaseDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionDetailRepository;
import uk.gov.moj.cpp.staging.dcs.persistance.repository.TransactionMetadataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class DcsNotificationHelperTest {

    @InjectMocks
    private DcsNotificationHelper dcsNotificationHelper;
    @Mock
    private DcsCaseDetailRepository dcsCaseDetailRepository;
    @Mock
    private TransactionDetailRepository transactionDetailRepository;
    @Mock
    private TransactionMetadataRepository transactionMetadataRepository;
    @Mock
    private Logger logger;
    private String caseId;
    private String defendantId;
    private String transactionRef;
    private LinkCaseAndDefendantRequest linkCaseAndDefendantRequest;

    @BeforeEach
    void setUp() {
        caseId = randomUUID().toString();
        defendantId = randomUUID().toString();
        transactionRef = randomUUID().toString();

        linkCaseAndDefendantRequest = new LinkCaseAndDefendantRequest();
        linkCaseAndDefendantRequest.setCaseId(caseId);
        linkCaseAndDefendantRequest.setTransactionRef(transactionRef);

        final List<Defendant> defendants = new ArrayList<>();
        final Defendant defendant = new Defendant();
        defendant.setId(defendantId);
        defendants.add(defendant);
        linkCaseAndDefendantRequest.setDefendants(defendants);
    }

    @Test
    void shouldProcessUpdateDcsCaseDetail(){
        final RequestFulfilledResponsePayload requestFulfilledResponsePayload = new RequestFulfilledResponsePayload();
        requestFulfilledResponsePayload.setCaseId(caseId);
        requestFulfilledResponsePayload.setTransactionRef(transactionRef);
        requestFulfilledResponsePayload.setCaseReferral(randomUUID().toString());

        final List<RequestFulfilledResponsePayloadDefendantsInner> responseDefendants = new ArrayList();
        final RequestFulfilledResponsePayloadDefendantsInner requestFulfilledResponsePayloadDefendantsInner = new RequestFulfilledResponsePayloadDefendantsInner();
        requestFulfilledResponsePayloadDefendantsInner.setDefendantId(defendantId);
        requestFulfilledResponsePayloadDefendantsInner.setDefendantReferral(randomUUID().toString());
        responseDefendants.add(requestFulfilledResponsePayloadDefendantsInner);
        requestFulfilledResponsePayload.setDefendants(responseDefendants);

        final DcsCaseDetailEntity dcsCaseDetailEntity = new DcsCaseDetailEntity();
        dcsCaseDetailEntity.setId(randomUUID());
        when(dcsCaseDetailRepository.findByCaseIdDefendantId(any(), any())).thenReturn(dcsCaseDetailEntity);

        dcsNotificationHelper.updateDcsCaseDetail(linkCaseAndDefendantRequest, requestFulfilledResponsePayload);
        verify(dcsCaseDetailRepository, times(1)).updateTransactionById(any(UUID.class),any(UUID.class), any(), any(UUID.class));
    }

    @Test
    void shouldSaveTransactionDetailsOnSuccess(){
        dcsNotificationHelper.saveOrUpdateTransactionDetails(randomUUID(), randomUUID(),null, TransactionStatus.SUCCESS.toString(), null, TransactionType.LINK_DEFENDANT);
        verify(transactionDetailRepository, times(1)).save(any());
    }

    @Test
    void shouldSaveTransactionDetailsOnFailed(){
        dcsNotificationHelper.saveOrUpdateTransactionDetails(randomUUID(), randomUUID(), null, TransactionStatus.FAILED.toString(), "error", TransactionType.LINK_DEFENDANT);
        verify(transactionDetailRepository, times(1)).save(any());
    }

    @Test
    void shouldSaveMetadata(){
        dcsNotificationHelper.saveOrUpdateMetadata(linkCaseAndDefendantRequest, TransactionStatus.SUCCESS.toString());
        verify(transactionMetadataRepository, times(1)).save(any());
    }
}
