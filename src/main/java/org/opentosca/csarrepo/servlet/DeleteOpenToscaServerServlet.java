package org.opentosca.csarrepo.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opentosca.csarrepo.exception.AuthenticationException;
import org.opentosca.csarrepo.model.User;
import org.opentosca.csarrepo.service.DeleteOpenToscaServerService;

/**
 * Servlet for deleting OpenToscaServers
 * 
 * @author eiselems (marcus.eisele@gmail.com)
 *
 */
@SuppressWarnings("serial")
@WebServlet(DeleteOpenToscaServerServlet.PATH)
public class DeleteOpenToscaServerServlet extends AbstractServlet {

	private static final Logger LOGGER = LogManager.getLogger(DeleteOpenToscaServerServlet.class);
	public static final String PATH = "/deleteopentoscaserver/*";

	public DeleteOpenToscaServerServlet() {
		super();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		try {
			User user = checkUserAuthentication(request, response);
			String[] pathInfo;
			long otServerId;
			pathInfo = request.getPathInfo().split("/");
			otServerId = Long.parseLong(pathInfo[1]);
			// TODO: use real user
			DeleteOpenToscaServerService deleteOtServerService = new DeleteOpenToscaServerService(user.getId(),
					otServerId);
			boolean result = deleteOtServerService.getResult();
			if (result) {
				this.redirect(request, response, ListOpenToscaServerServlet.PATH);
			} else {
				// TODO: Improve error handling
				throw new ServletException("Error while deleting OpenTOSCA Server with Id " + otServerId
						+ "with error: " + deleteOtServerService.getErrors().get(0));
			}
		} catch (AuthenticationException e) {
			return;
		} catch (Exception e) {
			LOGGER.error("Error while parsing URL parameters", e);
			throw new ServletException("Error while parsing URL parameters");
		}

	}
}
