# SubjectMatchMaker
Master human subject ID generator and matcher based on PHI keys: lastFirstNames, dob, gender, mrn

<pre>
u0028003$ java -jar -Xmx2G ~/Code/SubjectMatchMaker/target/SubjectMatchMaker_0.1.jar 

**************************************************************************************
**                            Subject Match Maker : April 2022                      **
**************************************************************************************
SMM attempts to match subject's PHI keys (FirstLastName, DoB, Gender, MRN) against a
registry of the same and fetch their unique subject coreIds.  SMM uses a sum of
the key's LevenshteinEditDistance/Length as the distance metric with penalties for
missing keys. Both a json and spreadsheet report are generated. If indicated,
queries not matched will be added to the registry with a new coreId. Use this tool to
assign unique ids to new subjects and find them using missing, partial, typo altered
PHI keys. JUnit tested.

Required:
-r Directory containing one file with the prefix 'currentRegistry_' that contains a
      registry of subjects, tab delimited file(.gz/.zip OK), one subject per line: 
      lastName firstName dobMonth(1-12) dobDay(1-31) dobYear(1900-2050) gender(M|F)
      mrn coreId otherIds. The last two columns are optional. Semicolon delimit
      otherIds. Use space for missing info. CoreIds will be created as needed.
      Example: Biden Joseph 11 20 1942 M 19485763 vd3ec3XR 7474732,847362
-q File containing queries to match to the registry, ditto. Alternatively, provide
      a single column of coreIds to use in fetching subject info from the registry.
-o Directory to write out the match result reports.

Optional:
-a Add query subjects that failed to match to the registry and assign them a coreId.
-s Max edit score for match, defaults to 0.12, smaller scores are more stringent.
-p Score penalty for a single missing key, defaults to 0.12
-k Score penatly for additional missing keys, defaults to 1
-t Number of threads to use, defaults to all.
-m Number of top matches to return per query, defaults to 3

Example: java -jar pathToUSeq/Apps/SubjectIdMatchMaker -r ~/PHI/SMMRegistry 
      -q ~/Tempus/newPatients_PHI.txt -o ~/Tempus/SMMRes/ -a 

**************************************************************************************
</pre>
