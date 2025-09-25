package uk.gov.moj.cpp.staging.dcs.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static java.lang.Thread.sleep;
import static java.util.UUID.randomUUID;
import static org.junit.platform.commons.util.StringUtils.isNotBlank;
import static uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub.stubDcsApiAddMaterialCall;
import static uk.gov.moj.cpp.staging.dcs.stub.MaterialServiceStub.stubMaterialServiceDownloadableMaterials;
import static uk.gov.moj.cpp.staging.dcs.stub.MaterialServiceStub.stubQueryMaterialById;
import static uk.gov.moj.cpp.staging.dcs.stub.ProgressionServiceStub.stubProgressionServiceAllCourtDocuments;
import static uk.gov.moj.cpp.staging.dcs.stub.ReferenceDataServiceStub.getAllDocumentTypeAccessStub;
import static uk.gov.moj.cpp.staging.dcs.stub.UsersGroupsStub.stubUsersAndGroups;

import uk.gov.moj.cpp.staging.dcs.stub.DcsServiceStub;
import uk.gov.moj.cpp.staging.dcs.stub.IdMapperStub;
import uk.gov.moj.cpp.staging.dcs.stub.MaterialServiceStub;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;

public class BaseIT {
    public UUID caseId;
    public UUID defendantId;
    public String caseUrn;
    public UUID defendantReferral;
    public UUID caseReferral;
    public UUID documentTypeId1;
    public UUID documentTypeId2;
    public UUID documentTypeId3;
    public UUID materialId1;
    public UUID materialId2;
    public UUID materialId3;
    public static final String LINKED = "LINKED TO DCS";
    public static final String NOT_LINKED = "NOT LINKED TO DCS";
    public static final String DCS_NOT_UPDATED = "DCS NOT UPDATED";
    public static final String DCS_UPDATED = "DCS UPDATED";

    @BeforeAll
    public static void setupOnce() {
        WireMock.configureFor(System.getProperty("INTEGRATION_HOST_KEY", "localhost"), 8080);
    }

    void setup() throws IOException, InterruptedException {
        reset();
        sleep(3000);
        IdMapperStub.setUp();
        stubUsersAndGroups();
        caseId = randomUUID();
        defendantId = randomUUID();
        caseUrn = generateUrn();
        defendantReferral = randomUUID();
        caseReferral = randomUUID();
        documentTypeId1 = randomUUID();
        documentTypeId2 = randomUUID();
        documentTypeId3 = randomUUID();
        materialId1 = randomUUID();
        materialId2 = randomUUID();
        materialId3 = randomUUID();
    }

    public static String generateUrn() {
        return randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }

    public void createSendMaterialFunctionalStubs(final String caseId, final String defendantId1, final String defendantId2, final String caseUrn){
        Map<String,String> progressionStubReplaceValuesMap = new HashMap<>();
        progressionStubReplaceValuesMap.put("CASE_ID", caseId);
        progressionStubReplaceValuesMap.put("DEFENDANT_ID_1", defendantId1);
        progressionStubReplaceValuesMap.put("DEFENDANT_ID_2", isNotBlank(defendantId2) ? defendantId2 : randomUUID().toString());
        progressionStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_1", documentTypeId1.toString());
        progressionStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_2", documentTypeId2.toString());
        progressionStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_3", documentTypeId3.toString());
        progressionStubReplaceValuesMap.put("MATERIAL_ID_1", materialId1.toString());
        progressionStubReplaceValuesMap.put("MATERIAL_ID_2", materialId2.toString());
        progressionStubReplaceValuesMap.put("MATERIAL_ID_3", materialId3.toString());
        stubProgressionServiceAllCourtDocuments(caseId, progressionStubReplaceValuesMap);

        Map<String,String> referenceDataStubReplaceValuesMap = new HashMap<>();
        referenceDataStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_1", documentTypeId1.toString());
        referenceDataStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_2", documentTypeId2.toString());
        referenceDataStubReplaceValuesMap.put("DOCUMENT_ACCESS_TYPE_ID_3", documentTypeId3.toString());
        getAllDocumentTypeAccessStub(referenceDataStubReplaceValuesMap);

        Map<String,Boolean> materialStubReplaceValuesMap = new HashMap<>();
        materialStubReplaceValuesMap.put(materialId1.toString(), true);
        materialStubReplaceValuesMap.put(materialId2.toString(), true);
        materialStubReplaceValuesMap.put(materialId3.toString(), true);
        stubMaterialServiceDownloadableMaterials(materialStubReplaceValuesMap);

        stubQueryMaterialById();
        stubDcsApiAddMaterialCall(caseUrn);

    }
}
