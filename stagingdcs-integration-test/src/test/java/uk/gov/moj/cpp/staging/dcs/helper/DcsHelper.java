package uk.gov.moj.cpp.staging.dcs.helper;

import static uk.gov.moj.cpp.staging.dcs.util.FileUtil.getFileContentsAsString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.staging.dcs.util.FileUtil;

import java.util.UUID;

import javax.json.JsonObject;

public class DcsHelper {
    public JsonObject createCaseinDcsRequest(final UUID caseId, final String fileName) {
        String createCaseInDcsRequestData = FileUtil.getFileContentsAsString(fileName);
        createCaseInDcsRequestData = createCaseInDcsRequestData.replace("CASE_ID", caseId.toString());
        return new StringToJsonObjectConverter().convert(createCaseInDcsRequestData);
    }
}
