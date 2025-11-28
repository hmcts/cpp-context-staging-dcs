package uk.gov.moj.cpp.staging.dcs.event.util;

import static java.nio.charset.Charset.defaultCharset;
import static javax.json.Json.createReader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Objects;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();

    private FileUtil() {
    }

    public static String getPayload(final String path) {
        String request = null;
        try (final InputStream inputStream = FileUtil.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream, notNullValue());
            request = IOUtils.toString(inputStream, defaultCharset());
        } catch (final Exception e) {
            LOGGER.error("Error consuming file from location {}", path, e);
            fail("Error consuming file from location " + path);
        }
        return request;
    }

    public static JsonObject jsonFromString(String jsonObjectStr) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonObjectStr));
        JsonObject object = jsonReader.readObject();
        jsonReader.close();
        return object;
    }


    public static JsonObject givenPayload(final String filePath) throws IOException {
        JsonReader jsonReader = null;
        try (final InputStream inputStream = FileUtil.class.getResourceAsStream(filePath)) {
            jsonReader = createReader(inputStream);
            return jsonReader.readObject();
        } finally {
            if (Objects.nonNull(jsonReader)) {
                jsonReader.close();
            }
        }
    }

    public static JsonObject getPayloadAsJsonObject(final String filename) throws IOException {
        String response = Resources.toString(Resources.getResource(filename), defaultCharset());
        return new StringToJsonObjectConverter().convert(response);
    }

    public static <T> T convertFromFile(final String url, final Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(new File(FileUtil.class.getClassLoader().getResource(url).getFile()), clazz);
    }
}
