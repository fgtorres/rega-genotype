package rega.genotype.ui.framework;

import net.sf.witty.wt.WContainerWidget;
import net.sf.witty.wt.WText;

public class Header extends WContainerWidget{
	public Header(WContainerWidget parent){
		super(parent);
		init();
	}
	
	private void init(){
		new WText(tr("header.title"),this);
	}
}
