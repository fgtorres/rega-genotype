package rega.genotype.ui.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import rega.genotype.singletons.Settings;

/**
 * Serve files on a url.
 * defined in web.xml
 * load-on-startup 2
 */
public class FileServlet extends HttpServlet {

	private static final long serialVersionUID = 71546732567L;
	private static String SERVLET_PATH;
	private static String TOOL_URL_PARAM = "toolUrl";
	private static String IMAGE_NAME_PARAM = "id";
	private static String FILE_EDITOR_PARAM = "file-editor";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String id = req.getParameter(IMAGE_NAME_PARAM);
		String toolUrl = req.getParameter(TOOL_URL_PARAM);
		String fileEditorUrl = req.getParameter(FILE_EDITOR_PARAM);

		File file;
		if (fileEditorUrl != null) 
			file = new File("./base-work-dir/job/file_editor/" + fileEditorUrl, id);
		else
			file = getFilePath(toolUrl, id);

		if (file == null || !file.exists()) {
			resp.setStatus(404);
			return;
		}

		resp.addHeader("Cache-Control", "max-age=2592000, private");

		// Stream file
		FileInputStream stream = new FileInputStream(file);
		byte b[] = new byte[stream.available()];
		stream.read(b);
		stream.close();
		resp.getOutputStream().write(b);
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		SERVLET_PATH = config.getServletContext().getContextPath() + "/files";
	}

	public static String getFileUrl(String toolUrl) {
		// Organism dir.
		return SERVLET_PATH + "?" + TOOL_URL_PARAM + "=" + toolUrl + "&" + IMAGE_NAME_PARAM + "=";
	}

	public static String getFileEditorUrl(String workDir) {
		// Organism dir.
		return SERVLET_PATH + "?" + FILE_EDITOR_PARAM + "=" + workDir + "&" + IMAGE_NAME_PARAM + "=";
	}

	private boolean isInDir(File file, File dir) {
		File f = file;
		while (f.getParent() != null) {
			if (f.getParent().equals(dir.getAbsolutePath()))
				return true;
			else {
				f = new File(f.getParent());
			}
		}
		return false;
	}
	
	/*
	 * Return the file path of the given id. The id should contains the
	 * extension of the file.
	 * expected id = {url path component}/path in organism dir
	 * return "" if id syntax is not correct.
	 */
	private File getFilePath(String toolUrl, String id) {
		File xmlPath;
		xmlPath = Settings.getInstance(getServletContext()).getXmlPath(toolUrl);

		if (xmlPath == null)
			return null;

		File ans = new File(xmlPath.getAbsolutePath() + id);

		if (!isInDir(ans, xmlPath)) {
			return null;
		}
		return ans;
	}
}
