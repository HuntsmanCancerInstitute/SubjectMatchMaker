package edu.utah.hci.bioinfo.smm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Util {

	public static final Pattern TAB = Pattern.compile("\t");
	public static final Pattern COMMA = Pattern.compile(",");
	public static final Pattern SEMICOLON = Pattern.compile(";");
	
	public static void pl(Object o) {
		System.out.println(o.toString());
	}
	
	public static void p(Object o) {
		System.out.print(o.toString());
	}
	
	public static void pl() {
		System.out.println();
	}

	
	/**Prints message to screen, then exits.*/
	public static void printErrAndExit (String message){
		System.err.println (message);
		System.exit(1);
	}
	
	/**Prints message to screen, then exits.*/
	public static void el (String message){
		System.err.println (message);
	}
	
	/**Converts a double ddd.dddddddd to a user determined number of decimal places right of the .  */
	public static String formatNumber(double num, int numberOfDecimalPlaces){
		NumberFormat f = NumberFormat.getNumberInstance();
		f.setMaximumFractionDigits(numberOfDecimalPlaces);
		return f.format(num);
	}
	
	/**Returns a gz zip or straight file reader on the file based on it's extension.
	 * @author davidnix*/
	public static BufferedReader fetchBufferedReader( File txtFile) throws IOException{
		BufferedReader in;
		String name = txtFile.getName().toLowerCase();
		if (name.endsWith(".zip")) {
			ZipFile zf = new ZipFile(txtFile);
			ZipEntry ze = (ZipEntry) zf.entries().nextElement();
			in = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)));
		}
		else if (name.endsWith(".gz")) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(txtFile))));
		}
		else in = new BufferedReader (new FileReader (txtFile));
		return in;
	}
	
	/**Returns a String separated by commas for each bin.*/
	public static String stringArrayToString(String[] s, String separator){
		if (s==null) return "";
		int len = s.length;
		if (len==1) return s[0];
		if (len==0) return "";
		StringBuffer sb = new StringBuffer(s[0]);
		for (int i=1; i<len; i++){
			sb.append(separator);
			sb.append(s[i]);
		}
		return sb.toString();
	}
	
	/**Attempts to delete a directory and it's contents.
	 * Returns false if all the file cannot be deleted or the directory is null.
	 * Files contained within scheduled for deletion upon close will cause the return to be false.*/
	public static void deleteDirectory(File dir){
		if (dir == null || dir.exists() == false) return;
		if (dir.isDirectory()) {
			File[] children = dir.listFiles();
			for (int i=0; i<children.length; i++) {
				deleteDirectory(children[i]);
			}
			dir.delete();
		}
		dir.delete();
	}
	
	/**Loads a file's lines into a String, will save blank lines. gz/zip OK.*/
	public static String loadFile(File file, String seperator, boolean trimLeadingTrailing){
		StringBuilder sb = new StringBuilder();
		try{
			BufferedReader in = fetchBufferedReader(file);
			String line;
			while ((line = in.readLine())!=null){
				if (trimLeadingTrailing) line = line.trim();
				sb.append(line);
				sb.append(seperator);
			}
			in.close();
		}catch(Exception e){
			System.out.println("Prob loadFileInto String "+file);
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	/**Loads a file's lines into a String[], will save blank lines. gz/zip OK*/
	public static String[] loadFile(File file){
		ArrayList<String> a = new ArrayList<String>();
		try{
			BufferedReader in = Util.fetchBufferedReader(file);
			String line;
			while ((line = in.readLine())!=null){
				line = line.trim();
				a.add(line);
			}
			in.close();
		}catch(Exception e){
			System.out.println("Prob loadFileInto String[]");
			e.printStackTrace();
		}
		String[] strings = new String[a.size()];
		a.toArray(strings);
		return strings;
	}
	
	/**Extracts the full path file names of all the files in a given directory with a given extension.
	 * If the directory is file and starts with the correct word then it is returned.*/
	public static File[] extractFilesStartingWith(File directory, String startingWith){
		File[] files = null;			
		ArrayList<File> filesAL = new ArrayList<File>();
		if (directory.isDirectory()){
			File[] toExamine;
			toExamine = directory.listFiles();
			for (File f: toExamine){
				if (f.getName().startsWith(startingWith)) filesAL.add(f);
			}
			files = new File[filesAL.size()];
			filesAL.toArray(files);
		}
		else if (directory.getName().startsWith(startingWith)) files = new File[]{directory};
		return files;
	}
	
	/**Executes a String of shell script commands via a temp file.  Only good for Unix.
	 * @throws IOException */
	public static String[] executeShellScript (String shellScript, File tempDirectory) throws IOException{
		//make shell file
		File shellFile = new File (tempDirectory, new Double(Math.random()).toString().substring(2)+"_TempFile.sh");
		shellFile.deleteOnExit();
		//write to shell file
		write(new String[] {shellScript}, shellFile);
		shellFile.setExecutable(true);
		//execute
		String[] cmd = new String[]{"bash", shellFile.getCanonicalPath()};
		String[] res = executeViaProcessBuilder(cmd, false, null);
		shellFile.delete();
		return res; 
	}
    /**Writes String[] as lines to the file.*/
	public static void write(String[] attributes, File p) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (p));
		for (String s: attributes) out.println(s);
		out.close();
	}
    /**Uses ProcessBuilder to execute a cmd, combines standard error and standard out into one and returns their output.
     * @throws IOException */
    public static String[] executeViaProcessBuilder(String[] command, boolean printToStandardOut, Map<String,String> envVarToAdd) throws IOException{
    	ArrayList<String> al = new ArrayList<String>();
    	ProcessBuilder pb = new ProcessBuilder(command);
    	pb.redirectErrorStream(true);
    	Process proc = pb.start();
    	BufferedReader data = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    	String line;
    	while ((line = data.readLine()) != null){
    		al.add(line);
    		if (printToStandardOut) System.out.println(line);
    	}
    	data.close();
    	String[] res = new String[al.size()];
    	al.toArray(res);
    	return res;
    }

}
