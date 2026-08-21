package uk.gov.moj.cpp.staging.dcs.event.service.azureblob;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

import uk.gov.justice.services.common.configuration.GlobalValue;

import java.io.File;
import java.net.URL;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.azure.core.util.Configuration;
import com.azure.core.util.ConfigurationBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AzureBlobClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobClientService.class);
    private static final String ERROR_MSG = "Azure %s is not specified. Please add configuration for `%s`";
    public static final int CONNECTION_TIMEOUT_MILLIS = 2000;
    public static final int READ_TIMEOUT_MILLIS = 4000;
    public static final String STRING_UNDERSCORE = "_";
    private BlobContainerClient blobContainerClient = null;
    private static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    private static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";
    @Inject
    @GlobalValue(key = "dcs.document.azure.storage.connection-string", defaultValue = "")
    private String storageConnectionString;
    @Inject
    @GlobalValue(key = "dcs.document.azure.storage.account-name", defaultValue = "")
    private String azureStorageAccountName;
    @Inject
    @GlobalValue(key = "dcs.document.azure.storage.container-name", defaultValue = "dcs-documents")
    private String azureStorageContainerName;
    @Inject
    private StorageApplicationParameters storageApplicationParameters;

    @PostConstruct
    void init() {
        checkNotNull(azureStorageContainerName,
                format(ERROR_MSG, "input container name", "dcs.document.azure.storage.container-name"));
    }

    public void connect(final String blobContainerName) {
        final BlobServiceClient blobServiceClient = createBlobServiceClient();

        blobContainerClient = blobServiceClient.getBlobContainerClient(blobContainerName);
        blobContainerClient.createIfNotExists();
        LOGGER.info("blobContainerClient : {}", blobContainerClient);
    }

    public String storeAndGetUploadedBlobUrlWithVersionFromUrl(final String blobName, final String tranRefAsPrefix, final String sourceBlobUrl) {
        LOGGER.info("Connecting to azure blob storage to upload and get blob url files from : {}", azureStorageContainerName);
        connect(azureStorageContainerName);
        final BlobClient client = blobContainerClient.getBlobClient(tranRefAsPrefix.concat(STRING_UNDERSCORE).concat(blobName));
        File urlFile = null;

        try {
            urlFile = new File("/tmp/".concat(tranRefAsPrefix.concat(STRING_UNDERSCORE).concat(blobName)));
            FileUtils.copyURLToFile(new URL(sourceBlobUrl), urlFile, CONNECTION_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
            client.uploadFromFile(urlFile.getPath(), true);
            return client.getBlobUrl();
        } catch (Exception e) {
            LOGGER.error("Error while generating temp file from material sasUrl or uploading to azure blob storage");
            throw new RuntimeException(e);
        } finally {
            boolean isTempFileDeleted = true;
            if (nonNull(urlFile) && urlFile.exists()) {
                isTempFileDeleted = urlFile.delete();
            }
            if (!isTempFileDeleted) {
                LOGGER.error("Temporary file is not deleted from temp folder while uploading to azure blob storage");
            }
        }
    }

    public boolean deleteBlobFromStorage(final String blobName, final String tranRefAsPrefix) {
        LOGGER.info("Connecting to azure blob storage to delete {} with transactionId : {}", blobName, tranRefAsPrefix);
        connect(azureStorageContainerName);
        final BlobClient client = blobContainerClient.getBlobClient(tranRefAsPrefix.concat(STRING_UNDERSCORE).concat(blobName));
        return client.deleteIfExists();
    }

    private BlobServiceClient createBlobServiceClient() {
        if (StringUtils.isEmpty(azureStorageAccountName)) {
            return new BlobServiceClientBuilder()
                    .connectionString(storageConnectionString)
                    .buildClient();
        }

        final Configuration configuration = new ConfigurationBuilder()
                .putProperty(AZURE_CLIENT_ID, storageApplicationParameters.getAzureLocalMiClientId())
                .putProperty(AZURE_TENANT_ID, storageApplicationParameters.getAzureLocalMiTenantId())
                .build();

        return new BlobServiceClientBuilder()
                .endpoint(format("https://%s.blob.core.windows.net/", azureStorageAccountName))
                .credential(new DefaultAzureCredentialBuilder()
                        .tenantId(storageApplicationParameters.getAzureLocalMiTenantId())
                        .managedIdentityClientId(storageApplicationParameters.getAzureLocalMiClientId())
                        .configuration(configuration)
                        .build())
                .buildClient();
    }
}
