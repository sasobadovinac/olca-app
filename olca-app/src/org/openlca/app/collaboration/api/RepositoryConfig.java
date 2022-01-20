package org.openlca.app.collaboration.api;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.openlca.core.database.IDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryConfig {

	private static final Logger log = LoggerFactory.getLogger(RepositoryConfig.class);
	public static final String GIT_DIR = ".git";
	public final String serverUrl;
	public final String repositoryId;
	public final String apiUrl;
	public final CredentialSupplier credentials;

	private RepositoryConfig(String serverUrl, String repositoryId, CredentialSupplier credentials) {
		this.serverUrl = serverUrl;
		this.repositoryId = repositoryId;
		this.apiUrl = serverUrl + "/ws";
		this.credentials = credentials;
	}

	@SuppressWarnings("resource")
	public static RepositoryConfig of(FileRepository repo, CredentialSupplier credentialSupplier) {
		try {
			var configs = new Git(repo).remoteList().call();
			var config = configs.stream()
					.filter(c -> c.getName().equals("origin"))
					.findFirst()
					.orElse(null);
			if (config == null || config.getURIs().isEmpty())
				throw new IllegalStateException("No remote URI configured");
			var uri = config.getURIs().get(0);
			var url = uri.toString();
			var splitIndex = url.substring(0, url.lastIndexOf("/")).lastIndexOf("/");
			var serverUrl = url.substring(0, splitIndex);
			var repositoryId = url.substring(splitIndex + 1);
			return new RepositoryConfig(serverUrl, repositoryId, credentialSupplier);
		} catch (Exception e) {
			log.error("Error loading git config", e);
			return null;
		}
	}

	public static File getGirDir(IDatabase database) {
		return new File(database.getFileStorageLocation(), GIT_DIR);
	}
	
}
