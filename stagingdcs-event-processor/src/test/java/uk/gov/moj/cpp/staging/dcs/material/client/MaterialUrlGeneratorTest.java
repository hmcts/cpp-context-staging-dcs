package uk.gov.moj.cpp.staging.dcs.material.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialUrlGeneratorTest {

    private static final String BASE_URI = "http://localhost:8080/material-query-api/query/api/rest/material";
    private static final String MATERIAL_REQUEST_PATH = "/material/";
    private static final String MATERIAL_STREAM_PDF_PARAMETERS = "?stream=true&requestPdf=true";

    private MaterialUrlGenerator materialUrlGenerator;

    @BeforeEach
    void createMaterialUrlGenerator() {
        materialUrlGenerator = new MaterialUrlGenerator();
    }

    @Test
    void shouldBuildPlainFileStreamUrlForMaterialId() {
        final UUID materialId = UUID.randomUUID();

        final String url = materialUrlGenerator.fileStreamUrlFor(materialId);

        assertThat(url, is(BASE_URI + MATERIAL_REQUEST_PATH + materialId));
    }

    @Test
    void shouldBuildPdfFileStreamUrlForMaterialId() {
        final UUID materialId = UUID.randomUUID();

        final String url = materialUrlGenerator.pdfFileStreamUrlFor(materialId);

        assertThat(url, is(BASE_URI + MATERIAL_REQUEST_PATH + materialId + MATERIAL_STREAM_PDF_PARAMETERS));
    }

    @Test
    void shouldBuildPdfFileStreamUrlWhenPdfStreamRequested() {
        final UUID materialId = UUID.randomUUID();

        final String url = materialUrlGenerator.fileStreamUrlFor(materialId, true);

        assertThat(url, is(BASE_URI + MATERIAL_REQUEST_PATH + materialId + MATERIAL_STREAM_PDF_PARAMETERS));
    }

    @Test
    void shouldBuildPlainFileStreamUrlWhenPdfStreamNotRequested() {
        final UUID materialId = UUID.randomUUID();

        final String url = materialUrlGenerator.fileStreamUrlFor(materialId, false);

        assertThat(url, is(BASE_URI + MATERIAL_REQUEST_PATH + materialId));
    }
}
