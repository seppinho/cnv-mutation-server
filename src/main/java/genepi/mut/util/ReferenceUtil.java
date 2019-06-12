package genepi.mut.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;

public class ReferenceUtil {

	public enum Reference {
		hg19, rcrs, precisionId, UNKNOWN, MISLEADING, LPA
	};

	private static Set<Integer> hotSpots = new HashSet<Integer>(
			Arrays.asList(302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 315, 316, 3105, 3106, 3107));

	// for BAQ calculation only needed for mtDNA!
	public static String getValidReferenceNameForBaq(int length) {
		String alteredRef = null;

		switch (length) {
		case 16569:
			alteredRef = "rCRS";
			break;
		case 16571:
			alteredRef = "gi|17981852|ref|NC_001807.4|";
		}

		return alteredRef;
	}

	public static int getTagFromSamRecord(List<SAMTagAndValue> attList, String att) {
		int value = 30;
		for (SAMTagAndValue member : attList) {
			if (member.tag.equals(att))
				value = (int) member.value;
		}
		return value;
	}

	public static String readInReference(String file) {
		StringBuilder stringBuilder = null;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line = null;
			stringBuilder = new StringBuilder();

			while ((line = reader.readLine()) != null) {

				if (!line.startsWith(">"))
					stringBuilder.append(line);

			}

			reader.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return stringBuilder.toString();
	}

	public static boolean ismtDNAHotSpot(int pos) {
		return hotSpots.contains(pos);
	}

	public static String findFileinDir(File reference, String suffix) {
		String refPath = null;
		if (reference.isDirectory()) {
			File[] files = reference.listFiles();
			for (File i : files) {
				if (i.getName().endsWith(suffix)) {
					refPath = i.getAbsolutePath();
				}
			}
		} else {
			System.out.println(reference + " not a directory");
		}
		System.out.println("path " + refPath);
		return refPath;
	}

	public static Reference determineReference(File file) {

		Reference ref = Reference.UNKNOWN;

		final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
				.open(file);

		SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();

		for (SAMSequenceRecord record : dict.getSequences()) {
			if (record.getSequenceLength() == 16571) {
				ref = Reference.hg19;
			}
			if (record.getSequenceLength() == 16569) {
				ref = Reference.rcrs;
			}
			if (record.getSequenceLength() == 16649) {
				ref = Reference.precisionId;
			}
			if (record.getSequenceLength() == 5104) {
				ref = Reference.LPA;
			}
		}

		try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ref;
	}

}
