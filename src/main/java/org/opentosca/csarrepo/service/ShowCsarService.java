package org.opentosca.csarrepo.service;

import org.opentosca.csarrepo.exception.PersistenceException;
import org.opentosca.csarrepo.model.Csar;
import org.opentosca.csarrepo.model.repository.CsarRepository;

/**
 * Service to show Csars
 * 
 * @author eiselems (marcus.eisele@gmail.com)
 */
public class ShowCsarService extends AbstractService {

	private final CsarRepository csarRepository = new CsarRepository();
	private Csar csar = null;

	/**
	 * @param userId
	 * @param csarId
	 */
	public ShowCsarService(long userId, long csarId) {
		super(userId);
		try {
			csar = csarRepository.getbyId(csarId);
		} catch (PersistenceException e) {
			this.addError(e.getMessage());
		}
		
		if(this.csar == null) {
			this.addError("invalidCsar");
		}
	}

	/**
	 * @return List of CSARs
	 */
	public Csar getResult() {
		super.logInvalidResultAccess("getResult");

		return this.csar;
	}
}
