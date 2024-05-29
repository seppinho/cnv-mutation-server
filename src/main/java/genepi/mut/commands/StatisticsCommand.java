package genepi.mut.commands;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import genepi.io.text.LineWriter;
import genepi.mut.App;
import genepi.mut.objects.StatisticsFile;
import genepi.mut.util.StatisticsFileUtil;
import genepi.mut.util.report.CloudgeneReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;

@Command(name = "stats", version = App.VERSION, description = "summarize statistics.")
public class StatisticsCommand implements Callable<Integer> {

	private static final int MIN_COVERAGE_PERCENTAGE = 50;
	private static final int MIN_MEAN_BASE_QUALITY = 10;
	private static final int MIN_MEAN_DEPTH = 50;

	List<String> allowed_contigs = new ArrayList<>(
			List.of("chrM", "MT", "chrMT", "rCRS", "NC_012920.1", "gi|251831106|ref|NC_012920.1|"));

	@Option(names = { "--input" }, description = "\"Input file", required = true)
	private String input;

	@Option(names = { "--mapping" }, description = "\"Mapping file", required = false)
	private String mapping = null;

	@Option(names = { "--output-excluded-samples" }, description = "\"Exclude file", required = true)
	private String output;

	@Option(names = { "--output-contig" }, description = "\"Exclude file", required = false)
	private String contigOut = "chrM";

	@Option(names = { "--tool" }, description = "\"Tool", required = false)
	private String tool = "mutserve";

	@Option(names = {
			"--baseQ" }, description = "Minimum Base Quality", required = false, showDefaultValue = Visibility.ALWAYS)
	private int baseQ = 20;

	@Option(names = {
			"--mapQ" }, description = "Minimum Map Quality", required = false, showDefaultValue = Visibility.ALWAYS)
	private int mapQ = 30;

	@Option(names = {
			"--alignQ" }, description = "Minimum Alignment Quality", required = false, showDefaultValue = Visibility.ALWAYS)
	private int alignQ = 30;

	@Option(names = {
			"--detection-limit" }, description = "Defined Detection Limit", required = false, showDefaultValue = Visibility.ALWAYS)
	private double detectionLimit = 0.01;

	@Option(names = {
			"--reference" }, description = "Reference for Variant Calling", required = false, showDefaultValue = Visibility.ALWAYS)
	private String reference = "rcrs";

	@Option(names = "--report", description = "Cloudgene Report Output", required = false)
	private String report = "cloudgene.report.json";

	private CloudgeneReport context = new CloudgeneReport();

	@Override
	public Integer call() throws IOException {

		context.setFilename(report);

		StringBuffer excludedSamplesFile = new StringBuffer();
		LineWriter writer = new LineWriter(output);
		LineWriter writerContig = new LineWriter(contigOut);

		StatisticsFileUtil stats = new StatisticsFileUtil();
		List<StatisticsFile> samples = stats.load(input);

		int countLowCoveredPercentage = 0;
		int countMeanBaseQuality = 0;
		int countNoReadGroups = 0;
		int countMeanDepth = 0;
		int countTooLowBaseQuality = 0;
		int countTooLowMapQuality = 0;
		int countMissingContigs = 0;
		int excludedSamples = 0;
		double lowestMeanDepth = -1;
		double highgestMeanDepth = -1;
		double lowestMeanBaseQuality = -1;
		double highestMeanBaseQuality = -1;
		List<String> contigs = new ArrayList<String>();
		StringBuffer text = new StringBuffer();

		if (mapping != null) {
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(mapping));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return -1;
			}
			String line;
			ArrayList<String> sampleList = new ArrayList<>();

			// header
			reader.readLine();
			
		

			while ((line = reader.readLine()) != null) {
				String sampleName = line.split("\t")[0];
				String fileName = line.split("\t")[1];
				if (sampleList.contains(sampleName)) {
					text.append("\n<b>Error:</b> Duplicate sample name for sample '" + sampleName + "' (Filename: "
							+ fileName + ".<br>mtDNA analysis cannot be started!");
					context.error(text.toString());
					reader.close();
					System.out.println("\n\nERROR: Duplicate sample name for sample '" + sampleName + "' (Filename: " + fileName+".");
					return -1;
				}

				sampleList.add(sampleName);
			}
			reader.close();
		}

		for (StatisticsFile sample : samples) {

			if (sample.getReadGroup() == null) {
				{
					countNoReadGroups++;
				}
			}

			if (sample.getSampleName() == null) {
				text.append("\n<b>Error:</b> Error in sample file name.<br>mtDNA analysis cannot be started!");
				context.error(text.toString());
				System.out.println("\n\nERROR: No sample file name has been detected.");
				return -1;
			}

			if (sample.getContig() != null) {

				if (!contigs.contains(sample.getContig())) {
					contigs.add(sample.getContig());
				}
			} else {
				countMissingContigs++;
				excludedSamples++;
				excludedSamplesFile.append(sample.getSampleName() + "\n");
			}

			if (sample.getMeanDepth() != -1) {

				if (sample.getMeanDepth() <= lowestMeanDepth || lowestMeanDepth == -1) {
					lowestMeanDepth = sample.getMeanDepth();
				}

				if (sample.getMeanDepth() > highgestMeanDepth) {
					highgestMeanDepth = sample.getMeanDepth();
				}
			}

			if (sample.getMeanBaseQuality() != -1) {

				if (sample.getMeanBaseQuality() <= lowestMeanBaseQuality || lowestMeanBaseQuality == -1) {
					lowestMeanBaseQuality = sample.getMeanBaseQuality();
				}

				if (sample.getMeanBaseQuality() > highestMeanBaseQuality) {
					highestMeanBaseQuality = sample.getMeanBaseQuality();
				}
			}

			if (sample.getCoveredPercentage() != -1 && sample.getCoveredPercentage() < MIN_COVERAGE_PERCENTAGE) {
				countLowCoveredPercentage++;
				excludedSamples++;
				excludedSamplesFile.append(
						sample.getSampleName() + "\t" + "Mean Coverage Percentage <" + MIN_COVERAGE_PERCENTAGE + "\n");
			} else if (sample.getMeanBaseQuality() != -1 && sample.getMeanBaseQuality() < MIN_MEAN_BASE_QUALITY) {
				countMeanBaseQuality++;
				excludedSamples++;
				excludedSamplesFile
						.append(sample.getSampleName() + "\t" + "Mean Base Quality < " + MIN_MEAN_BASE_QUALITY + "\n");
			} else if (sample.getMeanDepth() != -1 && sample.getMeanDepth() < MIN_MEAN_DEPTH) {
				countMeanDepth++;
				excludedSamples++;
				excludedSamplesFile.append(sample.getSampleName() + "\t" + "Mean Depth < " + MIN_MEAN_DEPTH + "\n");
			} else if (sample.getMeanBaseQuality() != -1 && sample.getMeanBaseQuality() < baseQ) {
				countTooLowBaseQuality++;
				excludedSamples++;
				excludedSamplesFile
						.append(sample.getSampleName() + "\t" + "Sample Mean Base Quality < " + baseQ + "\n");
			} else if (sample.getMeanMapQuality() != -1 && sample.getMeanMapQuality() < mapQ) {
				countTooLowBaseQuality++;
				excludedSamples++;
				excludedSamplesFile.append(sample.getSampleName() + "\t" + "Mapping Quality < " + mapQ + "\n");
			}

		}

		writer.write(excludedSamplesFile.toString());
		writer.close();

		text = new StringBuffer();

		text.append("<b>Variant Calling Parameters:</b> \n");
		text.append("Mode: " + tool + "\n");
		text.append("Reference: " + reference + "\n");
		text.append("Heteroplasmic Detection Limit: " + detectionLimit + "\n");
		text.append("Min Base Quality: " + baseQ + "\n");
		text.append("Min Mapping Quality: " + mapQ + "\n");
		text.append("Min Alignment Quality: " + alignQ + "\n");
		context.ok(text.toString());

		text = new StringBuffer();
		int validFiles = samples.size() - excludedSamples;
		text.append("<b>Statistics:</b> \n");
		text.append("Input Samples: " + samples.size() + "\n");
		text.append("Passed Samples: " + validFiles + "\n");

		if (contigs.size() > 1) {
			context.error("Different contigs have been detected");
			System.out.println("\n\nERROR: Different contigs have been detected for your input samples. Please upload them in different batches.");
			return -1;
		} else {
			text.append("Detected contig name: " + contigs.get(0) + "\n");
		}

		/*
		 * if (tool.equals("mutect2") || tool.equals("fusion")) {
		 * 
		 * boolean found = false; for (String contig : allowed_contigs) { if
		 * (contig.equals(contigs.get(0))) { found = true; } }
		 * 
		 * if (!found) { context.
		 * error("For Mutect2, please one of the following contig names for chromosome MT: "
		 * + allowed_contigs.toString()); return -1; }
		 * 
		 * }
		 */

		if (lowestMeanDepth != -1) {
			text.append("Min Mean Depth: " + lowestMeanDepth + "\n");
		}
		if (highgestMeanDepth != -1 && validFiles > 1) {
			text.append("Max Mean Depth: " + highgestMeanDepth + "\n");
		}

		if (lowestMeanDepth != -1) {
			text.append("Min Mean Base Quality: " + lowestMeanBaseQuality + "\n");
		}
		if (highgestMeanDepth != -1 && validFiles > 1) {
			text.append("Max Mean Base Quality: " + highestMeanBaseQuality + "\n");
		}

		context.ok(text.toString());

		text = new StringBuffer();

		if (countMissingContigs > 0) {
			text.append(countMissingContigs + " sample(s) with missing contigs have been excluded.");
		}

		writerContig.write(contigs.get(0));
		writerContig.close();

		if (countLowCoveredPercentage > 0) {
			text.append(countLowCoveredPercentage + " sample(s) with a coverage percentage of < "
					+ MIN_COVERAGE_PERCENTAGE + " have been excluded.");
		}
		if (countMeanBaseQuality > 0) {
			text.append(countMeanBaseQuality + " sample(s) with a mean base quality of < " + MIN_MEAN_BASE_QUALITY
					+ " have been excluded.");
		}

		if (countNoReadGroups > 0) {
			text.append("For " + countNoReadGroups + " sample(s) a readgroup tag (@RG) have been added");
		}

		if (countMeanDepth > 0) {
			text.append(countMeanDepth + " sample(s) with mean depth of < " + MIN_MEAN_DEPTH + " have been excluded.");
		}

		if (countTooLowBaseQuality > 0) {
			text.append(countTooLowBaseQuality
					+ " sample(s) have been removed where the mean base quality is lower then configured base quality ("
					+ baseQ + ").");
		}
		if (countTooLowMapQuality > 0) {
			text.append(countTooLowMapQuality
					+ " sample(s) have been removed where the mean base quality is lower then configured base quality ("
					+ mapQ + ").");
		}

		if (text.length() > 0) {
			context.warning(text.toString());
		}

		if (validFiles == 0) {
			context.error("No input samples passed the QC step.");
			System.out.println("\n\nERROR: No input samples passed the QC step.");
			return -1;
		} else {
			context.ok("Input Validation run succesfully, mtDNA analysis can be started.");
			return 0;
		}
		
	
	}

	public String formatTime(long timeInSeconds) {
		return String.format("%d min, %d sec", (timeInSeconds / 60), (timeInSeconds % 60));
	}

	public String getInput() {
		return input;
	}
	public void setInput(String input) {
		this.input = input;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

}
