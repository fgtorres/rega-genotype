package rega.genotype.ui.admin;

import rega.genotype.ui.admin.config.ToolConfigTable;
import eu.webtoolkit.jwt.WApplication;
import eu.webtoolkit.jwt.WEnvironment;
import eu.webtoolkit.jwt.WLink;
import eu.webtoolkit.jwt.WXmlLocalizedStrings;

/**
 * Determine what widget to show in admin area.
 * 
 * @author michael
 */
public class AdminApplication extends WApplication{
	public static final String ADMIN_BASE_PATH = "admin";
	
	public AdminApplication(WEnvironment env) {
		super(env);
		
		WXmlLocalizedStrings resources = new WXmlLocalizedStrings();
		resources.use("/rega/genotype/ui/i18n/resources/common_resources");
		setLocalizedStrings(resources);

		useStyleSheet(new WLink("../../style/genotype-rivm.css"));
		useStyleSheet(new WLink("../../style/genotype-rivm-ie.css"), "IE lte 7");
		
		// For now it is simple.
		new AdminNavigation(getRoot());
	}

	public static AdminApplication getAppInstance() {
		return (AdminApplication) WApplication.getInstance();
	}
}