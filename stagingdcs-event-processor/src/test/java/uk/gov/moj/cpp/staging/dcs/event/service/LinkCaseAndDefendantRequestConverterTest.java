package uk.gov.moj.cpp.staging.dcs.event.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.staging.dcs.event.util.FileUtil.givenPayload;

import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;

import java.io.IOException;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkCaseAndDefendantRequestConverterTest {

    @InjectMocks
    private LinkCaseAndDefendantRequestConverter linkCaseAndDefendantRequestConverter;

    @Test
    void shouldReturnLinkCaseAndDefendantRequestObject_defendantPerson() throws IOException {
        final JsonObject jsonObject = givenPayload("/stagingdcs.submit-dcs-case-record.json");
        final String transactionRef = UUID.randomUUID().toString();
        final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = linkCaseAndDefendantRequestConverter.convert(jsonObject, transactionRef);

        assertThat(linkCaseAndDefendantRequest.getCaseId(), is("d9fe4c51-1783-4fe4-a2b4-7c0e25905484"));
        assertThat(linkCaseAndDefendantRequest.getTransactionRef(), is(transactionRef));
        assertThat(linkCaseAndDefendantRequest.getDefendants().get(0).getDefendantOrganisation(), nullValue());
        assertThat(linkCaseAndDefendantRequest.getDefendants().get(0).getDefendantPerson().getForename(), is("John"));
        assertThat(linkCaseAndDefendantRequest.getDefendants().get(0).getHearings().size(), is(1));
    }

    @Test
    void shouldReturnLinkCaseAndDefendantRequestObject_defendantOrg_NoDuplicateHearings() throws IOException {
        final JsonObject jsonObject = givenPayload("/stagingdcs.submit-dcs-case-record-org.json");
        final LinkCaseAndDefendantRequest linkCaseAndDefendantRequest = linkCaseAndDefendantRequestConverter.convert(jsonObject, UUID.randomUUID().toString());

        assertThat(linkCaseAndDefendantRequest.getCaseReferral(), is("a9fe4c51-1783-4fe4-a2b4-7c0e25905484"));
        assertThat(linkCaseAndDefendantRequest.getDefendants().get(0).getDefendantOrganisation(), notNullValue());
        assertThat(linkCaseAndDefendantRequest.getDefendants().get(0).getHearings().size(), is(2));
    }
}