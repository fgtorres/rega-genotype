package rega.genotype.ui.framework.widgets;

import rega.genotype.ui.framework.Constants.Permissions;
import eu.webtoolkit.jwt.WContainerWidget;

public class LineEdit extends WContainerWidget {
	private Permissions permissions;

	public LineEdit(Permissions permissions) {
		this.permissions = permissions;
	}
}
