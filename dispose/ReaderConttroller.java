package com.rfid.integration.rest;

import com.rfid.integration.service.InventoryService;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("/reader")
public class ReaderController {

    private final InventoryService service = new InventoryService();

    @POST
    @Path("/start/{ip}")
    public Response start(@PathParam("ip") String ip) throws Exception {

        service.startReader(ip);

        return Response.ok().build();
    }

    @POST
    @Path("/stop/{ip}")
    public Response stop(@PathParam("ip") String ip) {

        service.stopReader(ip);

        return Response.ok().build();
    }
}