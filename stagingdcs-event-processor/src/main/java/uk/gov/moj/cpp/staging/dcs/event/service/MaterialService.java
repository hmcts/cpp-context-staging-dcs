package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.staging.dcs.material.client.MaterialUrlGenerator;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

@SuppressWarnings("java:S2629")
public class MaterialService {

    private static final String MATERIAL_IDS = "materialIds";
    private static final String MATERIALS = "materials";
    private static final String MATERIAL_QUERY_DOWNLOADABLE_MATERIAL = "material.query.is-downloadable-materials";

    public static final String MATERIAL_ID = "materialId";
    public static final String MATERIAL_QUERY_MATERIAL_BY_ID = "application/vnd.material.query.material+json";

    @Inject
    private Logger logger;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    @Inject
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @Inject
    private MaterialUrlGenerator materialUrlGenerator;

    public Optional<JsonObject> getAllMaterialsWithDownloadStatus(final List<String> materialIdList) {
        final Metadata metadataWithActionName = metadataBuilder().withName(MATERIAL_QUERY_DOWNLOADABLE_MATERIAL).withId(randomUUID()).build();
        final JsonObjectBuilder paramBuilder = createObjectBuilder().add(MATERIAL_IDS, join(materialIdList, ","));

        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, paramBuilder.build());
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        if (isNull(response.payload()) || !response.payload().containsKey(MATERIALS)) {
            return Optional.empty();
        }
        logger.info("Response info from material for downloadable materials {}", response.payload().toString());
        return ofNullable(response.payload().getJsonObject(MATERIALS));
    }

    public Map<String, Boolean> getDownloadableMaterialMap(final List<String> materialIdList) {
        final Map<String, Boolean> map = new HashMap<>();
        final Optional<JsonObject> materialsObject = getAllMaterialsWithDownloadStatus(materialIdList);
        materialIdList.forEach(id -> materialsObject.ifPresent(material -> map.put(id, material.getBoolean(id, false))));
        return map;
    }

    public Optional<String> queryMaterialWithMaterialIdToGetAzureBlobUrlOfMaterial(final String materialId) {
        final UUID systemUserId = serviceContextSystemUserProvider.getContextSystemUserId().orElseThrow(() -> new RuntimeException("Failed to retrieve System User Id"));
        final String materialUrl = materialUrlGenerator.fileStreamUrlFor(fromString(materialId));
        final Response response = ClientBuilder.newClient().target(materialUrl).request().accept(MATERIAL_QUERY_MATERIAL_BY_ID).header(USER_ID, systemUserId.toString()).get();
        final int responseStatus = response.getStatus();
        if (responseStatus == SC_OK) {
            final String azureUrl = response.getLocation().toString();
            logger.info("Response info from material for material blob url {}", azureUrl);
            return Optional.of(azureUrl);
        }
        return Optional.empty();
    }
}
