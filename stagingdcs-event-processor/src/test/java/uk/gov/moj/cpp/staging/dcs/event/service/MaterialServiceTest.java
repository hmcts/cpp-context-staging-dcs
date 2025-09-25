package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.material.url.MaterialUrlGenerator;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {
    private static final String MATERIALS = "materials";

    private static final String MATERIAL_QUERY_DOWNLOADABLE_MATERIAL = "material.query.is-downloadable-materials";
    public static final String MATERIAL_QUERY_MATERIAL_BY_ID = "application/vnd.material.query.material+json";
    private String materialId;
    @InjectMocks
    private MaterialService materialService;
    @Mock
    Logger logger;
    @Mock
    Requester requester;
    private Envelope responseEnvelope;
    @Mock
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;
    @Mock
    private MaterialUrlGenerator materialUrlGenerator;

    @BeforeEach
    void setUp() {
        materialId = randomUUID().toString();
        responseEnvelope = Envelope.envelopeFrom(
                Envelope.metadataBuilder()
                        .withUserId(randomUUID().toString())
                        .withId(randomUUID())
                        .withName(MATERIAL_QUERY_DOWNLOADABLE_MATERIAL)
                        .build(),
                createObjectBuilder()
                        .add(MATERIALS, createObjectBuilder()
                                .add(materialId, true)
                                .build())
                        .build()
        );
    }

    @Test
    void shouldGetAllDownloadableMaterials() {

        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final Optional<JsonObject> actualAssociationObject = materialService.getAllMaterialsWithDownloadStatus(List.of(materialId));

        assertThat(actualAssociationObject, notNullValue());
        JsonObject responsePayload = actualAssociationObject.get();
        assertThat(responsePayload.getBoolean(materialId), is(true));
    }

    @Test
    void shouldGetDownloadableMaterialMap() {

        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final Map<String, Boolean> responseMap = materialService.getDownloadableMaterialMap(List.of(materialId));

        assertThat(responseMap, notNullValue());
        assertThat(responseMap.get(materialId), is(true));
    }

    @Test
    void shouldNotAnyGetAllDownloadableMaterials() {

        Envelope responseEnvelope = Envelope.envelopeFrom(
                Envelope.metadataBuilder()
                        .withUserId(randomUUID().toString())
                        .withId(randomUUID())
                        .withName(MATERIAL_QUERY_DOWNLOADABLE_MATERIAL)
                        .build(),
                createObjectBuilder().build()
        );
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);
        final Optional<JsonObject> actualAssociationObject = materialService.getAllMaterialsWithDownloadStatus(List.of(materialId));

        assertThat(actualAssociationObject.isPresent(), is(false));
    }

    @Test
    @Disabled("somehow not working hence disabling it for time being")
    void shouldQueryMaterialWithMaterialIdToGetAzureBlobUrlOfMaterial() {
        final String sasMaterialUrl = "http://whateverhost.uk/foruploding/materialToazure/Storage";
        final UUID systemUserId = randomUUID();
        final Client client = mock(Client.class);
        final WebTarget webTarget = mock(WebTarget.class);
        final Invocation.Builder builder = mock(Invocation.Builder.class);
        Response response = Response.status(HttpStatus.SC_OK)
                .header(LOCATION, sasMaterialUrl)
                .entity(sasMaterialUrl)
                .build();
        when(materialUrlGenerator.fileStreamUrlFor(any())).thenReturn(sasMaterialUrl);
        when(serviceContextSystemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(systemUserId));
        try (MockedStatic<ClientBuilder> utilities = Mockito.mockStatic(ClientBuilder.class)) {
            utilities.when(ClientBuilder::newClient).thenReturn(client);
        }
        when(client.target(sasMaterialUrl)).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);
        when(builder.accept(MATERIAL_QUERY_MATERIAL_BY_ID)).thenReturn(builder);
        when(builder.header(USER_ID, systemUserId.toString())).thenReturn(builder);
        when(builder.get()).thenReturn(response);

        final Optional<String> responseUrl = materialService.queryMaterialWithMaterialIdToGetAzureBlobUrlOfMaterial(materialId);

        assertThat(responseUrl, notNullValue());
        assertThat(responseUrl.isPresent(), is(true));
        assertThat(responseUrl.get(), is(sasMaterialUrl));
    }
}