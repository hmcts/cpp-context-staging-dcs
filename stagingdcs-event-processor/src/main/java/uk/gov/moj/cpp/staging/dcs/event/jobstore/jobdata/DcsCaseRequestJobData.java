package uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata;

import uk.gov.hmcts.dcs.openapi.model.LinkCaseAndDefendantRequest;
import uk.gov.justice.services.messaging.Metadata;

import jakarta.json.JsonObject;

public record DcsCaseRequestJobData(JsonObject createPayload, String caseUrn, JsonObject metadata, String transactionRef) {
}
