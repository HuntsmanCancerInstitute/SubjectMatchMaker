package edu.utah.hci.bioinfo.smm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**Consider 
 encrypting:
 echo "nvjkfdYTRTT3245bcgdERTR234765zcxvidn" > passPhrase.txt
 gpg --batch -c --passphrase-file passPhrase.txt registry.txt
 decrypting:
 gpg --batch -d --passphrase-file passPhrase.txt -o decrypted.txt registry.txt.gpg
 * */
public class SubjectMatchMaker {

	//user defined fields
	private File subjectRegistryFile = null;
	private File querySubjectFile = null;
	private File matchResultsDirectory = null;
	private boolean addQuerySubjectsToRegistry = false;
	private boolean verbose = true;
	private boolean caseInsensitive = false;

	//internal
	private Subject[] registrySubjects = null;
	private Subject[] querySubjects = null;
	private String[] coreIds = null;
	private int numberThreads = 0;
	private int minSubjectsPerChunk = 100;
	private MatcherEngine[] matchers = null;
	public int numberTopMatchesToReturn = 3;
	public double missingOneKeyPenalty = 0.12;
	public double missingAdditionalKeyPenalty = 1;
	private double maxEditScoreForMatch = 0.12;
	private HashMap<String,Subject> coreIdSubject = null;
	private CoreId coreIdMaker = new CoreId();
	private File lockedRegistry = null;
	private ArrayList<Subject[]> registryQueryUpdates = new ArrayList<Subject[]>();

	//results files
	private File updatedRegistry = null;
	private File jsonReport = null;
	private File spreadsheetReport = null;


	public SubjectMatchMaker (String[] args) {
		try {
			long startTime = System.currentTimeMillis();

			processArgs(args);

			//load registry subjects
			Util.p("\nLoading registry... ");
			registrySubjects = loadSubjectData(subjectRegistryFile, true, false);
			Util.pl(registrySubjects.length);

			//any new coreIds created? if so then exit
			boolean created = false;
			for (Subject s: registrySubjects) {
				if (s.isCoreIdCreated()) {
					created = true;
					break;
				}
			}
			//save an update and exit?
			if (created)  {
				Util.pl("\nSaving updated registry with new CoreIDs...");
				saveUpdatedRegistry(null);
				Util.pl("\nNo search performed! Rerun with the updated registry.");
				Util.deleteDirectory(matchResultsDirectory);
			}
			
			else {
				loadIdSubjectHash();

				//load test subjects, will throw error if malformed
				Util.p("\nLoading test subjects to match against the registry... ");
				querySubjects = loadSubjectData(querySubjectFile, false, true);
				if (querySubjects == null && coreIds != null) lookUpSubjectInfo();
				else {
					Util.pl(querySubjects.length);

					//make a matcher for each chunk
					int numPerCore = fetchMinPerCore();

					Subject[][] split = chunk(registrySubjects, numPerCore);
					matchers = new MatcherEngine[split.length];
					Util.pl("\nLaunching "+split.length+" lookup threads...");
					for (int i=0; i< matchers.length; i++)  matchers[i] = new MatcherEngine(split[i], this);

					//run the comparison
					ExecutorService executor = Executors.newFixedThreadPool(matchers.length);
					for (MatcherEngine l: matchers) executor.execute(l);
					executor.shutdown();
					while (!executor.isTerminated()) {}

					//check the matchers 
					for (MatcherEngine m: matchers) {
						if (m.isFailed()) throw new IOException("ERROR: Matcher engine issue! \n");
					}

					//check for matches and assign or make coreIds
					checkForMatches();
					
					//compare queries to each other
					compareQueries(matchers[0]);

					//print the full json report with all of the details
					printJson();

					//print a spreadsheet report just top matches or new coreIds
					printResults();

					//update the registry?
					updateRegistryWithNoMatches();
					
					//any registry entries to be updated
					if (registryQueryUpdates.size()!=0) {
						if (verbose) {
							Util.pl("\nConsider updating the following incomplete registry entries with keys from the queries:");
							for (Subject[] regQue: registryQueryUpdates) {
								Util.pl("Registry to update:\n"+regQue[0].fetchJson(false).toString(3));
								Util.pl("Query with new keys:\n"+regQue[1].fetchJson(false).toString(3)+"\n");
								
							}
						}
						
					}
				}
			}

			//clear the lock
			lockedRegistry.delete();

			//finish and calc run time
			double diffTime = ((double)(System.currentTimeMillis() -startTime))/1000;
			if (verbose) Util.pl("\nDone! "+Math.round(diffTime)+" Sec\n");
		} catch (Exception e) {
			Util.el("\nERROR running the SubjectIdMatchMaker, aborting. ");
			e.printStackTrace();
			deleteResults();
			System.exit(1);
		}
	}

	/* Looks for queries that didn't match and have a new coreId, that do match each other, and assigns them all of the same new coreId.
	 * Don't want to create multiple new coreIds for the same person.*/
	private void compareQueries(MatcherEngine me) {
		ArrayList<Subject> passing = new ArrayList<Subject>();

		//for each query
		for (int i=0; i< querySubjects.length; i++) {
			Subject a = querySubjects[i];
			
			//did this query have a new coreId assigned to it? thus no match
			if (a.isCoreIdCreated() == false) continue;
			
			//look to see if there are other such queries
			String[] aKeys = a.getComparisonKeys();
			passing.clear();
			for (int j=0; j< querySubjects.length; j++) {
				Subject b = querySubjects[j];
				//don't compare to self or to those who didn't have a new coreId assigned
				if (b.isCoreIdCreated()==false || i==j) continue;
				
				//is the score passing? save it
				double score = me.scoreKeysLD(aKeys, b.getComparisonKeys());
				if (score <= maxEditScoreForMatch) passing.add(b);
			}
			
			//any passing? assign all the same new coreId using the first query's 
			if (passing.size()!=0) {
				String newCoreId = a.getCoreId();
				for (int x=0; x< passing.size(); x++) passing.get(x).setCoreId(newCoreId);
			}			
		}
	}

	private void lookUpSubjectInfo() throws IOException {
		Util.pl("\n\nLooking up and writing subject info for the provided coreIds... ");
		//check all are coreIds
		for (String s: coreIds) {
			if (CoreId.isCoreId(s) == false) throw new IOException("\nERROR: the following isn't a valid coreId -> "+s);
		}
		//write out report
		spreadsheetReport = new File (matchResultsDirectory, "coreIdReport_PHI.xls");
		PrintWriter out = new PrintWriter( new FileWriter(spreadsheetReport));
		out.println("QueryCoreId\tLastName\tFirstName\tDobMonth\tdobDay\tDobYear\tGender\tMrn\tCoreId\tOtherIds");
		for (String s: coreIds) {
			out.print(s);
			out.print("\t");
			Subject sl = coreIdSubject.get(s);
			if (sl!=null) out.println(sl.toString());
			else out.println();
		}
		out.close();
		
	}

	private void updateRegistryWithNoMatches() throws Exception {
		//do they want to update the registry
		if (addQuerySubjectsToRegistry) {
			//is there anything to update
			ArrayList<Subject> toAdd = new ArrayList<Subject>();
			HashSet<String> newCoreIds = new HashSet<String>();
			for (Subject s: querySubjects) {
				if (s.isCoreIdCreated()) {
					//must watch out for duplicate new coreIds, only add the first Subject query
					String cid = s.getCoreId();
					if (newCoreIds.contains(cid) == false) {
						newCoreIds.add(cid);
						toAdd.add(s);
					}
				}
			}
			if (toAdd.size()!=0) {
				Util.pl("\nSaving updated registry with "+toAdd.size()+" unmatched queries...");
				saveUpdatedRegistry(toAdd);
			}
		}
	}

	private void checkForMatches() throws IOException {
		//are they just looking to see what matches and not add non matches to the registry? if so null the coreIdMaker
		if (addQuerySubjectsToRegistry == false) coreIdMaker = null; 
		//for each query
		for (Subject tp: querySubjects) {
			tp.setMatches(coreIdMaker, maxEditScoreForMatch);
			//update registry?
			if (tp.isUpdateTopMatchKeys()) {
				registryQueryUpdates.add(new Subject[] {tp.getTopMatches()[0], tp});
			}
		}
	}

	private void saveUpdatedRegistry(ArrayList<Subject> additional) throws Exception {
		String time = new Long(System.currentTimeMillis()).toString();
		File registryDir = subjectRegistryFile.getParentFile();
		
		//write out updated registry
		updatedRegistry = new File (registryDir, "updatedRegistry"+time+"_PHI.txt");
		PrintWriter out = new PrintWriter ( new FileWriter(updatedRegistry));
		out.println("#LastName\tFirstName\tDoBMonth(1-12)\tDoBDay(1-31)\tDoBYear(1900-2050)\tGender(M|F)\tMRN\tCoreId\totherId(s);delimited)");
		for (Subject u: registrySubjects) out.println(u.toString());
		if (additional != null) for (Subject u: additional) out.println(u.toString());
		out.close();
		
		//rename original
		File oldRegistry = new File (registryDir, "oldRegistry_"+time+"_PHI.txt");
		boolean renamed = subjectRegistryFile.renameTo(oldRegistry);
		if (renamed == false) throw new IOException("ERROR: failed to rename original registry file "+subjectRegistryFile+" to "+oldRegistry);
		Util.pl("\tRenamed the original registry file "+subjectRegistryFile.getName()+" to "+oldRegistry.getName());
		
		//wait a  sec to allow system to catch up, needed for redwood disk shelf
		Thread.sleep(500);
		
		//move the saved registry to current
		File newRegistry = new File (registryDir, "currentRegistry_"+time+"_PHI.txt");
		renamed = updatedRegistry.renameTo(newRegistry);
		if (renamed == false) throw new IOException("ERROR: failed to rename updated registry file "+updatedRegistry+" to "+newRegistry);
		Util.pl("\tUpdated registry successfully saved to "+newRegistry.getName()+". Use this for new searches.");
		updatedRegistry = newRegistry;
		
	}

	private void deleteResults() {
		if (updatedRegistry!= null) updatedRegistry.delete();
		if (jsonReport!= null) jsonReport.delete();
		if (spreadsheetReport!= null) spreadsheetReport.delete();
		if (lockedRegistry!= null) lockedRegistry.delete();
	}

	private void loadIdSubjectHash() throws IOException {
		coreIdSubject = new HashMap<String,Subject>();
		for (int i=0; i< registrySubjects.length; i++) {
			String coreId = registrySubjects[i].getCoreId();
			if (coreIdSubject.containsKey(coreId)) throw new IOException("\nERROR: the coreId "+coreId+" associated with registry subject["+i+"] is a duplicate of a prior registry subject. Duplicate coreIds are not permitted." );
			coreIdSubject.put(coreId, registrySubjects[i]);
		}
	}

	private void printJson() throws IOException {
		JSONObject results = new JSONObject();

		JSONObject params = new JSONObject();
		params.put("maximumEditScoreForMatch", maxEditScoreForMatch);
		params.put("missingOneKeyPenalty", missingOneKeyPenalty);
		params.put("missingAdditionalKeyPenalty", missingAdditionalKeyPenalty);
		params.put("numberTopMatchesToReturn", numberTopMatchesToReturn);
		params.put("addQuerySubjectsToRegistry", addQuerySubjectsToRegistry);
		params.put("registry", subjectRegistryFile.getCanonicalPath());
		params.put("queries", querySubjectFile.getCanonicalPath());
		params.put("output", matchResultsDirectory.getCanonicalPath());
		params.put("isNameCaseInsensitive", caseInsensitive);
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
		params.put("date", dtf.format(now));
		results.put("searchSettings", params);

		JSONArray searchArray = new JSONArray();
		
		//for each query
		for (Subject tp: querySubjects) {
			JSONObject search = new JSONObject();
			
			//get the base features
			JSONObject query = tp.fetchJson(false);
			search.put("query", query);
			
			//reset the scores in the top matches
			double[] topScores = tp.getTopMatchScores();
			Subject[] topMatches = tp.getTopMatches();
			for (int i=0; i< topMatches.length; i++) topMatches[i].setScore(topScores[i]);
			
			//query results
			JSONObject result = new JSONObject();
			result.put("topMatchFound", tp.isTopMatchFound());
			if (tp.isTopMatchFound()) {
				result.put("topMatchCoreId", topMatches[0].getCoreId());
				if (tp.isUpdateTopMatchKeys()) result.put("updateMissingTopMatchKeys", true);
			}
			result.put("newCoreIdCreated", tp.isCoreIdCreated());
			if (tp.isCoreIdCreated()) result.put("newCoreId", tp.getCoreId());
			//match warning?
			if (tp.getMatchWarning()!=null) result.put("topMatchWarning", tp.getMatchWarning());
			
			JSONArray matches = new JSONArray();
			for (int i=0; i< topMatches.length; i++) {
				JSONObject jo = topMatches[i].fetchJson(true);
				matches.put(jo);
			}
			result.put("matches", matches);
			
			//add query to the array
			search.put("result", result);
			
			searchArray.put(search);
		}
		
		results.put("searches", searchArray);
		
		
		//save it
		jsonReport = new File(matchResultsDirectory, "matchReport_PHI.json");
		PrintWriter out = new PrintWriter( new FileWriter(jsonReport));
		out.println(results.toString(4));
		out.close();
	}


	private void printResults() throws IOException {

		//open file writer
		spreadsheetReport = new File (matchResultsDirectory, "matchReport_PHI.xls");
		PrintWriter out = new PrintWriter( new FileWriter(spreadsheetReport));
		
		//print header
		out.print("#OriginalSubject\tMatchFound\tCoreID\tScore\tRegistrySubject\tOtherIDs");
		for (int i=0; i< numberTopMatchesToReturn; i++) out.print("\tNextBestMatch\tCoreID\tScore\tRegistrySubject\tOtherIDs");
		out.println();
		
		//for each subject
		for (Subject tp: querySubjects) {
			
			//reset the scores in the top matches, these can change 
			double[] topScores = tp.getTopMatchScores();
			Subject[] topMatches = tp.getTopMatches();
			for (int i=0; i< topMatches.length; i++) topMatches[i].setScore(topScores[i]);
			
			StringBuilder sb = new StringBuilder(Util.stringArrayToString(tp.getComparisonKeys(),"|"));
			
			//top match found?
			if (tp.isTopMatchFound()) sb.append("\tTRUE\t");
			else {
				sb.append("\tFALSE");
				if (tp.getCoreId()!=null) {
					sb.append("\t");
					sb.append(tp.getCoreId());
					sb.append("\t.\t.\t.\t.\t");
				}
				else sb.append("\t.\t.\t.\t.\t.\t");
			}
			topMatches[0].addTabInfo(sb);
			
			for (int i=1; i<topMatches.length; i++) {
				sb.append("\t.\t");
				topMatches[i].addTabInfo(sb);
			}
			out.println(sb.toString());
		}
		
		//close writer
		out.close();
	}

	private int fetchMinPerCore() {
		double numAllSubjects = registrySubjects.length;
		for (int i=numberThreads; i >=1; i--) {
			int numPerChunk = (int)Math.round(numAllSubjects/(double)i);
			if (numPerChunk >= minSubjectsPerChunk) return numPerChunk;
		}
		return minSubjectsPerChunk;
	}

	private Subject[] loadSubjectData(File dataFile, boolean addCoreId, boolean isQuery) throws IOException {
		String line = null;
		BufferedReader in = Util.fetchBufferedReader(dataFile);
		ArrayList<Subject> pAL = new ArrayList<Subject>();
		ArrayList<String> cAL = new ArrayList<String>();
		int index = 0;
		while ((line = in.readLine())!= null) {
			if (line.length()==0 || line.startsWith("#"))continue;
			String[] fields = Util.TAB.split(line);
			if (fields.length == 1) cAL.add(fields[0]);
			else pAL.add(new Subject(index, fields, addCoreId, coreIdMaker, isQuery, caseInsensitive));
			index++;
		}
		in.close();
		
		if (cAL.size()!=0) {
			coreIds = new String[cAL.size()];
			cAL.toArray(coreIds);
			return null;
		}
		
		Subject[] p = new Subject[pAL.size()];
		pAL.toArray(p);
		return p;
	}

	public static void main(String[] args) {
		if (args.length ==0){
			printDocs();
			System.exit(0);
		}
		new SubjectMatchMaker(args);
	}		

	/**This method will process each argument and assign new variables*/
	public void processArgs(String[] args){
		try {
			if (verbose) Util.pl("\nArguments: "+ Util.stringArrayToString(args, " ") +"\n");
			Pattern pat = Pattern.compile("-[a-zA-Z]");
			File subjectRegistryDir = null;
			for (int i = 0; i<args.length; i++){
				Matcher mat = pat.matcher(args[i]);
				if (mat.matches()){
					char test = args[i].charAt(1);
					try{
						switch (test){
						case 'r': subjectRegistryDir = new File(args[++i]).getCanonicalFile(); break;
						case 'q': querySubjectFile = new File(args[++i]); break;
						case 'o': matchResultsDirectory = new File(args[++i]); break;
						case 't': numberThreads = Integer.parseInt(args[++i]); break;
						case 'm': numberTopMatchesToReturn = Integer.parseInt(args[++i]); break;
						case 'p': missingOneKeyPenalty = Double.parseDouble(args[++i]); break;
						case 'a': addQuerySubjectsToRegistry = true; break;
						case 'u': updatedRegistry = new File(args[++i]); break;
						case 's': maxEditScoreForMatch = Double.parseDouble(args[++i]); break;
						case 'v': verbose = false; break;
						case 'c': caseInsensitive = true; break;
						default: Util.printErrAndExit("\nProblem, unknown option! " + mat.group());
						}
					}
					catch (Exception e){
						e.printStackTrace();
						Util.printErrAndExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
					}
				}
			}
			
			//check registry file
			checkRegistryDirectory(subjectRegistryDir);
			
			//check output dir
			if (matchResultsDirectory == null) Util.printErrAndExit("ERROR: failed to find the output directory "+matchResultsDirectory);
			else {
				if (matchResultsDirectory.exists() && matchResultsDirectory.isDirectory()==false) {
					Util.printErrAndExit("ERROR: the output directory exists but isn't a directory? See "+matchResultsDirectory);
				}
				else {
					if (matchResultsDirectory.exists()==false && matchResultsDirectory.mkdirs()==false) {
						Util.printErrAndExit("ERROR: failed to make the output directory? See "+matchResultsDirectory);
					}
				}
			}

			//check query subject file
			if (querySubjectFile == null || querySubjectFile.canRead() == false) Util.printErrAndExit("ERROR: failed to find the query subject file "+querySubjectFile);

			//threads
			int numProc = Runtime.getRuntime().availableProcessors() - 1;
			if (numberThreads == 0 || numberThreads > numProc) numberThreads = numProc;		

			//print params
			if (verbose) printOptions();

		} catch (Exception e) {
			e.printStackTrace();
			Util.printErrAndExit("\nProblem parsing arguments!");
		}
	}

	private void checkRegistryDirectory(File subjectRegistryDir) throws Exception {
		Util.pl("Checking the Subject Registry Directory...");
		//does the dir exist
		if (subjectRegistryDir == null || subjectRegistryDir.isDirectory()== false) Util.printErrAndExit("ERROR: failed to find the subject registry directory -r ? See "+subjectRegistryDir);
		
		//is there a lock on the dir
		int wait = 0;
		while (true) {
			if (wait == 6) {
				Util.printErrAndExit("\nERROR: a persistant LOCKED file found in "+subjectRegistryDir+". Be sure no other SubjectMatchMaker is running, delete it, and restart.");
			}
			File lr = new File (subjectRegistryDir, "LOCKED");
			if (lr.exists()) {
				Util.pl("\t"+ lr +" file found, registry in use, waiting...");
				wait++;
				//wait 10 seconds
				Thread.sleep(10000);
			}
			else {
				lockedRegistry = lr;
				lockedRegistry.createNewFile();
				break;
			}
		}
		
		//is there just one currentRegistry_ file found?
		File[] currReg = Util.extractFilesStartingWith(subjectRegistryDir, "currentRegistry_");
		
		if (currReg.length == 1) subjectRegistryFile = currReg[0];
		else {
			lockedRegistry.delete();
			if (currReg.length == 0) Util.printErrAndExit("\nERROR: no file staring with 'currentRegistry_' was found in "+subjectRegistryDir);
			else Util.printErrAndExit("\nERROR: more than one file staring with 'currentRegistry_' was found in "+subjectRegistryDir);
		}
	}

	private void printOptions() throws IOException {
		String opt = "\nOptions:\n"+
				"-r Registry file "+ subjectRegistryFile +"\n"+
				"-q Query file "+ querySubjectFile +"\n"+
				"-o Output results dir "+ matchResultsDirectory.getCanonicalFile()+"\n"+
				"-a Add query subjects to registry? "+ addQuerySubjectsToRegistry+"\n"+
				"-s Max edit score for match "+ maxEditScoreForMatch+"\n"+
				"-p First missing key score penalty "+ missingOneKeyPenalty+ "\n"+
				"-k Subsequent missing key score penalty "+ missingAdditionalKeyPenalty+ "\n"+
				"-t Number threads "+ numberThreads+ "\n"+
				"-m Number of matches to return "+ numberTopMatchesToReturn+"\n"+
				"-c Is case-insensitive "+caseInsensitive;

		Util.pl(opt);
	}




	/**Splits an object[] into chunks containing the minNumEach. Any remainder is evenly distributed over the prior.
	 * Note this is by reference, the array is not copied. */
	public static Subject[][] chunk (Subject[] s, int minNumEach){
		//watch out for cases where the min can't be met
		int numChunks = s.length/minNumEach;
		if (numChunks == 0) return new Subject[][]{s};

		double numLeftOver = (double)s.length % (double)minNumEach;

		int[] numInEach = new int[numChunks];
		for (int i=0; i< numChunks; i++) numInEach[i] = minNumEach;

		while (numLeftOver > 0){
			for (int i=0; i< numChunks; i++) {
				numInEach[i]++;
				numLeftOver--;
				if (numLeftOver == 0) break;
			}
		}
		//build chunk array
		Subject[][] chunks = new Subject[numChunks][];
		int index = 0;
		//for each chunk
		for (int i=0; i< numChunks; i++){
			//create container and fill it
			Subject[] sub = new Subject[numInEach[i]];
			for (int j=0; j< sub.length; j++) sub[j] = s[index++];
			chunks[i] = sub;
		}
		return chunks;
	}


	public static void printDocs(){
		Util.pl("\n" +
				"**************************************************************************************\n" +
				"**                           Subject Match Maker : Sept 2022                        **\n" +
				"**************************************************************************************\n" +
				"SMM attempts to match subject's PHI keys (FirstLastName, DoB, Gender, MRN) against a\n"+
				"registry of the same and fetch their unique subject coreIds.  SMM uses a sum of\n"+
				"the key's LevenshteinEditDistance/Length as the distance metric with penalties for\n"+
				"missing keys. Both a json and spreadsheet report are generated. If indicated,\n"+
				"queries not matched will be added to the registry with a new coreId. Use this tool to\n"+
				"assign unique ids to new subjects and find them using missing, partial, or typo\n"+
				"altered PHI keys. JUnit tested.\n"+

				"\nRequired:\n"+
				"-r Directory containing one file with the prefix 'currentRegistry_' that contains a\n"+
				"      registry of subjects, tab delimited file(.gz/.zip OK), one subject per line: \n"+
				"      lastName firstName dobMonth(1-12) dobDay(1-31) dobYear(1900-2050) gender(M|F)\n"+
				"      mrn coreId otherIds. The last two columns are optional. Semicolon delimit\n"+
				"      otherIds. Use '.' for missing info. CoreIds will be created as needed.\n"+
				"      Example: Biden Joseph 11 20 1942 M 19485763 . 7474732,847362\n"+
				"-q File containing queries to match to the registry, ditto. Alternatively, provide\n"+
				"      a single column of coreIds to use in fetching subject info from the registry.\n"+
				"-o Directory to write out the match result reports.\n"+

				"\nOptional:\n"+
				"-a Add query subjects that failed to match to the registry and assign them a coreId.\n"+
				"-s Max edit score for match, defaults to 0.12, smaller scores are more stringent.\n"+
				"-p Score penalty for a single missing key, defaults to 0.12\n"+
				"-k Score penatly for additional missing keys, defaults to 1\n"+
				"-t Number of threads to use, defaults to all.\n"+
				"-m Number of top matches to return per query, defaults to 3\n"+
				"-c Case-insensitive name matching, defaults to case sensitive.\n"+

				"\nExample: java -jar pathTo/SubjectIdMatchMaker_xxx.jar -r ~/PHI/SMMRegistry \n"+
				"      -q ~/Tempus/newPatients_PHI.txt -o ~/Tempus/SMMRes/ -a -c \n"+
				"\n**************************************************************************************\n");
	}

	public Subject[] getQuerySubjects() {
		return querySubjects;
	}
	public int getNumberTopMatchesToReturn() {
		return numberTopMatchesToReturn;
	}
	public double getMissingOneKeyPenalty() {
		return missingOneKeyPenalty;
	}
	public double getMissingAdditionalKeyPenalty() {
		return missingAdditionalKeyPenalty;
	}

	public File getUpdatedRegistry() {
		return updatedRegistry;
	}


}
