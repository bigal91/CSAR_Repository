package org.opentosca.csarrepo.rest.resource;

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opentosca.csarrepo.model.Csar;
import org.opentosca.csarrepo.model.CsarFile;
import org.opentosca.csarrepo.rest.model.CsarEntry;
import org.opentosca.csarrepo.rest.model.SimpleXLink;
import org.opentosca.csarrepo.rest.util.LinkBuilder;
import org.opentosca.csarrepo.service.ShowCsarService;

public class CsarResource {

	private static final Logger LOGGER = LogManager.getLogger(CsarResource.class);
	private UriInfo uriInfo;
	private long id;

	public CsarResource(UriInfo uriInfo, long id) {
		this.uriInfo = uriInfo;
		this.id = id;
	}

	@GET
	@Produces(MediaType.APPLICATION_XML)
	public Response getCsar() {
		// TODO: check if csar exists
		List<SimpleXLink> links = new LinkedList<SimpleXLink>();
		links.add(LinkBuilder.selfLink(uriInfo));

		List<SimpleXLink> csarFiles = new LinkedList<SimpleXLink>();
		// TODO: add real UserID
		ShowCsarService showService = new ShowCsarService(0L, id);

		if (showService.hasErrors()) {
			// TODO: move to helper
			// TODO: don't only fetch first error
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(showService.getErrors().get(0)).build();
		}

		Csar csar = showService.getResult();

		for (CsarFile csarFile : csar.getCsarFiles()) {
			csarFiles.add(new SimpleXLink(LinkBuilder.linkToCsarFile(uriInfo, id, csarFile.getId()), csarFile.getName()
					+ "-" + csarFile.getId()));
		}

		CsarEntry csarEntry = new CsarEntry(csar, links, csarFiles);
		System.out.println("CsarResource.getCsar() id: " + id);
		return Response.ok(csarEntry).build();
	}

	// TODO: move id to constant class
	@Path("/{" + "id" + "}")
	public Object getCsarFile(@PathParam("id") long id, @Context UriInfo uriInfo) {
		return new CsarFileResource(uriInfo, this.id, id);
	}
}
