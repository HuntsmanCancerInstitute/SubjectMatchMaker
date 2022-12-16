package edu.utah.hci.bioinfo.smm.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import edu.utah.hci.bioinfo.smm.SubjectMatchMaker;
import edu.utah.hci.bioinfo.smm.Util;
import org.json.JSONArray;
import org.json.JSONObject;

/**Run this as a junit test before exporting a runnable jar.*/
public class SubjectMatchMakerTest {

	//define the test resource dir on your local computer
	private static File testResourceDir = new File ("/Users/u0028003/Code/SubjectMatchMaker/TestingResources");
	private static File testQueries = new File (testResourceDir, "testQueries.txt");
	private static File testCoreIdQueries = new File (testResourceDir, "testCoreIdQueries.txt");
	private static File testRegistry = new File (testResourceDir, "startingRegistry_NoCoreIds.txt");
	
	@Test
	public void testAddCoreIdsToRegistry() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);

			//check that core ids were added to all subjects
			File[] curr = Util.extractFilesStartingWith(registryDirectory, "currentRegistry_");
			assertTrue(curr.length==1);

			BufferedReader in = Util.fetchBufferedReader(curr[0]);
			String line = null;
			String[] fields = null;
			while ((line = in.readLine())!=null) {
				line = line.trim();
				fields = Util.TAB.split(line);
				if (fields.length < 8) fail("Less than 8 fields for -> "+line+" Did coreIds get added to the updated registry?");
			}
			in.close();

			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}
	
	@Test
	public void runSearchNoUpdate() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and update the the registry
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);
			
			//launch the real search, this will grab the updated registry and generate two output files, no additional update
			smm = new SubjectMatchMaker(args);
			File[] reports = Util.extractFilesStartingWith(outputDirectory, "matchReport");
			assertTrue(reports.length==2);
			
			//check the json file
			File json = new File(outputDirectory, "matchReport_PHI.json");
			assertTrue(json.exists());
			checkNoUpdateJson(json, false, false);
			
			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}
	
	@Test
	public void runSearchWithUpdate() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and update the the registry
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
					"-a"
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);
			
			//launch the real search, this will grab the updated registry and generate two output files, no additional update
			smm = new SubjectMatchMaker(args);
			
			//check that the registry has only one new obama entry
			checkObama(smm);
			
			//check the json file
			File json = new File(outputDirectory, "matchReport_PHI.json");
			checkNoUpdateJson(json, true, false);
			
			//launch the search again on the updated registry containing the non matches
			File json2 = new File(outputDirectory, "matchReport_PHI.json");
			smm = new SubjectMatchMaker(args);
			checkWithUpdateJson(json2, true);
			
			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}
	
	@Test
	public void runSearchWithRegistryKeyUpdate() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and update the the registry
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
					"-a"
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);
			
			//launch the real search, this will updated registry with new info for Susan Collins
			File testQuery = new File(testResourceDir,"testQueryWithMoreInfo.txt");
			args = new String[]{
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQuery.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
					"-u"
			};
			smm = new SubjectMatchMaker(args);
			
			checkCollins(smm);
			
			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}
	
	private void checkCollins(SubjectMatchMaker smm) {
		boolean found = false;
		String[] lines = Util.loadFile(smm.getUpdatedRegistry());
		for (String l: lines) if (l.contains("Collins") && l.contains("avaId123;hciId456")) {
			found = true;
			break;
		}
		assertTrue(found);
		
	}
	
	private void checkObama(SubjectMatchMaker smm) {
		int numObamaFound = 0;
		String[] lines = Util.loadFile(smm.getUpdatedRegistry());
		for (String l: lines) if (l.contains("Obama")) numObamaFound++;
		assertTrue(numObamaFound==1);
		
	}

	@Test
	public void runCoreIdSearch() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and update the the registry
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testCoreIdQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);
			
			//launch the coreId lookup, this will grab the updated registry and generate one output files
			smm = new SubjectMatchMaker(args);
			
			//check the spreadsheet file
			File xls = new File(outputDirectory, "coreIdReport_PHI.xls");
			String[] resLines = Util.loadFile(xls);
			assertTrue(resLines.length==5);
			String bennet = "KJ3KV8XX	Bennet	Michael	11	28	1964	M	880402	KJ3KV8XX	8485766;6625133";
			assertTrue(resLines[2].equals(bennet));
			String notFound = "xx8xx8xx";
			assertTrue(resLines[4].equals(notFound));
			
			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}

	@Test
	public void runSearchNoUpdateCaseInsensitive() {
		try {
			setupLocalDirs();

			//launch with first query on new Registry, this will create new coreIDs and update the the registry
			File registryDirectory = new File(testResourceDir,"Registry");
			File outputDirectory = new File(testResourceDir,"Results");
			
			String[] args = {
					"-r", registryDirectory.getCanonicalPath(),
					"-q", testQueries.getCanonicalPath(),
					"-o", outputDirectory.getCanonicalPath(),
					"-c"
			};
			SubjectMatchMaker smm = new SubjectMatchMaker(args);
			
			//launch the real search, this will grab the updated registry and generate two output files, no additional update
			smm = new SubjectMatchMaker(args);
			File[] reports = Util.extractFilesStartingWith(outputDirectory, "matchReport");
			assertTrue(reports.length==2);
			
			//check the json file
			File json = new File(outputDirectory, "matchReport_PHI.json");
			assertTrue(json.exists());
			checkNoUpdateJson(json, false, true);
			
			cleanupLocalDirs();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception caught.");
		}
	}	
	
	private void checkWithUpdateJson(File json, boolean newCoreIdsCreated) throws IOException {
		String jString = Util.loadFile(json, " ", true);
	    JSONObject main = new JSONObject(jString);
	     
	     //any searchSettings?
	     assertTrue(main.has("searchSettings"));
	     
	     JSONArray searches = main.getJSONArray("searches");
	     assertTrue(searches.length()==6);
	     
	     //for each search
	     for (int i=0; i< searches.length(); i++) {
	    	 
	    	 JSONObject search = searches.getJSONObject(i);
	    	 JSONObject query = search.getJSONObject("query");
	    	 JSONObject result = search.getJSONObject("result");
	    	 String lastName = query.getString("lastName");
	    	 
	    	 if (lastName.equals("Barrasso")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    		 JSONArray matches = result.getJSONArray("matches");
	    		 JSONObject match = matches.getJSONObject(0);
	    		 assertTrue(match.getString("lastName").equals("Barrasso"));
	    		 assertTrue(match.getString("firstName").equals("John"));
	    		 assertTrue(match.getDouble("matchScore") == 0.09090909090909091);
	    		 assertTrue(match.getString("gender").equals("M"));
	    		 assertTrue(match.getString("mrn").equals("393308"));
	    		 assertTrue(match.getInt("dobDay") == 21);
	    		 assertTrue(match.getInt("dobMonth") == 7);
	    		 assertTrue(match.getInt("dobYear") == 1952);
	    		 assertTrue(match.has("coreId"));
	    		 String otherId = match.getJSONArray("otherIds").getString(0);
	    		 assertTrue(otherId.equals("8576646"));
	    		 
	    	 }
	    	 else if (lastName.equals("Benet")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    		 JSONArray matches = result.getJSONArray("matches");
	    		 JSONObject match = matches.getJSONObject(2);
	    		 assertTrue(match.getString("lastName").equals("Obama"));
	    	 }
	    	 else if (lastName.equals("Blackburn")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    		 JSONArray matches = result.getJSONArray("matches");
	    		 JSONObject match = matches.getJSONObject(0);
	    		 assertTrue(match.getString("lastName").equals("Blackburn"));
	    		 assertTrue(match.getDouble("matchScore") == 0);
	    		 
	    	 }
	    	 else if (lastName.equals("Baldwin")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    	 }
	    	 else if (lastName.equals("Obama")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    	 }
	    	 else fail("Failed to find the lastName in the search json for "+lastName);
	     }
	}
	
	private void checkNoUpdateJson(File json, boolean newCoreIdsCreated, boolean checkTopMatchWarning) throws IOException {
		String jString = Util.loadFile(json, " ", true);
	    JSONObject main = new JSONObject(jString);
	     
	     //any searchSettings?
	     assertTrue(main.has("searchSettings"));
	     
	     JSONArray searches = main.getJSONArray("searches");
	     assertTrue(searches.length()==6);
	     
	     //for each search
	     for (int i=0; i< searches.length(); i++) {
	    	 
	    	 JSONObject search = searches.getJSONObject(i);
	    	 JSONObject query = search.getJSONObject("query");
	    	 JSONObject result = search.getJSONObject("result");
	    	 String lastName = query.getString("lastName");
	    	 
	    	 if (lastName.equals("Barrasso")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    		 JSONArray matches = result.getJSONArray("matches");
	    		 JSONObject match = matches.getJSONObject(0);
	    		 assertTrue(match.getString("lastName").equals("Barrasso"));
	    		 assertTrue(match.getString("firstName").equals("John"));
	    		 assertTrue(match.getDouble("matchScore") == 0.09090909090909091);
	    		 assertTrue(match.getString("gender").equals("M"));
	    		 assertTrue(match.getString("mrn").equals("393308"));
	    		 assertTrue(match.getInt("dobDay") == 21);
	    		 assertTrue(match.getInt("dobMonth") == 7);
	    		 assertTrue(match.getInt("dobYear") == 1952);
	    		 assertTrue(match.has("coreId"));
	    		 String otherId = match.getJSONArray("otherIds").getString(0);
	    		 assertTrue(otherId.equals("8576646"));
	    		 
	    	 }
	    	 else if (lastName.equals("Benet")) {
	    		 assertTrue(result.has("topMatchCoreId"));
	    		 assertFalse(result.has("newCoreId"));
	    		 if (checkTopMatchWarning) assertTrue(result.has("topMatchWarning")); 
	    		 JSONArray matches = result.getJSONArray("matches");
	    		 JSONObject match = matches.getJSONObject(2);
	    		 assertTrue(match.getString("lastName").equals("Warner"));
	    		 
	    	 }
	    	 else if (lastName.equals("Blackburn")) {
	    		 assertFalse(result.has("topMatchCoreId"));
	    		 if (newCoreIdsCreated)  assertTrue(result.has("newCoreId"));
	    		 else  assertFalse(result.has("newCoreId"));
	    		 
	    	 }
	    	 else if (lastName.equals("Baldwin")) {
	    		 assertFalse(result.has("topMatchCoreId"));
	    		 if (newCoreIdsCreated)  assertTrue(result.has("newCoreId"));
	    		 else  assertFalse(result.has("newCoreId"));
	    	 }
	    	 else if (lastName.equals("Obama")) {
	    		 assertFalse(result.has("topMatchCoreId"));
	    		 if (newCoreIdsCreated)  assertTrue(result.has("newCoreId"));
	    		 else  assertFalse(result.has("newCoreId"));
	    	 }
	    	 else fail("Failed to find the lastName in the search json for "+lastName);
	     }
	}

	private static void cleanupLocalDirs() {
		File registry = new File(testResourceDir, "Registry");
		File results = new File(testResourceDir, "Results");
		Util.deleteDirectory(registry);
		Util.deleteDirectory(results);
	}

	/**This sets up the local test dir.*/
	private static void setupLocalDirs() throws Exception {
		String[] cmds = {
				"cd "+testResourceDir,
				"rm -rf Registry Results",
				"mkdir Registry",
				"cp startingRegistry_NoCoreIds.txt Registry/currentRegistry_NoCoreIds.txt",
		};
		String c = Util.stringArrayToString(cmds, "\n");
		Util.executeShellScript(c, testResourceDir);
	}
	
}
