/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.cryptotrader.game;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * The front end trading endpoint. This basically just forwards requests to the trading service using the
 * new JAX-RS RX client.
 *
 */
@Path("/trade")
public class TradingEndpoint {

    private Client client;

    @PostConstruct
    private void setup() {
        client = ClientBuilder.newClient();
    }

    @PreDestroy
    private void close() {
        client.close();
    }

    @Path("/bitcoin")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_PLAIN})
    public void bitcoin(@Suspended AsyncResponse response, JsonObject jsonObject) {
        //connect to the trading service
        client
                .target(ExchangeService.BITCOIN_TRADE)
                .request(MediaType.APPLICATION_JSON)
                .rx() //use the RX invoker
                .post(Entity.entity(jsonObject, MediaType.APPLICATION_JSON_TYPE))
                .whenComplete((r, e) -> {
                    //this callback is invoked when the request is complete
                    if (e != null) {
                        response.resume(Response.serverError().build());
                    } else if(r.getStatus() == Response.Status.BAD_REQUEST.getStatusCode()) {
                        response.resume(Response.status(Response.Status.BAD_REQUEST).entity(r.readEntity(String.class)).build());
                    } else if(r.getStatus() == Response.Status.OK.getStatusCode()) {
                        //the trading endpoint responds in JSON, but we
                        //just want to respond in plain text
                        JsonObject json = r.readEntity(JsonObject.class);
                        response.resume(json.getJsonNumber("units"));
                    } else {
                        response.resume(Response.serverError().build());
                    }
                });
    }

}
