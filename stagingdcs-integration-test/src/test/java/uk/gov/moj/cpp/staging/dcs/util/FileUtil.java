package uk.gov.moj.cpp.staging.dcs.util;

import static com.google.common.io.Resources.getResource;
import static java.lang.ClassLoader.getSystemResourceAsStream;
import static java.lang.String.format;
import static java.nio.charset.Charset.defaultCharset;
import static org.apache.commons.collections.MapUtils.isNotEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Map;

import com.google.common.io.Resources;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    private FileUtil() {
    }

    public static String getPayload(final String path) {
        String fileContents = null;
        try (final InputStream inputStream = FileUtil.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream, notNullValue());
            fileContents = IOUtils.toString(inputStream, defaultCharset());
        } catch (final Exception e) {
            LOGGER.error("Error consuming file from location {}", path, e);
            fail("Error consuming file from location " + path);
        }
        return fileContents;
    }

    public static String getFileContentsAsString(final String fileName) {
        final StringBuilder sb = new StringBuilder();
        getBufferedReader(getReader(getResourceAsStream(fileName))).lines().forEach(sb::append);
        return sb.toString();
    }

    public static String getFileContentsAsString(final String fileName, final Object... placeholders) {
        final StringBuilder sb = new StringBuilder();
        getBufferedReader(getReader(getResourceAsStream(fileName))).lines().forEach(sb::append);
        return format(sb.toString(), placeholders);
    }

    public static BufferedReader getBufferedReader(final InputStreamReader inputStreamReader) {
        return new BufferedReader(inputStreamReader);
    }

    public static InputStreamReader getReader(final InputStream fileStream) {
        return new InputStreamReader(fileStream);
    }

    public static InputStream getResourceAsStream(String fileName) {
        return FileUtil.class.getClassLoader().getResourceAsStream(fileName);
    }

    public static String resourceToString(final String path, final Object... placeholders) {
        try (final InputStream systemResourceAsStream = getSystemResourceAsStream(path)) {
            assertThat(systemResourceAsStream, is(notNullValue()));
            return format(IOUtils.toString(systemResourceAsStream), placeholders);
        } catch (final IOException e) {
            LOGGER.error("Error consuming file from location {}", path, e);
            fail("Error consuming file from location " + path);
            throw new UncheckedIOException(e);
        }
    }

    public static String getJsonResponse(final String filename) {
        try {
            return Resources.toString(getResource(filename), defaultCharset());
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static String getPayloadWithReplacedValues(String filePath, Map<String,String> replaceValuesString) {
        String genericPayload = resourceToString(filePath);
        if (isNotEmpty(replaceValuesString)) {
            for (String key : replaceValuesString.keySet()) {
                genericPayload = genericPayload.replaceAll(key, replaceValuesString.get(key));
            }
        }
        return genericPayload;
    }
}
