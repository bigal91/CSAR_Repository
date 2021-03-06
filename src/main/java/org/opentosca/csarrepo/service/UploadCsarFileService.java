package org.opentosca.csarrepo.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.lingala.zip4j.exception.ZipException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opentosca.csarrepo.exception.PersistenceException;
import org.opentosca.csarrepo.filesystem.FileSystem;
import org.opentosca.csarrepo.model.Csar;
import org.opentosca.csarrepo.model.CsarFile;
import org.opentosca.csarrepo.model.HashedFile;
import org.opentosca.csarrepo.model.Plan;
import org.opentosca.csarrepo.model.repository.CsarFileRepository;
import org.opentosca.csarrepo.model.repository.CsarPlanRepository;
import org.opentosca.csarrepo.model.repository.CsarRepository;
import org.opentosca.csarrepo.model.repository.FileSystemRepository;
import org.opentosca.csarrepo.util.Extractor;
import org.opentosca.csarrepo.util.StringUtils;
import org.opentosca.csarrepo.util.ZipUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author eiselems (marcus.eisele@gmail.com), Dennis Przytarski
 *
 */
public class UploadCsarFileService extends AbstractService {

	private static final String CSAR_REPOSITORY_FILENAME = "CSAR-REPOSITORY.txt";
	private static final String ENTRY_DEFINITION_PATTERN = "Entry-Definitions: ([\\S]+)\\n";
	private static final String TOSCA_METADATA_FILEPATH = "TOSCA-Metadata/TOSCA.meta";

	private static final Logger LOGGER = LogManager.getLogger(UploadCsarFileService.class);
	private static final String XPATH_PLANS_FROM_SERVICETEMPLATE = "//*[local-name()='Plan']";
	private static final String XPATH_PLANMODELREFERENCE_REFERENCE = "//*[local-name()='PlanModelReference']/@*[name()='reference']";

	private CsarFile csarFile;

	// TODO: check if this is the newest one, or remove namespace checking by
	// replacing it with "*"
	private static final String SERVICETEMPLATE_NS = "http://docs.oasis-open.org/tosca/ns/2011/12";

	private static final String SERVICETEMPLATE_LOCALNAME = "ServiceTemplate";

	private static final String BUILDPLAN_TYPE_TOSCA = "http://docs.oasis-open.org/tosca/ns/2011/12/PlanTypes/BuildPlan";

	/**
	 * @param userId
	 * @param file
	 * @throws IOException
	 */
	public UploadCsarFileService(long userId, long csarId, InputStream inputStream, String name) {
		super(userId);

		if (!checkExtension(name, "csar")) {
			this.addError(String.format("Uploaded file %s does not contain required extension", name));
			return;
		}

		storeFile(csarId, inputStream, name);
	}

	/**
	 * Checks, if the name contains the given extension.
	 * 
	 * @param name
	 * @param extension
	 * @return true, if given name contains given extension
	 */
	private boolean checkExtension(String name, String extension) {
		int index = name.lastIndexOf('.');
		return 0 < index && name.substring(index + 1).equals(extension);
	}

	/**
	 * Moves the uploaded file to the filesystem and creates a csar file.
	 * 
	 * @param csarId
	 * @param inputStream
	 * @param name
	 */
	private void storeFile(long csarId, InputStream inputStream, String name) {
		CsarRepository csarRepository = new CsarRepository();
		CsarFileRepository csarFileRepository = new CsarFileRepository();
		FileSystemRepository fileSystemRepository = new FileSystemRepository();

		try {
			Csar csar = csarRepository.getbyId(csarId);
			if (null == csar) {
				String errorMsg = String.format("CSAR with ID: %d could not be found", csarId);
				this.addError(errorMsg);
				LOGGER.error(errorMsg);
				return;
			}

			FileSystem fileSystem = new FileSystem();
			File temporaryFile = fileSystem.saveTempFile(inputStream);

			HashedFile hashedFile = getHashedFileForTempFile(temporaryFile);

			Document document = prepareXml(fileSystem.getFile(hashedFile.getFilename()));

			parseServiceTemplateFromXml(csar, document);

			// if plans are not already set parse them directly from the XML
			if (hashedFile.getPlans() == null || hashedFile.getPlans().isEmpty()) {
				parsePlansFromXml(csar, hashedFile, document);
			}

			fileSystemRepository.save(hashedFile);

			this.csarFile = new CsarFile();
			this.csarFile.setCsar(csar);
			this.csarFile.setHashedFile(hashedFile);
			this.csarFile.setName(name);
			this.csarFile.setUploadDate(new Date());
			if (null != csarRepository.getLastCsarFile(csar)) {
				this.csarFile.setVersion(1 + csarRepository.getLastCsarFile(csar).getVersion());
			} else {
				this.csarFile.setVersion(1);
			}
			csarFileRepository.save(csarFile);

			csar.getCsarFiles().add(csarFile);

			csarRepository.save(csar);
		} catch (IllegalStateException | IOException | ParserConfigurationException | PersistenceException
				| SAXException | XPathExpressionException e) {
			this.addError(e.getMessage());
			LOGGER.error(e.getMessage());
			return;
		}
	}

	private Document prepareXml(File temporaryFile) throws IOException, ParserConfigurationException, SAXException {
		String entryDefinition = Extractor.match(Extractor.unzip(temporaryFile, TOSCA_METADATA_FILEPATH),
				ENTRY_DEFINITION_PATTERN);
		String xmlData = Extractor.unzip(temporaryFile, entryDefinition);

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
		return documentBuilder.parse(new ByteArrayInputStream(xmlData.getBytes()));
	}

	/**
	 * Parses the serviceTemplate from the xml inside the temporary file and
	 * updates it in the given CSAR
	 * 
	 * @param csar
	 * @param temporaryFile
	 * @param xpath
	 * @return
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws PersistenceException
	 *             if the serviceTemplate doesn't match the serviceTemplate of
	 *             the given CSAR
	 * @throws XPathExpressionException
	 */
	private void parseServiceTemplateFromXml(Csar csar, Document document) throws IOException,
			ParserConfigurationException, SAXException, PersistenceException, XPathExpressionException {

		NodeList elementsByTagNameNS = document.getElementsByTagNameNS(SERVICETEMPLATE_NS, SERVICETEMPLATE_LOCALNAME);
		Element serviceTemplate = (Element) elementsByTagNameNS.item(0);

		if (null == serviceTemplate) {
			throw new PersistenceException("Service Definition does not contain valid ServiceTemplate");
		}

		String serviceTemplateId = serviceTemplate.getAttribute("id");
		String namespace = serviceTemplate.getAttribute("targetNamespace");

		if (null == csar.getServiceTemplateId()) {
			csar.setServiceTemplateId(serviceTemplateId);
			csar.setNamespace(namespace);
			LOGGER.info("csar: service template id ({}) and namespace ({}) set", serviceTemplateId, namespace);
		} else if (!csar.getServiceTemplateId().equals(serviceTemplateId)
				|| (null == csar.getNamespace() && null != namespace && !namespace.equals(null))
				|| (null != csar.getNamespace() && !csar.getNamespace().equals(namespace))) {
			throw new PersistenceException(String.format(
					"File does not match csar service template id (%s: %s) or namespace (%s: %s).",
					csar.getServiceTemplateId(), serviceTemplateId, csar.getNamespace(), namespace));
		}
	}

	/**
	 * Parses the given XML File for Plans and adds them to the given hashedFile
	 * 
	 * @param csar
	 * @param hashedFile
	 * @param xpath
	 * @param nodeList
	 * @throws XPathExpressionException
	 * @throws PersistenceException
	 */

	private void parsePlansFromXml(Csar csar, HashedFile hashedFile, Document document)
			throws XPathExpressionException, PersistenceException {
		XPath xpath = XPathFactory.newInstance().newXPath();

		XPathExpression referenceExpression = xpath.compile(XPATH_PLANMODELREFERENCE_REFERENCE);

		NodeList elementsByTagNameNS = document.getElementsByTagNameNS(SERVICETEMPLATE_NS, SERVICETEMPLATE_LOCALNAME);
		Element serviceTemplate = (Element) elementsByTagNameNS.item(0);

		XPathExpression expression = xpath.compile(XPATH_PLANS_FROM_SERVICETEMPLATE);
		NodeList nodeList = (NodeList) expression.evaluate(serviceTemplate, XPathConstants.NODESET);

		for (int i = 0; i < nodeList.getLength(); i++) {
			Element item = (Element) nodeList.item(i);
			String planId = item.getAttribute("id");
			String fullZipPath = (String) referenceExpression.evaluate(item, XPathConstants.STRING);
			String extractedFileName = StringUtils.extractFilenameFromPath(fullZipPath);

			String planTypeFromXml = item.getAttribute("planType");
			String planNameFromXml = item.getAttribute("name");

			Plan.Type planType = null;
			if (BUILDPLAN_TYPE_TOSCA.equals(planTypeFromXml)) {
				planType = Plan.Type.BUILD;
			} else {
				planType = Plan.Type.OTHERS;
				LOGGER.debug("{} was mapped to PlanType OTHERS", planTypeFromXml);
			}

			Plan plan = new Plan(hashedFile, planId, planNameFromXml, extractedFileName, planType);
			CsarPlanRepository csarPlanRepository = new CsarPlanRepository();
			csarPlanRepository.save(plan);
			UploadCsarFileService.LOGGER.debug(
					"Extracted plan id: '{}' reference: '{}' from csar->id: '{}', ns: '{}' / name: '{}'", planId,
					extractedFileName, csar.getId(), csar.getNamespace(), csar.getName());

			hashedFile.addPlan(planId, plan);
		}
	}

	/**
	 * returns hashedFile matching given hash
	 * 
	 * @param temporaryFile
	 * @return
	 * @throws PersistenceException
	 * @throws ZipException
	 */
	private HashedFile getHashedFileForTempFile(File temporaryFile) throws PersistenceException {

		FileSystemRepository fileSystemRepository = new FileSystemRepository();
		FileSystem fileSystem = new FileSystem();

		try {
			ZipUtils.delete(temporaryFile, CSAR_REPOSITORY_FILENAME);
		} catch (ZipException e) {
			throw new PersistenceException(e);
		}

		String hash = fileSystem.generateHash(temporaryFile);
		HashedFile hashedFile = null;
		if (!fileSystemRepository.containsHash(hash)) {
			hashedFile = new HashedFile();
			File newFile = fileSystem.saveToFileSystem(temporaryFile);
			hashedFile.setFilename(UUID.fromString(newFile.getName()));
			hashedFile.setHash(hash);
			hashedFile.setSize(newFile.length());
			fileSystemRepository.save(hashedFile);
		} else {
			hashedFile = fileSystemRepository.getByHash(hash);
		}
		return hashedFile;
	}

	public CsarFile getResult() {
		super.logInvalidResultAccess("getResult");

		return this.csarFile;
	}

}
