package uk.gov.justice.api.resource;

import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("transaction/{transactionRef}")
public interface CommandApiTransactionTransactionRefResource {

    @POST
    @Produces("*/*")
    @Consumes("application/vnd.stagingdcs.process-dcs-transaction-status+json")
    Response processTransactionStatus(@HeaderParam(USER_ID) final String userId, @PathParam("transactionRef") final String transactionRef, final JsonObject payload);
}
