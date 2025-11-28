package uk.gov.moj.cpp.staging.dcs.stub;

import static java.util.Collections.emptyMap;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import com.google.common.io.Resources;
import org.apache.commons.lang3.text.StrSubstitutor;

public class SimpleFileClient {

    public static SimpleResponse getFile(final String path) throws IOException {
        return getFile(path, emptyMap());
    }

    public static SimpleResponse getFile(final String path, final Map<String, String> valuesMap) throws IOException {
        final String string = Resources.toString(Resources.getResource(path), Charset.defaultCharset());
        return SimpleResponse.of(new StrSubstitutor(valuesMap).replace(string));
    }
}
