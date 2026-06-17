package uk.gov.moj.cpp.staging.dcs.stub;

import java.io.StringReader;

import uk.gov.justice.services.messaging.JsonObjects;
import javax.json.JsonObject;
import javax.ws.rs.core.Response;

public class SimpleResponse {

    private final String string;
    private final int status;

    private SimpleResponse(final String string) {
        this.string = string;
        this.status = Response.Status.OK.getStatusCode();
    }

    private SimpleResponse(final String string, final Integer status) {
        this.string = string;
        this.status = status;
    }

    public static SimpleResponse of(final String string) {
        return new SimpleResponse(string);
    }

    public static SimpleResponse of(final String string, final int status) {
        return new SimpleResponse(string, status);
    }

    public String asString() {
        return string;
    }

    public int asStatus() {
        return status;
    }

    public JsonObject asJsonObject() {
        return JsonObjects.createReader(new StringReader(string)).readObject();
    }
}
