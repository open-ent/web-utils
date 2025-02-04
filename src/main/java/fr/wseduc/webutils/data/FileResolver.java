package fr.wseduc.webutils.data;

import io.vertx.core.json.JsonObject;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class FileResolver {

	private static final ConcurrentHashMap<String, String> basePaths = new ConcurrentHashMap<>();

	private FileResolver() {}

	public void setBasePath(String module, JsonObject config) {
		setBasePath(module, config.getString("cwd"));
	}

	public void setBasePath(String module, String basePath) {
		if (basePath != null) {
			basePaths.put(module, basePath + (!basePath.endsWith(File.separator) ? File.separator : ""));
		} else {
			basePaths.put(module, "");
		}
	}

	private static class FileResolverHolder {
		private static final FileResolver instance = new FileResolver();
	}

	public static FileResolver getInstance() {
		return FileResolverHolder.instance;
	}

	public String getAbsolutePath(String module, String file) {
		if (file == null || file.isEmpty() || file.startsWith(File.separator)) return file;
		String currentBasePath = basePaths.get(module);
		return currentBasePath != null ? currentBasePath + file : file;
	}

	public static String absolutePath(String module, String file) {
		return FileResolver.getInstance().getAbsolutePath(module, file);
	}
}