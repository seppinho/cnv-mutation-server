package genepi.mut.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import genepi.io.table.reader.CsvTableReader;
import genepi.mut.objects.VariantLine;
import genepi.mut.objects.VariantResult;

public class RawFileAnalysermtDNA {

	private static Set<Integer> privMutationsL02 = new HashSet<Integer>(Arrays.asList(15372, 16183));

	private static Set<Integer> privMutationsL11 = new HashSet<Integer>(Arrays.asList(7076, 9462, 11150, 15236, 16129));

	private static Set<Integer> hotspots = new HashSet<Integer>(
			Arrays.asList(302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 315, 316, 3105, 3106, 3107));

	private boolean callDel;

	public ArrayList<QCMetric> calculateLowLevelForTest(String in, String refpath, String sangerpos, double level) {

		ArrayList<QCMetric> metrics = new ArrayList<QCMetric>();

		NumberFormat nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
		DecimalFormat df = (DecimalFormat) nf;
		df.setMinimumFractionDigits(2);
		df.setMaximumFractionDigits(3);
		Set<Integer> allPos = new TreeSet<Integer>();
		Set<Integer> sangerPos = new TreeSet<Integer>();
		Set<String> falsePositives = new TreeSet<String>();
		Set<String> both = new TreeSet<String>();
		List<Double> hetero = new ArrayList<Double>();

		CsvTableReader idReader = new CsvTableReader(in, '\t');

		Set<String> ids = new TreeSet<String>();
		while (idReader.next()) {
			ids.add(idReader.getString("SAMPLE"));
		}
		idReader.close();

		for (String id : ids) {

			CsvTableReader goldReader = new CsvTableReader(sangerpos, '\t');
			while (goldReader.next()) {
				sangerPos.add(goldReader.getInteger("Pos"));
				allPos.add(goldReader.getInteger("Pos"));
			}
			CsvTableReader cloudgeneReader = new CsvTableReader(in, '\t');
			int gold = sangerPos.size();

			falsePositives.clear();
			both.clear();

			int truePositiveCount = 0;
			int falsePositiveCount = 0;
			int trueNegativeCount = 0;
			int falseNegativeCount = 0;
			int foundBySanger = 0;
			hetero.clear();

			while (cloudgeneReader.next()) {

				VariantLine line = new VariantLine();

				line.parseLineFromFile(cloudgeneReader);

				if (id.equals(line.getId())) {

					if (!isHotspot(line.getPosition())) {

						if (!isSampleMutation(line.getPosition())) {

							int position = line.getPosition();

							if (checkBases(line)) {

								double hetLevel = VariantCaller.calcVariantLevel(line, line.getMinorBasePercentsFWD(),
										line.getMinorBasePercentsREV());

								VariantResult varResult = VariantCaller.determineLowLevelVariant(line,
										line.getMinorBasePercentsFWD(), line.getMinorBasePercentsREV(),
										line.getLlrFWD(), line.getLlrREV(), level, line.getMinorBaseFWD());

								varResult.setLevel(hetLevel);

								if (varResult.getType() == VariantCaller.LOW_LEVEL_VARIANT) {

									hetero.add(cloudgeneReader.getDouble("LEVEL"));

									if (sangerPos.contains(position)) {

										sangerPos.remove(position);
										truePositiveCount++;
										both.add(position + " (" + Math.abs(line.getLlrFWD()) + ")");

									} else {

										falsePositives.add(position + " (" + Math.abs(line.getLlrFWD()) + ")");
										falsePositiveCount++;

									}

								}

								else {

									if (!allPos.contains(position)) {

										trueNegativeCount++;

									}

									else {
										falseNegativeCount++;
									}
								}

							}
						}
					}
				}
			}

			cloudgeneReader.close();

			foundBySanger = sangerPos.size();

			System.out.println("  ID: " + id);

			System.out.println("  Correct hits : " + truePositiveCount + "/" + gold);

			System.out.println("    " + both);

			System.out.println("  Not correctly found: " + foundBySanger);

			System.out.println("    " + sangerPos);

			System.out.println("  Found additionally with Cloudgene: " + falsePositiveCount);

			System.out.println("    " + falsePositives);

			double sens = truePositiveCount / (double) (truePositiveCount + falseNegativeCount) * 100;
			double spec = trueNegativeCount / (double) (falsePositiveCount + trueNegativeCount) * 100;
			double prec = truePositiveCount / (double) (truePositiveCount + falsePositiveCount) * 100;

			System.out.println("");
			System.out.println("Precision\t" + df.format(prec));
			System.out.println("Sensitivity\t" + df.format(sens));
			System.out.println("Specificity\t" + df.format(spec));

			QCMetric metric = new QCMetric();
			metric.setId(id);
			metric.setSensitivity(sens);
			metric.setSpecificity(spec);
			metric.setPrecision(prec);

			metrics.add(metric);
		}
		return metrics;
	}

	public void analyseRaw(String in, String refpath, String sangerpos, double level) {

		NumberFormat nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
		DecimalFormat df = (DecimalFormat) nf;
		df.setMinimumFractionDigits(2);
		df.setMaximumFractionDigits(3);
		int minCoverage = 30;

		CsvTableReader cloudgeneReader = new CsvTableReader(in, '\t');

		while (cloudgeneReader.next()) {

			VariantLine line = new VariantLine();

			line.parseLineFromFile(cloudgeneReader);

			if (!isHotspot(line.getPosition())) {

				int position = line.getPosition();

				if (checkBases(line)) {

					double hetLevel = VariantCaller.calcVariantLevel(line, line.getMinorBasePercentsFWD(),
							line.getMinorBasePercentsREV());

					VariantResult varResult = VariantCaller.determineLowLevelVariant(line,
							line.getMinorBasePercentsFWD(), line.getMinorBasePercentsREV(), line.getLlrFWD(),
							line.getLlrREV(), level, line.getMinorBaseFWD());

					varResult.setLevel(hetLevel);

					if (varResult.getType() == VariantCaller.LOW_LEVEL_VARIANT) {

						System.out.println("Low Level Variant: " + line.getPosition());

					}

				}

				double hetLevel = VariantCaller.calcVariantLevel(line, line.getMinorBasePercentsFWD(),
						line.getMinorBasePercentsREV());

				System.out.println("pos " + position);

				System.out.println(hetLevel);

				System.out.println(1 - level);

				VariantResult varResult = VariantCaller.determineVariants(line);

				varResult.setLevel(hetLevel);

				if (varResult.getType() == VariantCaller.VARIANT) {

					System.out.println("Variant: " + line.getPosition());

				}

			}
		}

		cloudgeneReader.close();

	}

	public static boolean isSampleMutation(int pos) {
		return privMutationsL02.contains(pos) || privMutationsL11.contains(pos);
	}

	public static boolean isHotspot(int pos) {
		return hotspots.contains(pos);
	}

	public boolean isCallDel() {
		return callDel;
	}

	public void setCallDel(boolean callDel) {
		this.callDel = callDel;
	}

	private boolean checkBases(VariantLine line) {
		return (line.getMinorBaseFWD() == line.getMinorBaseREV() && line.getTopBaseFWD() == line.getTopBaseREV())
				|| ((line.getMinorBaseFWD() == line.getTopBaseREV() && line.getTopBaseFWD() == line.getMinorBaseREV()));
	}

}
