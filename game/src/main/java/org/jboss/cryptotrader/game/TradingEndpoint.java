package org.jboss.cryptotrader.game;

import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.cryptotrader.ExchangeService;

@Path("/trade")
public class TradingEndpoint {

    @Path("/bitcoin")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_PLAIN})
    public void bitcoin(@Suspended AsyncResponse response, JsonObject jsonObject) {
        ClientBuilder.newClient()
                .target(ExchangeService.BITCOIN_TRADE)
                .request(MediaType.APPLICATION_JSON)
                .rx()
                .post(Entity.entity(jsonObject, MediaType.APPLICATION_JSON_TYPE))
                .whenComplete((r, e) -> {
                    if (e != null || r.getStatus() != 200) {
                        response.resume(Response.serverError().build());
                    } else {
                        JsonObject json = r.readEntity(JsonObject.class);
                        response.resume(json.getJsonNumber("units"));
                    }
                });
    }

}
