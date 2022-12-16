package edu.utah.hci.bioinfo.smm;

import java.util.HashSet;
import java.util.Random;
import java.util.regex.Pattern;

/** Generates and tests coreIds
 * an 10 letter number string in the following pattern, LLLDLLDLLL, L=A-Za-z but no OoIiLl, D=2-9,  > 300M unique combinations 
 * this used to be an 8 letter number string but this was too few unique combinations, 
 * see https://www.calculator.net/permutation-and-combination-calculator.html */
public class CoreId {
	
	//fields
	public static final String CORE_ID_DESCRIPTION = "LLLDLLDLLL, L=A-Za-z but no OoIiLl, D=2-9";
	public static final Pattern CORE_ID_Pattern = Pattern.compile("[A-Za-z&&[^OoIilL]]{2,3}[2-9][A-Za-z&&[^OoIilL]]{2}[2-9][A-Za-z&&[^OoIilL]]{2,3}");
	private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
	private static final String NUMBERS = "23456789";
	
	// if modifing also do in util.gen.Misc in USeq
	
	public static boolean isCoreId(String testId) {
		return CORE_ID_Pattern.matcher(testId).matches();
	}
	
	public String createCoreId() {
		String eightLetters = getRandomString(8,LETTERS);
		String twoNumbers = getRandomString(2,NUMBERS);
		StringBuilder sb = new StringBuilder();
		sb.append(eightLetters.substring(0, 3)); //0,1,2
		sb.append(twoNumbers.substring(0,1));    //0
		sb.append(eightLetters.substring(3, 5)); //3,4
		sb.append(twoNumbers.substring(1,2));    //1
		sb.append(eightLetters.substring(5, 8)); //5,6,7
		return sb.toString();
	}
	
	private String getRandomString(int length, String saltChars) {
		Random random = new Random();
        StringBuilder salt = new StringBuilder();
        int bound = saltChars.length();
        for (int i=0; i< length; i++) {
            int index = random.nextInt(bound);
            salt.append(saltChars.charAt(index));
        }
        return salt.toString();
    }
	/**Generate and test some coreIds*/
	public static void main (String[] args) {
		CoreId coreId = new CoreId();
		HashSet<String> keys = new HashSet<String>();
		//good for 100K IDs, why not 690 million?
		for (int i=0; i< 10000000; i++) {
			String id = coreId.createCoreId();
			//Util.pl(id+" "+CoreId.isCoreId(id));
			if (keys.add(id)==false) Util.printErrAndExit("Duplicate ID "+id+"\n"+keys);
		}
		Util.pl("None");
	}

}
