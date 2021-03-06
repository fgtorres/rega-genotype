package rega.genotype.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rega.genotype.ApplicationException;
import rega.genotype.ngs.NgsFileSystem;
import rega.genotype.ngs.model.ConsensusBucket;
import rega.genotype.singletons.Settings;
import rega.genotype.ui.framework.widgets.ObjectListModel;

/**
 * Utility for samtools
 * See: http://samtools.sourceforge.net/ and http://biobits.org/samtools_primer.html
 * 
 * @author michael
 */
public class SamtoolsUtil {

	public static File samFile(final ConsensusBucket bucket, File jobDir) {
		return NgsFileSystem.samFile(jobDir, 
				bucket.getDiamondBucket(), bucket.getRefName());
	}

	public static File createSamFile(final ConsensusBucket bucket, File jobDir, 
			String pe1Name, String pe2Name) throws ApplicationException {
		String bwaPath = Settings.getInstance().getConfig().getGeneralConfig().getBwaCmd();
		File consensusFile = NgsFileSystem.consensusFile(jobDir, bucket.getDiamondBucket(),
				bucket.getRefName());

		File pe1 = NgsFileSystem.diamodPeFile(
				jobDir, bucket.getDiamondBucket(), pe1Name);
		File pe2 = NgsFileSystem.diamodPeFile(
				jobDir, bucket.getDiamondBucket(), pe2Name);

		File out = samFile(bucket, jobDir);

		File consensusDir = NgsFileSystem.consensusRefSeqDir(
				NgsFileSystem.consensusDir(jobDir, bucket.getDiamondBucket()),
				bucket.getRefName());

		// ./bwa index ref.fa
		String cmd = bwaPath + " index " + consensusFile.getAbsolutePath();
		System.err.println(cmd);
		Utils.executeCmd(cmd, consensusDir);

		// ./bwa mem ref.fa read1.fq read2.fq > aln-pe.sam.gz
		cmd = bwaPath + " mem " + consensusFile.getAbsolutePath()
				+ " " + pe1.getAbsolutePath() + " " + pe2.getAbsolutePath() 
				+ " > " + out.getAbsolutePath(); 
		System.err.println(cmd);
		Utils.execShellCmd(cmd, consensusFile);

		return out;
	}

	public static void samToBam(File samFile, File out, File workDir) throws ApplicationException {
		// samtools view -b -S -o alignments/sim_reads_aligned.bam alignments/sim_reads_aligned.sam
		String samtoolsCmd = Settings.getInstance().getConfig().getGeneralConfig().getSamtoolsCmd();
		String cmd = samtoolsCmd + " view -b -S -o " + out.getAbsolutePath() + " " + samFile.getAbsolutePath();
		System.err.println(cmd);
		Utils.executeCmd(cmd, workDir);
	}

	public static void sortBamFile(File bamFile, File out, File workDir) throws ApplicationException {
		// samtools sort alignments/sim_reads_aligned.bam alignments/sim_reads_aligned.sorted
		String samtoolsCmd = Settings.getInstance().getConfig().getGeneralConfig().getSamtoolsCmd();
		String cmd = samtoolsCmd + " sort " + bamFile.getAbsolutePath() + " -o " + out.getAbsolutePath();
		System.err.println(cmd);
		Utils.execShellCmd(cmd, workDir);
	}

	public static void createCovMap(File bamFile, File out, File workDir) throws ApplicationException {
		// samtools depth deduped_MA605.bam > deduped_MA605.coverage
		String samtoolsCmd = Settings.getInstance().getConfig().getGeneralConfig().getSamtoolsCmd();
		String cmd = samtoolsCmd + " depth " + bamFile.getAbsolutePath() + " > " + out.getAbsolutePath();
		System.err.println(cmd);
		Utils.execShellCmd(cmd, workDir);
	}

	public static void samToCovMap(File samFile, File out, File consensusWorkDir) throws ApplicationException {
		File bamFile = new File(consensusWorkDir, NgsFileSystem.ALINGMENT_BAM_FILE);
		File sortedBamFile = new File(consensusWorkDir, NgsFileSystem.ALINGMENT_BAM_SORTED_FILE);
		samToBam(samFile, bamFile, consensusWorkDir);
		sortBamFile(bamFile, sortedBamFile, consensusWorkDir);
		createCovMap(sortedBamFile, out, consensusWorkDir);
	}

	public static ObjectListModel<Integer> covMapModel(File covMapFile, int consensusLength) {
		BufferedReader br = null;
		String line = "";
		List<Integer> ans = new ArrayList<Integer>();
		try {
			br = new BufferedReader(new FileReader(covMapFile));
			while ((line = br.readLine()) != null) {
				String[] split = line.split("\t");
				if (split.length == 3) {
					try {
						int pos = Integer.parseInt(split[1]);
						if (ans.size() < pos) { 
							// fill 0 cov area
							ans.addAll(Collections.nCopies(pos - ans.size(), 0));
						}
						ans.add(Integer.parseInt(split[2]));
					} catch (NumberFormatException e) {
						e.printStackTrace(); // should not get here!!
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (ans.size() < consensusLength) { 
			ans.addAll(Collections.nCopies(consensusLength - ans.size(), 0));
		}
		return new ObjectListModel<Integer>(ans) {
			@Override
			public Object render(Integer t) {
				return t;
			}
		};
	}
}
