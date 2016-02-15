package model.mln;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.feature.CausalSignalList;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.PairType;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.CausalRelation;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.TemporalRelation;
import task.CausalTimeBankTask;

public class BuildEvidenceTimeBankDense {
	
	private String[] label = {"BEFORE", "AFTER", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "VAGUE"};
	private List<String> labelList = Arrays.asList(label);
	
	public static final String[] devDocs = { 
		"APW19980227.0487.tml", 
		"CNN19980223.1130.0960.tml", 
		"NYT19980212.0019.tml",  
		"PRI19980216.2000.0170.tml", 
		"ed980111.1130.0089.tml" 
	};
	
	public static final String[] testDocs = { 
		"APW19980227.0489.tml",
		"APW19980227.0494.tml",
		"APW19980308.0201.tml",
		"APW19980418.0210.tml",
		"CNN19980126.1600.1104.tml",
		"CNN19980213.2130.0155.tml",
		"NYT19980402.0453.tml",
		"PRI19980115.2000.0186.tml",
		"PRI19980306.2000.1675.tml" 
	};
	
	public static Map<String, Map<String, String>> getTLINKs(String tlinkPath, boolean inverse) throws Exception {
		Map<String, Map<String, String>> tlinkPerFile = new HashMap<String, Map<String, String>>();
		
		BufferedReader br = new BufferedReader(new FileReader(new File(tlinkPath)));
		String line;
		String filename, e1, e2, tlink;
	    while ((line = br.readLine()) != null) {
	    	String[] cols = line.split("\t");
	    	filename = cols[0] + ".tml";
	    	e1 = cols[1]; e2 = cols[2];
	    	if (e1.startsWith("t")) e1 = e1.replace("t", "tmx");
	    	if (e2.startsWith("t")) e2 = e2.replace("t", "tmx");
	    	tlink = getRelTypeTimeBankDense(cols[3]);
	    	
	    	if (!tlinkPerFile.containsKey(filename)) {
	    		tlinkPerFile.put(filename, new HashMap<String, String>());
	    	}
    		tlinkPerFile.get(filename).put(e1+"\t"+e2, tlink);
    		if (inverse) tlinkPerFile.get(filename).put(e2+"\t"+e1, getInverseRelTypeTimeBankDense(tlink));
	    }
	    br.close();
		
		return tlinkPerFile;
	}
	
	public static String getInverseRelTypeTimeBankDense(String type) {
		switch(type) {
			case "BEFORE": return "AFTER";
			case "AFTER": return "BEFORE";
			case "INCLUDES": return "IS_INCLUDED";
			case "IS_INCLUDED": return "INCLUDES";
			default: return type;
		}
	}
	
	public static String getRelTypeTimeBankDense(String type) {
		switch(type) {
			case "s": return "SIMULTANEOUS";
			case "b": return "BEFORE";
			case "a": return "AFTER";
			case "i": return "INCLUDES";
			case "ii": return "IS_INCLUDED";
			default: return "VAGUE";
		}
	}
	
	private static boolean exists(String name, String[] names) {
		for( String nn : names )
			if( name.equals(nn) ) return true;
		return false;
	}

	public static void main(String [] args) throws Exception {
		
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.clink, Field.csignal};
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);
		
		String dirTxpPath = "./data/Causal-TimeBank_TXP/";
		
		File[] txpFiles = new File(dirTxpPath).listFiles();
		
		//TimeBank-Dense
		Map<String, Map<String, String>> tlinkPerFile = getTLINKs("./data/TimebankDense.T3.txt", false);
				
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		Integer f = 1;
		for (String filename : tlinkPerFile.keySet()) {	//assuming that there is no sub-directory
			File txpFile = new File(dirTxpPath, filename + ".txp");
			
			if (exists(filename, devDocs) && txpFile.isFile()) {
//			if (exists(filename, testDocs) && txpFile.isFile()) {
				
				Doc docTxp = txpParser.parseDocument(txpFile.getPath());
				BufferedWriter bw = new BufferedWriter(new FileWriter("./data/mln/timebank-dense/"+txpFile.getName()+".db"));
				
				for (String pair : tlinkPerFile.get(filename).keySet()) {	//for every TLINK in TimeBank-Dense file
					String sourceID = pair.split("\t")[0];
					String targetID = pair.split("\t")[1];
					String tlinkType = tlinkPerFile.get(filename).get(pair);
					
					if (!sourceID.equals(targetID)
							&& docTxp.getEntities().containsKey(sourceID)
							&& docTxp.getEntities().containsKey(targetID)
							) {	
						
						Entity e1 = docTxp.getEntities().get(sourceID);
						Entity e2 = docTxp.getEntities().get(targetID);
						PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlinkType, tsignalList, csignalList);	
						
						if (fv.getPairType().equals(PairType.event_event)) {
							bw.write("RelEE("
									+ sourceID.toUpperCase() + ", "
									+ targetID.toUpperCase() + ", "
									+ tlinkType
									+ ")\n");
						} else if (fv.getPairType().equals(PairType.event_timex)) {
							if (sourceID.contains("t")) {
								bw.write("RelTE("
										+ sourceID.toUpperCase() + ", "
										+ targetID.toUpperCase() + ", "
										+ tlinkType
										+ ")\n");
							} else {
								bw.write("RelET("
										+ sourceID.toUpperCase() + ", "
										+ targetID.toUpperCase() + ", "
										+ tlinkType
										+ ")\n");
							}
						} else if (fv.getPairType().equals(PairType.timex_timex)) {
							bw.write("RelTT("
									+ sourceID.toUpperCase() + ", "
									+ targetID.toUpperCase() + ", "
									+ tlinkType
									+ ")\n");
						}
					}
				}
				for (CausalRelation clink : docTxp.getClinks()) {
					bw.write("RelEE("
							+ clink.getSourceID().toUpperCase() + ", "
							+ clink.getTargetID().toUpperCase() + ", "
							+ "CAUSAL"
							+ ")\n");
				}
				bw.close();
				f ++;
			}
			
		}
	}
}
