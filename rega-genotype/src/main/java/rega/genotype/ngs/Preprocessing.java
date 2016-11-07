package rega.genotype.ngs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import rega.genotype.ApplicationException;
import rega.genotype.singletons.Settings;

public class Preprocessing{
	/**
	 * cutadapt -b ADAPTER -o output.fastq ~/install/fasta-examples/hiv_1.fastq
	 * @param fastqFile
	 * @param workDir - the virus job dir
	 * @param outFile - 
	 * @throws ApplicationException
	 */
	public static void cutAdapters(File fastqFile, File outFile, File workDir) throws ApplicationException {
		String cmd = Settings.getInstance().getConfig().getGeneralConfig().getCutAdaptCmd();
		cmd += " -b ADAPTER -o " + outFile.getAbsolutePath() + " " + fastqFile.getAbsolutePath();

		System.err.println(cmd);

		NgsFileSystem.executeCmd(cmd, workDir);
	}

	/**
	 * Execute pre-processing from command line. (Any pre-processing software can be used this way)
	 * 
	 * When using this function you should know fastq files are stored in NgsFileSystem.FASTQ_FILES_DIR
	 * The result (pre-processed) files need to be stored in 
	 * NgsFileSystem.PREPROCESSED_PE1_DIR and NgsFileSystem.PREPROCESSED_PE2_DIR
	 * Then the next step knows where to find them.
	 * @See NgsFileSystem documentation for more information.
	 * 
	 * @param cmd 
	 * @param workDir - the virus job dir
	 * @throws ApplicationException
	 */
	public static void generalPreprocessing(String cmd, File workDir) throws ApplicationException {
		NgsFileSystem.executeCmd(cmd, workDir);
	}

	/**
	 * cutadapt -a ADAPTER_FWD -A ADAPTER_REV -o out.1.fastq -p out.2.fastq reads.1.fastq reads.2.fastq
	 * @param workDir - the virus job dir
	 * @throws ApplicationException
	 */
	public static void cutadaptPreprocess(File workDir) throws ApplicationException {
		File fastqPE1 = NgsFileSystem.fastqPE1(workDir);
		File fastqPE2 = NgsFileSystem.fastqPE2(workDir);
		if (fastqPE1 == null || fastqPE2 == null)
			return;

		File preprocessed1 = NgsFileSystem.createPreprocessedPE1(workDir, fastqPE1.getName());
		File preprocessed2 = NgsFileSystem.createPreprocessedPE2(workDir, fastqPE2.getName());

		String cmd = Settings.getInstance().getConfig().getGeneralConfig().getCutAdaptCmd();
		cmd += " -b ADAPTER_FWD -B ADAPTER_REV " 
				+ " -o " + preprocessed1.getAbsolutePath()
				+ " -p " + preprocessed2.getAbsolutePath()
				+ " " + fastqPE2.getAbsolutePath() + " " + fastqPE2.getAbsolutePath();

		NgsFileSystem.executeCmd(cmd, workDir);

		if (NgsFileSystem.preprocessedPE1(workDir) == null
				|| NgsFileSystem.preprocessedPE2(workDir) == null)
			throw new ApplicationException("Cutadapt output was not saved.");
	}

	/**
	 * preprocess fastq file with trimmomatic.
	 * args = ILLUMINACLIP:adapters.fasta:2:10:7:1 LEADING:10 TRAILING:10  MINLEN:5
	 * @param sequenceFile
	 * @param workDir - the virus job dir
	 * @return preprocessed fastq file
	 * @throws ApplicationException
	 * TODO: delete - not used
	 */
	public static List<File> preprocessTrimomatic(File sequenceFile1, File sequenceFile2, File workDir) throws ApplicationException {
		String trimmomaticPath = "TODO";//TODO
		
		String trimmomaticCmd = "java -Xmx1000m -jar " + trimmomaticPath + " PE -threads 1 ";
		String trimmomaticOptions = " ILLUMINACLIP:TruSeq2-SE.fa:2:30:10 LEADING:10 TRAILING:10 SLIDINGWINDOW:4:20 MINLEN:50";

		String inputFileNames = sequenceFile1.getAbsolutePath() + " " + sequenceFile2.getAbsolutePath();

		File preprocessedDir = new File(workDir, NgsFileSystem.PREPROCESSED_DIR);
		preprocessedDir.mkdirs();
		NgsFileSystem.preprocessedPE1(workDir);
		
		File paired1 = NgsFileSystem.createPreprocessedPE1(workDir, sequenceFile1.getName());
		File paired2 = NgsFileSystem.createPreprocessedPE1(workDir, sequenceFile2.getName());

		File unpaired1 = new File(preprocessedDir, NgsFileSystem.PREPROCESSED_FILE_NAMR_UNPAIRD + sequenceFile1.getName());
		File unpaired2 = new File(preprocessedDir, NgsFileSystem.PREPROCESSED_FILE_NAMR_UNPAIRD + sequenceFile2.getName());

		String outoutFileNames = paired1.getAbsolutePath()
				+ " " + unpaired1 .getAbsolutePath()
				+ " " + paired2.getAbsolutePath()
				+ " " + unpaired2.getAbsolutePath();

		String cmd = trimmomaticCmd + " " + inputFileNames + " " + outoutFileNames + " " + trimmomaticOptions;

		NgsFileSystem.executeCmd(cmd, workDir);

		List<File> ans = new ArrayList<File>();
		ans.add(paired1);
		ans.add(paired2);
		ans.add(unpaired1);
		ans.add(unpaired2);
		return ans;
	}
}