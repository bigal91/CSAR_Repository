package org.opentosca.csarrepo.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.opentosca.csarrepo.exception.PersistenceException;
import org.opentosca.csarrepo.model.WineryServer;
import org.opentosca.csarrepo.model.repository.WineryServerRepository;

public class CreateWineryServerService extends AbstractService {

	public CreateWineryServerService(long userId, String name, String uri) {
		super(userId);

		// validate the name
		name.trim();
		if (name.isEmpty() || name.length() > 255) {
			this.addError("nameLengthError");
		}

		// validate uri
		try {
			URI address = new URI(uri);
			if (!super.hasErrors()) {
				// no errors save
				WineryServer ws = new WineryServer();
				ws.setName(name);
				ws.setAddress(address);
				WineryServerRepository repo = new WineryServerRepository();
				try {
					repo.save(ws);
				} catch (PersistenceException e) {
					this.addError("savingWineryFailed");
				}
			}
		} catch (URISyntaxException e1) {
			this.addError("invalidURIError");
		}
	}

	public boolean getResult() {
		return !super.hasErrors();
	}
}
