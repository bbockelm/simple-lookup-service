package net.es.lookup.resources;

import net.es.lookup.api.Subscribe;

import javax.ws.rs.POST;
import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * This class and other similar resource classes need to be explicitly loaded in the 
 * net.es.lookup.service.LookupService class
 */
@Path("/lookup/subscribe/{queryId}")
public class SubscribeResource {

    private Subscribe subscribe = new Subscribe();


    @POST
    @Consumes("application/json")
    @Produces("application/json")
    public String postHandler (String message) {

        return this.subscribe.subscribe(message);
    }


    @GET
    @Produces("application/json")
    public String getHandler (@PathParam("queryId") String queryId) {

        if (queryId == null) {
            return "Not yet implemented\n";
        } else {
            return "Not yet implemented\n";
        }
    }


    @DELETE
    @Produces("application/json")
    public String deleteHandler (String message, @PathParam("service") String serviceid) {
        return "Not yet implemented\n";
    }

}
