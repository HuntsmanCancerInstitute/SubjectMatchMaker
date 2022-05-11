package edu.utah.hci.bioinfo.smm;

import java.util.Arrays;
import java.util.Random;
import org.apache.commons.text.similarity.LevenshteinDistance;


public class MatcherEngine implements Runnable {

	private LevenshteinDistance ld = LevenshteinDistance.getDefaultInstance();
	private boolean failed = false;
	// These subjects are only only present in this thread
	private Subject[] subjectChunk = null;
	private Subject[] querySubjects = null;
	private double missingOneKeyPenalty = 0;
	private double missingAdditionalKeyPenalty = 0;
	private int numMatchesToReturn = 0;
	
	
	
	public MatcherEngine(Subject[] subjectChunk, SubjectMatchMaker pm) {
		this.subjectChunk = subjectChunk;
		querySubjects = pm.getQuerySubjects();
		missingOneKeyPenalty = pm.getMissingOneKeyPenalty();
		missingAdditionalKeyPenalty = pm.getMissingAdditionalKeyPenalty();
		numMatchesToReturn = pm.getNumberTopMatchesToReturn();
		
	}

	
	public void run() {	
		try {
			//create a random set of index positions
			int[] indexes = new int[querySubjects.length];
			for (int i=0; i< indexes.length; i++) indexes[i] = i;
			randomize(indexes, new Random());
			
			//for each subject, find the top hits from the threads chunk of db subjects
			for (int i=0; i< indexes.length; i++) {
				Subject query = querySubjects[indexes[i]];
				findTopMatches(query);
			}
			
		} catch (Exception e) {
			failed = true;
			System.err.println("Error: problem matching subjects" );
			e.printStackTrace();
		}
	}
	
	/**For multiple iterations.*/
	public static void randomize (int[] array, Random rng){     
	    // n is the number of items left to shuffle
	    for (int n = array.length; n > 1; n--) {
	        // Pick a random element to move to the end
	        int k = rng.nextInt(n);  // 0 <= k <= n - 1.
	        // Simple swap of variables
	        int tmp = array[k];
	        array[k] = array[n - 1];
	        array[n - 1] = tmp;
	    }
	}


	/*Find top matches*/
	private void findTopMatches(Subject query) {
		
		//set the match score for every query:registry comparison
		String[] queryKeys = query.getComparisonKeys();
		for (Subject c: subjectChunk) {
			double score = scoreKeysLD(queryKeys, c.getComparisonKeys());
			c.setScore(score);
		}
		//sort smallest to largest
		Arrays.sort(subjectChunk);
		
		//add top hits to the query subject in a thread safe manner
		Subject[] topHits = new Subject[numMatchesToReturn];
		for (int i=0; i<topHits.length; i++)topHits[i] = subjectChunk[i];
		query.addTopCandidates(topHits);
		
	}


	/**Score keys using Levenshtein Distance
	 * If more than one key is missing, a value of 1 is added to the return score for each.  If just one, then it is ignored.
	 * Thus it's ok to be missing one key, but afterward the penalty is severe. */
	public double scoreKeysLD(String[] query, String[] db) {

//IO.pl("\nT: "+Misc.stringArrayToString(query, ",")+"\nD: "+Misc.stringArrayToString(db, ","));
			//for each key
			double sum = 0;
			int numMissing = 0;
			for (int i=0; i< query.length; i++) {
//IO.p("\t"+query[i]+" vs "+db[i]+" ");
				//missing?
				if (query[i].length() == 0 || db[i].length() == 0) {
					numMissing++;
//IO.pl("missing");
				}
				else {
					double edits = ld.apply(query[i], db[i]);
					double length = query[i].length();
					double ws = edits/length;
//IO.pl(edits+"/"+length+"="+ws);
					sum+= ws;
				}
			}
			//just one missing? then return sum of edits, otherwise add 1 for each
			if (numMissing !=0) {
				if (numMissing == 1) sum+= missingOneKeyPenalty;
				else {
					sum = sum + missingOneKeyPenalty + ((numMissing-1)* missingAdditionalKeyPenalty);
				}
//IO.pl("\tadding missing penalties");
			}
//IO.pl("\t\tScore: "+sum);
			return sum;
	}


	public boolean isFailed() {
		return failed;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	/*
	LevenshteinDistance ld = LevenshteinDistance.getDefaultInstance();
	//						FNLN	Gen		DoB				MRN
	String[] subject = {"DavidNix", "M", "08/11/1968", "12345678"};
	String[][] entries = {
			{"PaolaNix", "F", "06/09/1972", "88534667"},
			{"LarryNix", "M", "08/11/1968", "12345678"},
			{"DavidNix", "M", "09/11/1968", "12345678"},
			{"", "M", "09/11/1968", "12345678"}
	};
	
	for (String[] dbEntry: entries) {
		IO.pl(Misc.stringArrayToString(dbEntry, " "));
		double numEdits = 0;
		double length = 0;
		double indiDist = 0;
		for (int i=0; i< subject.length; i++) {
			//missing?
			if (subject[i].length() == 0 || dbEntry[i].length() == 0) continue;
			double e = ld.apply(subject[i], dbEntry[i]);
			numEdits += e;
			double l = subject[i].length();
			length += l;
			indiDist+= e/l;
			
		}
		double distanceMetric = numEdits/length;
		IO.pl("\t"+Num.formatNumber(distanceMetric, 3)+"\t"+Num.formatNumber(indiDist, 3)+"\t("+numEdits+"/"+length+")");
	}
	*/
	
}
