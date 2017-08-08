package genepi.cnv.steps;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.math.MathException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import genepi.cnv.align.AlignTool;
import genepi.cnv.pileup.PileupTool;
import genepi.cnv.sort.SortTool;
import genepi.cnv.util.QCMetric;
import genepi.cnv.util.RawFileAnalyser;
import genepi.cnv.util.TestCluster;
import genepi.cnv.util.WorkflowTestContext;
import genepi.hadoop.HdfsUtil;
import genepi.hadoop.common.WorkflowStep;
import genepi.io.FileUtil;
import genepi.io.text.LineReader;
import junit.framework.Assert;

public class PileupStepTest {

	public static final boolean VERBOSE = true;

	@BeforeClass
	public static void setUp() throws Exception {
		TestCluster.getInstance().start();
	}

	@AfterClass
	public static void tearDown() throws Exception {
		//TestCluster.getInstance().stop();
	}

	@Test
	public void PileupTestPE() throws IOException {

		String inputFolder = "test-data/mtdna/fastqpe/input";
		String archive = "test-data/mtdna/fastqpe/reference/rcrs.tar.gz";
		String hdfsFolder = "input";
		String type = "pe";

		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(hdfsFolder, archive, type);

		context.setInput("chunkLength", "0");

		// create step instance
		AlignTool align = new AlignnMock("files");
		context.setOutput("bwaOut", "cloudgene-bwaOutPe1");
		context.setOutput("outputBam", "outputBam1");
		context.setOutput("analyseOut", "analyseOut1");
		context.setOutput("variants2", "analyseOut2");

		boolean result = align.run(context);

		assertTrue(result);

		SortTool sort = new SortMock("files");
		result = sort.run(context);
		assertTrue(result);

		assertTrue(HdfsUtil.exists("outputBam1"));

		PileupTool pileUp = new PileupMock("files");
		result = pileUp.run(context);
		assertTrue(result);

		List<String> files = HdfsUtil.getFiles("analyseOut1");

		for (String file : files) {
			System.out.println(file);
		}

	}

	@Test
	public void Pileup1000GBamTest() throws IOException {

		String inputFolder = "test-data/mtdna/bam/input";
		String archive = "test-data/mtdna/bam/reference/rcrs.tar.gz";
		String hdfsFolder = "input";
		String type = "bam";
		
		Set<Integer> expected = new HashSet<Integer>(Arrays.asList(1456,2746,3200,12410,14071,14569,15463,16093,16360,10394,1438,152,15326,15340,16519,263,4769,750,8592,8860));
		
		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(hdfsFolder, archive, type);

		PileupTool pileUp = new PileupMock("files");
		context.setOutput("analyseOut", "analyseOutBAM");
		context.setOutput("variantsHdfs", "variantsHdfs");
		context.setOutput("variantsLocal", "variantsLocal");
		context.setOutput("baq", "true");
		
		boolean result = pileUp.run(context);
		assertTrue(result);

		String variants = "test-data/tmp/variants.txt";
		
		//HdfsUtil.merge(variants, "variantsHdfs",false);
		
		//String raw = "test-data/tmp/raw.txt";
		
		//HdfsUtil.merge(raw, "analyseOutBAM",false);
		
		LineReader reader = new LineReader("variantsLocal");
		HashSet<Integer> results = new HashSet<Integer>();
		
		//header
		reader.next();
		while(reader.next()){
			String[] splits = reader.get().split("\t");
			int pos = Integer.valueOf(splits[1]);
			results.add(pos);
		}
		
		assertEquals(true, results.equals(expected));
		
	
	


	}

	class AlignnMock extends AlignTool {

		private String folder;

		public AlignnMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	class SortMock extends SortTool {

		private String folder;

		public SortMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	class PileupMock extends PileupTool {

		private String folder;

		public PileupMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	protected boolean run(WorkflowTestContext context, WorkflowStep step) {
		step.setup(context);
		return step.run(context);
	}

	protected WorkflowTestContext buildContext(String input, String archive, String type) {

		File file = new File("test-data/tmp");
		if (file.exists()) {
			FileUtil.deleteDirectory(file);
		}
		file.mkdirs();

		WorkflowTestContext context = new WorkflowTestContext();

		context.setInput("input", input);
		context.setInput("inType", type);
		context.setVerbose(VERBOSE);
		context.setInput("archive", archive);
		context.setOutput("mapQuality", "20");
		context.setOutput("baseQuality", "20");
		context.setOutput("alignQuality", "30");
		context.setOutput("statistics", "statistics");
		context.setOutput("baq", "true");

		return context;

	}

	private void importInputdata(String folder, String input) {
		System.out.println("Import Data:");
		String[] files = FileUtil.getFiles(folder, "*.*");
		for (String file : files) {
			String target = HdfsUtil.path(input, FileUtil.getFilename(file));
			System.out.println("  Import " + file + " to " + target);
			HdfsUtil.put(file, target);
		}
	}
}
