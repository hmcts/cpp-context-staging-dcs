package uk.gov.moj.cpp.staging.dcs.event.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.staging.dcs.event.service.azureblob.AzureBlobClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class AzureStorageServiceTest {
    @InjectMocks
    private AzureStorageService azureStorageService;

    @Mock
    public Logger logger;
    @Mock
    AzureBlobClientService azureBlobClientService;
    @Test
    void shouldStoreMaterialToAzureStorage() {
        final String azureStorageUrlAfterUpload = "https://azureUrlForDcsPayoad/material?anything";
        when(azureBlobClientService.storeAndGetUploadedBlobUrlWithVersionFromUrl(any(), any(), any())).thenReturn(azureStorageUrlAfterUpload);
        final String uploadUrl = azureStorageService.storeMaterialToAzureStorage(any(), any(), any());
        assertThat(uploadUrl, is(azureStorageUrlAfterUpload));
    }

    @Test
    void shouldDeleteMaterialFromAzureStorage() {
        final String fileName = "fileName";
        final String tranRefId = randomUUID().toString();
        when(azureBlobClientService.deleteBlobFromStorage(fileName, tranRefId)).thenReturn(true);
        final boolean isDeleted = azureStorageService.deleteMaterialFromAzureStorage(fileName, tranRefId);
        assertThat(isDeleted, is(true));
    }
}