package uk.gov.justice.api.resource;

import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("transaction/{transactionRef}")
public interface CommandApiTransactionTransactionRefResource {

    @POST
    @Produces("*/*")
    @Consumes("application/vnd.stagingdcs.process-dcs-transaction-status+json")
    Response processTransactionStatus(@HeaderParam(USER_ID) final String userId, @PathParam("transactionRef") final String transactionRef, final JsonObject payload);
}
