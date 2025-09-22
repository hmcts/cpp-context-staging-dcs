package uk.gov.moj.cpp.staging.dcs.helper;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.staging.dcs.util.FileUtil;

import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;

public class DcsHelper {
    public JsonObject createCaseinDcsRequest(final UUID caseId, final UUID defendantId, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
        ;
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }

    public JsonObject createCaseinDcsRequest(final UUID caseId, final UUID defendantId, final String caseUrn, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
                .replaceAll("CASE_URN", caseUrn)
        ;
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }

    public JsonObject createCaseinDcsRequest(final UUID caseId, final UUID defendantId1, final UUID defendantId2, final String caseUrn, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID1", defendantId1.toString())
                .replaceAll("DEFENDANT_ID2", defendantId2.toString())
                .replaceAll("CASE_URN", caseUrn)
        ;
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }

    public JsonObject processTransactionStatusRequest(final UUID caseId, final UUID defendantId, final String fileName) {
        String processTransactionStatusString = FileUtil.getFileContentsAsString(fileName);
        processTransactionStatusString = processTransactionStatusString
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantId.toString())
        ;
        return new StringToJsonObjectConverter().convert(processTransactionStatusString);
    }

    public JsonObject createCaseWithMultipleDefendants(final UUID caseId, final List<UUID> defendantIds, final String caseUrn, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID1", defendantIds.get(0).toString())
                .replaceAll("DEFENDANT_ID2", defendantIds.get(1).toString())
                .replaceAll("CASE_URN", caseUrn)
        ;
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }

    public JsonObject createCaseWithMultipleDefendants1(final UUID caseId, final List<UUID> defendantIds, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData
                .replaceAll("CASE_ID", caseId.toString())
                .replaceAll("DEFENDANT_ID", defendantIds.get(0).toString())
                .replaceAll("DEF_ID", defendantIds.get(1).toString())
        ;
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }
}
