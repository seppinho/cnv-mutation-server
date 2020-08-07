package genepi.mut.pileup;

import genepi.hadoop.CacheStore;
import genepi.hadoop.PreferenceStore;
import genepi.mut.objects.BasePosition;
import genepi.mut.objects.BasePositionHadoop;
import genepi.mut.util.ReferenceUtil;
import htsjdk.samtools.SAMRecord;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.seqdoop.hadoop_bam.FileVirtualSplit;
import org.seqdoop.hadoop_bam.SAMRecordWritable;

public class PileupMapper extends Mapper<LongWritable, SAMRecordWritable, Text, BasePositionHadoop> {

	enum Counters {

		GOOD_MAPPING, BAD_MAPPING, BAD_QUALITY, GOOD_QUALITY, BAD_ALIGNMENT_SCORE, WRONG_REF, INVALID_READ, INVALID_FLAGS

	}

	BamAnalyser analyser;

	String filename;

	int mapQual;

	int alignQual;
	
	int minCoverage;
	
	boolean deletions;
	
	boolean insertions;

	protected void setup(Context context) throws IOException, InterruptedException {

		// required for BAM splits
		if (context.getInputSplit().getClass().equals(FileVirtualSplit.class)) {
			filename = ((FileVirtualSplit) context.getInputSplit()).getPath().getName().replace(".bam", "");
		} else {
			filename = ((FileSplit) context.getInputSplit()).getPath().getName().replace(".bam", "").replace(".sam", "")
					.replace(".cram", "");
		}
		
		//replace all non digits/chars with an underscore
		filename = filename.replaceAll("\\W+", "_");

		CacheStore cache = new CacheStore(context.getConfiguration());

		PreferenceStore store = new PreferenceStore(context.getConfiguration());

		String version = store.getString("server.version");

		int baseQual = context.getConfiguration().getInt("baseQual", 20);
		
		mapQual = context.getConfiguration().getInt("mapQual", 20);

		alignQual = context.getConfiguration().getInt("alignQual", 30);
		
		boolean baq = context.getConfiguration().getBoolean("baq", true);
		
		String refAsArchive = cache.getArchive("reference");
		
		File referencePath = new File(refAsArchive);

		String fastaPath = ReferenceUtil.findFileinDir(referencePath, ".fasta");
		
		analyser = new BamAnalyser(filename, fastaPath, baseQual, mapQual, alignQual, baq, version);
		
		//default is to ignore deletions
		deletions = context.getConfiguration().getBoolean("deletions", false);
		//default is to ignore deletions
		insertions = context.getConfiguration().getBoolean("insertions", false);

	}

	@Override
	protected void cleanup(Context context) throws IOException, InterruptedException {

		BasePositionHadoop baseHadoop = new BasePositionHadoop();
		
		HashMap<Integer, BasePosition> counts = analyser.getCounts();

		Text outKey = new Text();

		for (int key : counts.keySet()) {

			BasePosition basePos = counts.get(key);

			outKey.set(key+"");

			baseHadoop.setBasePosition(basePos);
			
			context.write(outKey, baseHadoop);

		}

	}

	public void map(LongWritable key, SAMRecordWritable value, Context context)
			throws IOException, InterruptedException {
		try {

			countStats(context, value.get());
					
			//analyse SAM read			
			analyser.analyseRead(value.get());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void countStats(Context context, SAMRecord samRecord) throws Exception {

		context.getCounter("mtdna", "OVERALL-READS").increment(1);

		if (samRecord.getMappingQuality() < mapQual) {
			context.getCounter("mtdna", "FILTERED").increment(1);
			context.getCounter("mtdna", "BAD-MAPPING").increment(1);
			return;
		}

		if (samRecord.getReadUnmappedFlag()) {
			context.getCounter("mtdna", "FILTERED").increment(1);
			context.getCounter("mtdna", "UNMAPPED").increment(1);
			return;
		}

		if (samRecord.getDuplicateReadFlag()) {
			context.getCounter("mtdna", "FILTERED").increment(1);
			context.getCounter("mtdna", "DUPLICATE").increment(1);
			return;
		}

		if (samRecord.getReadLength() <= 25) {
			context.getCounter("mtdna", "FILTERED").increment(1);
			context.getCounter("mtdna", "SHORT-READ").increment(1);
			return;
		}

		if (ReferenceUtil.getTagFromSamRecord(samRecord.getAttributes(), "AS") < alignQual) {
			context.getCounter("mtdna", "FILTERED").increment(1);
			context.getCounter("mtdna", "BAD-ALIGNMENT").increment(1);
			return;
		}

		context.getCounter("mtdna", "UNFILTERED").increment(1);

	}

}
