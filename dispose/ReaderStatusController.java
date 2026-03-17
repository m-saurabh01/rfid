package com.rfid.integration.rest;

import com.rfid.integration.core.ReaderNode;
import com.rfid.integration.core.ReaderRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

@Path("/reader")
public class ReaderStatusController {

    @GET
    @Path("/status/{ip}")
    public Response status(@PathParam("ip") String ip) {

        ReaderNode node =
                ReaderRegistry.getInstance().getOrCreate(ip);

        String json =
                "{"
                        + "\"connected\":" + node.isConnected() + ","
                        + "\"inventoryRunning\":" + node.isInventoryRunning()
                        + "}";

        return Response.ok(json).build();
    }
}