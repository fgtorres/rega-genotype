package rega.genotype.ngs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import rega.genotype.AbstractSequence;
import rega.genotype.ApplicationException;
import rega.genotype.FileFormatException;
import rega.genotype.NgsSequence;
import rega.genotype.NgsSequence.BucketData;
import rega.genotype.NgsSequence.Contig;
import rega.genotype.ParameterProblemException;
import rega.genotype.SequenceAlignment;
import rega.genotype.config.Config.ToolConfig;
import rega.genotype.config.NgsModule;
import rega.genotype.framework.async.LongJobsScheduler;
import rega.genotype.framework.async.LongJobsScheduler.Lock;
import rega.genotype.ngs.NgsProgress.BasketData;
import rega.genotype.ngs.NgsProgress.State;
import rega.genotype.ngs.QC.QcData;
import rega.genotype.ngs.QC.QcResults;
import rega.genotype.ngs.QC.QcResults.Result;
import rega.genotype.taxonomy.RegaSystemFiles;
import rega.genotype.taxonomy.TaxonomyModel;
import rega.genotype.tools.blast.BlastTool;
import rega.genotype.utils.BlastUtil;
import rega.genotype.utils.FileUtil;
import rega.genotype.utils.LogUtils;

/**
 * Contract long virus contigs from ngs output. 
 * Steps:QC (FastQC), pre-processing (Trimmomatic),
 * primary search (Diamond blast), assembly (Spades) 
 * 
 * @author michael
 */
public class NgsAnalysis {
	private Logger ngsLogger = null;
	private File workDir;
	private NgsModule ngsModule;
	private ToolConfig toolConfig;

	public NgsAnalysis(File workDir, NgsModule ngsModule, ToolConfig toolConfig){
		this.workDir = workDir;
		this.ngsModule = ngsModule;
		this.toolConfig = toolConfig;
		ngsLogger = LogUtils.createLogger(workDir);
	}

	/**
	 * Pre-process fastq sequences.
	 * By default use cutadapt
	 * Can be re-implemented.
	 * 
	 * @throws ApplicationException
	 */
	protected void preprocess() throws ApplicationException {
		// Preprocessing.cutadaptPreprocess(workDir);
		Preprocessing.trimomatic(workDir, ngsLogger);
	}

	/**
	 * Primary search: sort the short NGS sequences 
	 * By default use diamond blast
	 * Can be re-implemented.
	 * 
	 * @throws ApplicationException
	 */
	protected void primarySearch() throws ApplicationException {
		PrimarySearch.diamondSearch(workDir, ngsModule, ngsLogger);
	}

	/**
	 * assemble ngs data to long contigs
	 * 
	 * By default use Spades
	 * Can be re-implemented.
	 * 
	 * @throws ApplicationException
	 */
	protected File assemble(File sequenceFile1, File sequenceFile2, String virusName) throws ApplicationException {
		return Assemble.spadesAssemble(sequenceFile1, sequenceFile2, workDir, virusName, ngsModule, ngsLogger);
	}

	/**
	 * Contract long virus contigs from ngs output. 
	 * Steps:QC (FastQC), pre-processing (Trimmomatic),
	 * primary search (Diamond blast), assembly (Spades)
	 * @param workDir
	 */
	public boolean analyze() {
		NgsProgress ngsProgress = NgsProgress.read(workDir);

		// QC

		ngsProgress.setState(State.QC);
		ngsProgress.save(workDir);

		boolean needPreprocessing = true;// TODO true -> always preproces // for now we base it only on adapter content.
		File fastqDir = NgsFileSystem.fastqDir(workDir);
		try {
			QC.qcReport(fastqDir.listFiles(),
					new File(workDir, NgsFileSystem.QC_REPORT_DIR),
					workDir);

			List<QcResults> qcresults = QC.getResults(new File(workDir, NgsFileSystem.QC_REPORT_DIR));
			for (QcResults qcr: qcresults) {
				if (qcr.adapterContent == Result.Fail)
					needPreprocessing = true;
			}

			QcData qcData = new QC.QcData(QC.qcReportFile(workDir));
			ngsProgress.setReadCountInit(qcData.getTotalNumberOfReads());
			ngsProgress.setReadLength(qcData.getReadLength());
		} catch (ApplicationException e1) {
			e1.printStackTrace();
			ngsProgress.setErrors("QC failed: " + e1.getMessage());
			ngsProgress.save(workDir);
			cleanBigData();
			return false;
		}

		ngsProgress.setSkipPreprocessing(!needPreprocessing);
		ngsProgress.setState(State.Preprocessing);
		ngsProgress.save(workDir);

		// pre-process
		if (needPreprocessing) {
			try {
				preprocess();
			} catch (ApplicationException e) {
				e.printStackTrace();
				ngsProgress.setErrors("Preprocessing failed: " + e.getMessage());
				ngsProgress.save(workDir);
				cleanBigData();
				return false;
			}

			File preprocessed1 = NgsFileSystem.preprocessedPE1(workDir);
			File preprocessed2 = NgsFileSystem.preprocessedPE2(workDir);

			ngsProgress = NgsProgress.read(workDir);
			ngsProgress.setState(State.QC2);
			ngsProgress.save(workDir);

			// QC 2

			try {
				QC.qcReport(new File[] {preprocessed1, preprocessed2}, 
						new File(workDir, NgsFileSystem.QC_REPORT_AFTER_PREPROCESS_DIR),
						workDir);

				QcData qcData = new QC.QcData(QC.qcPreprocessedReportFile(workDir));
				ngsProgress.setReadCountAfterPrepocessing(qcData.getTotalNumberOfReads());
				ngsProgress.setReadLength(qcData.getReadLength());
			} catch (ApplicationException e1) {
				e1.printStackTrace();
				ngsProgress.setErrors("QC failed: " + e1.getMessage());
				ngsProgress.save(workDir);
				cleanBigData();
				return false;
			}
		} else {
			ngsProgress.setReadCountAfterPrepocessing(ngsProgress.getReadCountInit());
		}

		ngsProgress.save(workDir);
		
		// diamond blast

		try {
			primarySearch();
		} catch (ApplicationException e) {
			e.printStackTrace();
			ngsProgress.setErrors("primary search failed: " + e.getMessage());
			ngsProgress.save(workDir);
			cleanBigData();
			return false;
		}

		boolean ans = assembleAll();

		cleanBigData();
		return ans;
	}

	/**
	 * Delete large ngs files from work dir.
	 */
	public void cleanBigData() {
		if (!workDir.exists() || workDir.listFiles() == null)
			return;
		File preprocessedDir = NgsFileSystem.preprocessedDir(workDir);
		File fastqDir = NgsFileSystem.fastqDir(workDir);
		// TODO: diamond results are useful only for testing 
		//File diamondDBDir = new File(workDir, NgsFileSystem.DIAMOND_BLAST_DIR);
		// delete all html files
		for (File f: workDir.listFiles())
			if (f.isFile() && f.getName().endsWith(".html"))
				f.delete();

		try {
			if (fastqDir.exists())
				FileUtils.deleteDirectory(fastqDir);
			if (preprocessedDir.exists())
				FileUtils.deleteDirectory(preprocessedDir);
//			if (diamondDBDir.exists())
//				FileUtils.deleteDirectory(diamondDBDir);
		} catch (IOException e) {
			e.printStackTrace();
			// leave it
		}
	}

	public BlastTool startIdentification(NgsProgress ngsProgress) {
		BlastTool tool;
		String traceFile = workDir.getAbsolutePath() + File.separatorChar + "result.xml";
		try {
			tool = new BlastTool(toolConfig, workDir);
			tool.startTracer(traceFile);
			tool.formatDB();
		} catch (IOException e) {
			e.printStackTrace();
			ngsProgress.setErrors("Identification - failed to init blast tool: " + e. getMessage());
			ngsProgress.save(workDir);
			return null;
		} catch (ParameterProblemException e) {
			e.printStackTrace();
			ngsProgress.setErrors("Identification - failed to init blast tool: " + e. getMessage());
			ngsProgress.save(workDir);
			return null;
		} catch (FileFormatException e) {
			e.printStackTrace();
			ngsProgress.setErrors("Identification - failed to init blast tool: " + e. getMessage());
			ngsProgress.save(workDir);
			return null;
		} catch (ApplicationException e) {
			e.printStackTrace();
			ngsProgress.setErrors("Identification - failed to init blast tool: " + e. getMessage());
			ngsProgress.save(workDir);
			return null;
		}
		return tool;
	}
	
	public boolean assembleAll() {
		NgsProgress ngsProgress = NgsProgress.read(workDir); // primarySearch may update NgsProgress with diamond results.
		BlastTool tool = startIdentification(ngsProgress);
		if (tool == null)
			return false;

		ngsProgress.setState(State.Spades);
		ngsProgress.save(workDir);
		
		// spades
		Lock jobLock = LongJobsScheduler.getInstance().getJobLock(workDir);

		File dimondResultDir = new File(workDir, NgsFileSystem.DIAMOND_RESULT_DIR);
		for (File d: dimondResultDir.listFiles()){
			assembleVirus(d, tool);
		}

		jobLock.release();

		tool.stopTracer();
		File done = new File(workDir.getAbsolutePath()+File.separatorChar+"DONE");
		try {
			FileUtil.writeStringToFile(done, System.currentTimeMillis()+"");
		} catch (IOException e) {
			e.printStackTrace();
			ngsProgress.setErrors("Identification - could not write DONE file: " + e. getMessage());
			ngsProgress.save(workDir);
		}

		File sequences = new File(workDir, NgsFileSystem.SEQUENCES_FILE);
		if (!sequences.exists())
			ngsProgress.setErrors("No assembly results.");
		else
			ngsProgress.setState(State.FinishedAll);

		ngsProgress.save(workDir);

		return true;
	}

	public boolean assembleVirus(File virusDiamondDir, BlastTool tool) {
		if (!virusDiamondDir.isDirectory())
			return false;

		NgsProgress ngsProgress = NgsProgress.read(workDir);

		String fastqPE1FileName = NgsFileSystem.fastqPE1(workDir).getName();
		String fastqPE2FileName = NgsFileSystem.fastqPE2(workDir).getName();

		File sequenceFile1 = new File(virusDiamondDir, fastqPE1FileName);
		File sequenceFile2 = new File(virusDiamondDir, fastqPE2FileName);

		BasketData basketData = ngsProgress.getDiamondBlastResults().get(virusDiamondDir.getName());
		if (ngsModule.getMinReadsToStartAssembly() > basketData.getReadCountTotal())
			return false; // no need to assemble if there is not enough reads.

		try {
			long startAssembly = System.currentTimeMillis();

			File assembledFile = assemble(
					sequenceFile1, sequenceFile2, virusDiamondDir.getName());
			if (assembledFile == null)
				return false;

			long endAssembly = System.currentTimeMillis();
			ngsLogger.info("assembled " + virusDiamondDir.getName() + " = " + (endAssembly - startAssembly) + " ms");

			// fill sequences.xml'
			File sequences = new File(workDir, NgsFileSystem.SEQUENCES_FILE);
			if (!sequences.exists())
				try {
					sequences.createNewFile();
				} catch (IOException e1) {
					e1.printStackTrace();
					ngsProgress.setErrors("assemble failed, could not create sequences.xml");
					ngsProgress.save(workDir);
					return false;
				}

			SequenceAlignment consensusAlignment = new SequenceAlignment(new FileInputStream(assembledFile), SequenceAlignment.FILETYPE_FASTA, SequenceAlignment.SEQUENCE_DNA);

			File ncbiVirusesFasta = RegaSystemFiles.ncbiVirusesFileAnnotated();
			if (!ncbiVirusesFasta.exists())
				throw new ApplicationException("Ncbi Viruses Db Path needs to be set in global settings");

			workDir.mkdirs();
			String virusName = virusDiamondDir.getName();
			File virusConsensusDir = NgsFileSystem.consensusDir(workDir, virusName);
			SequenceAlignment refs = detectRefs(virusConsensusDir, consensusAlignment, ncbiVirusesFasta);

			// FIXME:
			//  - probably should change the cutoff for the alignment, relative to length?

			File consensusInputContigs = assembledFile;

			for (AbstractSequence ref : refs.getSequences()) {
				ngsLogger.info("Trying with " + ref.getName() + " " + ref.getDescription());
				String refseqName = ref.getName().replaceAll("\\|", "_");
				File refWorkDir = NgsFileSystem.consensusRefSeqDir(virusConsensusDir, refseqName);
				
				File alingment = SequenceToolMakeConsensus.consensusAlign(consensusInputContigs, ref, refWorkDir, ngsModule, ngsLogger);
				File consensus = SequenceToolMakeConsensus.makeConsensus(alingment, refWorkDir, ngsModule, ngsLogger);

				consensusInputContigs = NgsFileSystem.consensusUnusedContigsFile(refWorkDir); // next time use only what was not used buy the first ref.

				// add virus taxonomy id to every consensus contig name, save sequence metadata.

				SequenceAlignment sequenceAlignment = new SequenceAlignment(
						new FileInputStream(consensus), 
						SequenceAlignment.FILETYPE_FASTA, 
						SequenceAlignment.SEQUENCE_DNA);

				int i = 0;
				for (AbstractSequence s: sequenceAlignment.getSequences()) {
					String[] split = fastqPE1FileName.split("_");
					String fastqFileId = (split.length > 0) ? split[0] : fastqPE1FileName;
					String refAC = "AC";
					if (refseqName.contains("_ref_"))
						refAC = refseqName.split("_ref_")[0];
					String bucket = virusName;

					String name = refAC + "__" + i + " " + s.getName();
					String description = fastqFileId;

					BucketData bucketData = new BucketData(bucket, ref.getName(), ref.getDescription(), 
							ref.getLength());

					List<Contig> contigs = SequenceToolMakeConsensus.readCotigsData(refWorkDir);
					if (!contigs.isEmpty()){
						NgsSequence ngsSequence = new NgsSequence(name, false, description, s.getSequence(),
								null, bucketData, contigs);

						tool.analyze(ngsSequence);
						i++;
					}
				}

				ngsLogger.info("Created " + sequenceAlignment.getSequences().size() + " contigs");

				ngsProgress.save(workDir);

				consensus.delete();
				sequenceAlignment.writeOutput(new FileOutputStream(consensus),
						SequenceAlignment.FILETYPE_FASTA);
	
				ngsLogger.info("consensus " + virusDiamondDir.getName() + " = " + (System.currentTimeMillis() - endAssembly) + " ms");
	
				FileUtil.appendToFile(consensus, sequences);
			}
		} catch (Exception e) {
			e.printStackTrace();
			ngsProgress.getSpadesErrors().add("assemble failed." + e.getMessage());
			ngsProgress.save(workDir);
			return false;
		}

		return true;
	}

	/**
	 * Detect for given genus a list of reference sequences based on long contigs.
	 * The sequences are stored in the consensus dir.
	 */
	private SequenceAlignment detectRefs(File virusDir,
			SequenceAlignment contigs, File ncbiVirusesFasta)
			throws ApplicationException, IOException, InterruptedException,
			ParameterProblemException, FileFormatException,
			FileNotFoundException {

		SequenceAlignment refs = new SequenceAlignment();
		final Map<String, Double> refNameScoreMap = new HashMap<String, Double>(); // make sure not to add same seq 2 times.

		for (AbstractSequence contig : contigs.getSequences()) {
			if (contig.getLength() < ngsModule.getRefMinContigLength())
				continue;

			//File consensusContigDir = NgsFileSystem.consensusContigDir(virusDir, contig.getName());
			File reference = NgsFileSystem.consensusRefFile(virusDir);
			virusDir.mkdirs();

			Double matchScore = BlastUtil.computeBestRefSeq(contig, virusDir,
					reference, ncbiVirusesFasta, ngsModule.getRefMaxBlastEValue(),
					ngsModule.getRefMinBlastBitScore(), ngsLogger);

			if (matchScore != null) {
				SequenceAlignment ref = new SequenceAlignment(new FileInputStream(reference),
						SequenceAlignment.FILETYPE_FASTA, SequenceAlignment.SEQUENCE_DNA);
				AbstractSequence as = ref.getSequences().get(0);

				String bucketTxId = virusDir.getName().split("_")[0];
				String refTxId = RegaSystemFiles.taxonomyIdFromAnnotatedNcbiSeq(as.getDescription());

				if (refTxId != null && !bucketTxId.equals(TaxonomyModel.VIRUSES_TAXONOMY_ID)) {
					List<String> refAncestorTaxa = TaxonomyModel.getInstance().getHirarchyTaxonomyIds(refTxId);
					if (!refAncestorTaxa.contains(bucketTxId)){
						ngsLogger.info("Seq: " + as.getName() + " " + as.getDescription() + " not processed because bucket "
								+ bucketTxId + " is not ancestor - not in : " + Arrays.toString(refAncestorTaxa.toArray()));
						continue;
					}
				}
				Double prevScore = refNameScoreMap.get(as.getName());
				if (prevScore != null)
					refNameScoreMap.put(as.getName(), Math.max(prevScore, matchScore));
				else {
					refs.addSequence(as);
					refNameScoreMap.put(as.getName(), matchScore);
				}
			}
		}

		// order by best score.
		Collections.sort(refs.getSequences(), new Comparator<AbstractSequence>() {
			public int compare(AbstractSequence as1, AbstractSequence as2) {
				Double score1 = refNameScoreMap.get(as1.getName());
				Double score2 = refNameScoreMap.get(as2.getName());
				return -score1.compareTo(score2);
			}
		});

		return refs;
	}
}
