package rega.genotype.ui.util;

import java.io.File;
import java.util.EnumSet;

import rega.genotype.ngs.model.ConsensusBucket;
import rega.genotype.ngs.model.Contig;
import eu.webtoolkit.jwt.AlignmentFlag;
import eu.webtoolkit.jwt.WBrush;
import eu.webtoolkit.jwt.WColor;
import eu.webtoolkit.jwt.WPaintDevice;
import eu.webtoolkit.jwt.WPaintedWidget;
import eu.webtoolkit.jwt.WPainter;
import eu.webtoolkit.jwt.WPainterPath;

public class LinearCovMap {
	public static final double GENOM_HIGHT = 20;
	public static final double GENOM_WIDTH = 400;
	public static final int MARGIN = 15;

	public static File generalGenomeImage() {
		return new File("todo"); // TODO
	}

	public static File genomeImage(final ConsensusBucket bucket, final File workDir) {
		File imagesDir = new File(workDir, "genome-images");
		imagesDir.mkdirs();
		return new File(imagesDir, bucket.getDiamondBucket() + bucket.getRefName());
	}

	public static double scale(double genomeSize, double x) {
		double scaleFactor = GENOM_WIDTH / genomeSize;
		return (double)x * scaleFactor;
	}

	public static WPaintedWidget paint(final ConsensusBucket bucket, final File workDir) {		 
		//File out = genomeImage(bucket, workDir);

		WPaintedWidget paintedWidget = new WPaintedWidget() {
			@Override
			protected void paintEvent(WPaintDevice paintDevice) {
				WPainterPath genomePath = new WPainterPath();
				genomePath.addRect(MARGIN, MARGIN, GENOM_WIDTH, GENOM_HIGHT);
				genomePath.addRect(MARGIN * 0.25, GENOM_HIGHT * 0.25 + MARGIN, 
						MARGIN * 0.75, GENOM_HIGHT * 0.5);
				genomePath.addRect(MARGIN + GENOM_WIDTH, GENOM_HIGHT * 0.25 + MARGIN, 
						MARGIN * 0.75, GENOM_HIGHT * 0.5);

				WPainter painter = new WPainter(paintDevice);
				painter.drawPath(genomePath);
				painter.drawText(1, 1, MARGIN, MARGIN, EnumSet.of(AlignmentFlag.AlignLeft), "5'");
				painter.drawText(GENOM_WIDTH + MARGIN, 1, MARGIN, MARGIN, 
						EnumSet.of(AlignmentFlag.AlignLeft), "3'");
				painter.drawText(1, 1, MARGIN, MARGIN, EnumSet.of(AlignmentFlag.AlignLeft), "5'");
				painter.drawText(GENOM_WIDTH / 2 + MARGIN, 1, MARGIN, MARGIN, 
						EnumSet.of(AlignmentFlag.AlignCenter), bucket.getRefName());

				for (Contig contig: bucket.getContigs()) {
					WPainterPath gcontigPath = new WPainterPath();
					double start = scale(bucket.getConsensusLength(), 
							contig.getEndPosition() - contig.getLength());
					double width = scale(bucket.getConsensusLength(), contig.getLength());
					gcontigPath.addRect(MARGIN + start, MARGIN, width, GENOM_HIGHT);
					int alpha = Math.min(contig.getCov().intValue() * 10, 255);
					painter.setBrush(new WBrush(new WColor(50, 150, 50, alpha)));
					painter.drawPath(gcontigPath);
				}
			}
		};

		paintedWidget.update();
		paintedWidget.resize((int)(MARGIN * 2 + GENOM_WIDTH), 50);

		return paintedWidget;
	}
}
