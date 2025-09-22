package uk.gov.moj.cpp.staging.dcs.event.service;

import uk.gov.moj.cpp.staging.dcs.event.service.azureblob.AzureBlobClientService;

import javax.inject.Inject;

import org.slf4j.Logger;
@SuppressWarnings("java:S2629")
public class AzureStorageService {

    @Inject
    private Logger logger;

    @Inject
    private AzureBlobClientService azureBlobClientService;

    public String storeMaterialToAzureStorage(final String fileName, final String tranRefId, final String azureMaterialUrl) {
        final String blobUrl = azureBlobClientService.storeAndGetUploadedBlobUrlWithVersionFromUrl(fileName, tranRefId, azureMaterialUrl);
        logger.info("dcs material stored blob with url {} for transactionId {}", blobUrl, tranRefId);
        return blobUrl;
    }

    public boolean deleteMaterialFromAzureStorage(final String fileName, final String tranRefId) {
        logger.info("deleting dcs material from blob store file {} for transactionId {}", fileName, tranRefId);
        return azureBlobClientService.deleteBlobFromStorage(fileName, tranRefId);
    }
}
