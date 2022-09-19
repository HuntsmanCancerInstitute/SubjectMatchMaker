package edu.utah.hci.bioinfo.smm;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class Subject implements Comparable<Subject> {

	private String lastName = "";
	private String firstName = "";
	private int dobMonth = -1;
	private int dobDay = -1;
	private int dobYear = -1;
	private String gender = "";
	private String mrn = "";
	private String[] otherSubjectIds = null;
	private String coreId = null;
	private boolean coreIdCreated = false;
	private boolean isQuery = false;

	private double score = 0; //this is a temp value and changes
	private String[] comparisonKeys = null;
	private boolean topMatchFound = false;
	private Subject[] topMatches = null;
	private double[] topMatchScores = null;
	private String matchWarning = null;
	public static final Pattern LEADING_ZEROs = Pattern.compile("^0+");
	
	
	//constructor
	public Subject(int dataLineIndex, String[] t, boolean addCoreId, CoreId coreIdMaker, boolean isQuery, boolean isCaseInsensitive) throws IOException {
		this.isQuery = isQuery;
		
		//required: lastName firstName dobMonth dobDay dobYear gender mrn 
		//             0        1          2       3      4      5     6
		//optional: coreId otherIds
		//              7        8
		if (t.length< 7) throw new IOException("ERROR: too few fields in subject dataline index : "+dataLineIndex);
		//remove leading or trailing whitespace and placeholder '.'
		for (int i=0; i<t.length; i++) {
			t[i] = t[i].trim();
			if (t[i].equals(".")) t[i] = "";
		}

		lastName = t[0];
		firstName = t[1];

		if (t[2].length()!=0) {
			dobMonth = Integer.parseInt(t[2]);
			if (dobMonth< 1 || dobMonth > 12) throw new IOException("ERROR: dob month field '"+t[2]+"' is malformed, must be 1-12, in subject dataline index : "+dataLineIndex);
		}

		if (t[3].length()!=0) {
			dobDay = Integer.parseInt(t[3]);
			if (dobDay< 1 || dobDay > 31) throw new IOException("ERROR: dob day field '"+t[3]+"' is malformed, must be 1-31, in subject dataline index : "+dataLineIndex);
		}

		if (t[4].length()!=0) {
			dobYear = Integer.parseInt(t[4]);
			if (dobYear< 1900 || dobYear > 2050) throw new IOException("ERROR: dob year field '"+t[4]+"' is malformed, must be 1900-2050, in subject dataline index : "+dataLineIndex);
		}

		if (t[5].length()!=0) {
			gender = t[5];
			if (gender.equals("M") == false && gender.equals("F") == false) throw new IOException("ERROR: the gender field '"+t[5]+"' is malformed, must be M or F, in subject dataline index : "+dataLineIndex);
		}

		//strip off any leading zeros, e.g. 0012345
		if (t[6].length()!=0) mrn = LEADING_ZEROs.matcher(t[6]).replaceAll("");
		

		if (t.length > 7 && t[7].length()!=0) {
			coreId = t[7];
			//test it
			if (CoreId.isCoreId(coreId)==false)  throw new IOException("ERROR: the coreId '"+t[7]+"' is not a matching coreId, in subject dataline index : "+dataLineIndex);;
		}
		else if (addCoreId) {
			coreId = coreIdMaker.createCoreId();
			coreIdCreated = true;
		}

		if (t.length > 8 && t[8].length()!=0) {
			otherSubjectIds = Util.SEMICOLON.split(t[8]);
		}

		makeComparisonKeys(isCaseInsensitive);

	}
	
	
	public JSONObject fetchJson(boolean includeScore) throws IOException {
		JSONObject query = new JSONObject();
		if (lastName.length()!=0) query.put("lastName", lastName);
		if (firstName.length()!=0) query.put("firstName", firstName);
		if (dobMonth !=-1) query.put("dobMonth", dobMonth);
		if (dobDay !=-1) query.put("dobDay", dobDay);
		if (dobYear !=-1) query.put("dobYear", dobYear);
		if (gender.length()!=0) query.put("gender", gender);
		if (mrn.length()!=0) query.put("mrn", mrn);
		if (coreId != null) query.put("coreId", coreId);
		if (otherSubjectIds != null) {
			JSONArray h = new JSONArray();
			for (String hid: otherSubjectIds) h.put(hid);
			query.put("otherIds", h);
		}
		if (includeScore) query.put("matchScore", score);
		return query;
	}
	
	public void setMatches(CoreId coreIdMaker, double maxEditScoreForMatch) throws IOException {

		//add in scores and sort smallest (best) to largest (worse) and check if top match
		int numTopMatches = 0;
		for (int i=0; i< topMatches.length; i++) {
			topMatches[i].setScore(topMatchScores[i]);
			if (topMatchScores[i]<= maxEditScoreForMatch) {
				numTopMatches++;
			}
		}
		//sort the top match registry subjects
		Arrays.sort(topMatches);

		//sort the scores and use these instead of what is stored in the subject since this can change
		Arrays.sort(topMatchScores);

		//is there a qualifying top match
		if (numTopMatches == 1) topMatchFound = true;

		//more than one where the first score is < or = to the second
		else if (numTopMatches > 1) {
			if (topMatches[0].score < topMatches[1].score) topMatchFound = true;
			else if (topMatches[0].score == topMatches[1].score) {
				topMatchFound = true;
				matchWarning = "Top matches have the same score ("+topMatches[0].score+"), selecting the first.";
			}
			else topMatchFound = false;
		}

		//top match not found, create new coreId?
		else {
			topMatchFound = false;
			if (coreIdMaker != null) {
				coreId = coreIdMaker.createCoreId();
				coreIdCreated = true;
			}
		}
	}



	/**Leave missing data as "", these will be skipped.*/
	private void makeComparisonKeys(boolean caseInsensitive) {
		String dob = "";
		if (dobMonth!=-1 && dobDay!=-1 && dobYear!=-1) dob = dobMonth+"/"+dobDay+"/"+dobYear;
		comparisonKeys = new String[] {
				lastName+ firstName,
				dob,
				gender,
				mrn
		};
		if (caseInsensitive) comparisonKeys[0] = comparisonKeys[0].toUpperCase();
	}

	public synchronized void addTopCandidates(Subject[] topHits) {
		// yet instantiated?
		if (topMatches == null) {
			topMatches = topHits;
			topMatchScores = new double[topHits.length];
			for (int i=0; i< topHits.length; i++) topMatchScores[i] = topHits[i].getScore();
		}
		else {
			//for each topHit from the chunk, compare to what this subject has already seen
			for (int i=0; i< topHits.length; i++) {
				if (topHits[i].getScore() < topMatchScores[i]) {
					topMatches[i] = topHits[i];
					topMatchScores[i] = topHits[i].getScore();
				}
			}
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(lastName); sb.append("\t");
		sb.append(firstName); sb.append("\t");
		if (dobMonth != -1) sb.append(dobMonth);
		sb.append("\t");
		if (dobDay != -1) sb.append(dobDay);
		sb.append("\t");
		if (dobYear != -1) sb.append(dobYear);
		sb.append("\t");
		sb.append(gender); sb.append("\t");
		sb.append(mrn); sb.append("\t");
		if (coreId !=null) sb.append(coreId);
		sb.append("\t");
		if (otherSubjectIds !=null) {
			sb.append(otherSubjectIds[0]);
			for (int i=1; i< otherSubjectIds.length; i++) {
				sb.append(";");
				sb.append(otherSubjectIds[i]);
			}
		}
		return sb.toString();
	}
	
	public String toStringPretty() {
		StringBuilder sb = new StringBuilder(comparisonKeys[0]);
		for (int i=1; i< comparisonKeys.length; i++) {
			sb.append("\t");
			sb.append(comparisonKeys[i]);
		}
		if (coreId == null && otherSubjectIds==null) return sb.toString();
		
		sb.append("\t");
		if (coreId !=null) sb.append(coreId);
		sb.append("\t");
		if (otherSubjectIds !=null) {
			sb.append(otherSubjectIds[0]);
			for (int i=1; i< otherSubjectIds.length; i++) {
				sb.append(";");
				sb.append(otherSubjectIds[i]);
			}
		}
		return sb.toString();
	}
	
	public void addTabInfo(StringBuilder sb) {
		if (coreId!=null) sb.append(coreId);
		else sb.append(".");
		sb.append("\t");
		sb.append(Util.formatNumber(score, 3));
		sb.append("\t");
		addComparisonKeys(sb);
		sb.append("\t");
		if (otherSubjectIds!=null) sb.append(Util.stringArrayToString(otherSubjectIds,";"));
		else sb.append("."); 

	}
	
	/**Leave missing data as "", these will be skipped.*/
	private void addComparisonKeys(StringBuilder sb) {
		String dob = "";
		if (dobMonth!=-1 && dobDay!=-1 && dobYear!=-1) dob = dobMonth+"/"+dobDay+"/"+dobYear;
		sb.append(lastName); sb.append(firstName); sb.append("|");
		sb.append(dob); sb.append("|");
		sb.append(gender); sb.append("|");
		sb.append(mrn); 
	}

	/**Sorts by score, smallest to largest*/
	public int compareTo(Subject o) {
		if (o.score < this.score) return 1;
		if (o.score > this.score) return -1;
		return 0;
	}
	
	/**Returns the coreId associated with this subject, either from the topMatch if a topMatch was found, a newly generated coreId, or null.*/
	public String getCoreIdNewOrMatch() {
		//this is old or newly created
		if (coreId != null) return coreId;
		//from a successful match
		if (topMatchFound) return topMatches[0].getCoreId();
		return null;
	}

	public String[] getComparisonKeys() {
		return comparisonKeys;
	}
	public double getScore() {
		return score;
	}
	public void setScore(double score) {
		this.score = score;
	}
	public Subject[] getTopMatches() {
		return topMatches;
	}
	public double[] getTopMatchScores() {
		return topMatchScores;
	}

	public String getCoreId() {
		return coreId;
	}

	public String getLastName() {
		return lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public int getDobMonth() {
		return dobMonth;
	}

	public int getDobDay() {
		return dobDay;
	}

	public int getDobYear() {
		return dobYear;
	}

	public String getGender() {
		return gender;
	}

	public String getMrn() {
		return mrn;
	}

	public String[] getOtherSubjectIds() {
		return otherSubjectIds;
	}

	public boolean isCoreIdCreated() {
		return coreIdCreated;
	}


	public boolean isTopMatchFound() {
		return topMatchFound;
	}


	public String getMatchWarning() {
		return matchWarning;
	}


	public boolean isQuery() {
		return isQuery;
	}


	public void setCoreId(String coreId) {
		this.coreId = coreId;
	}








}
