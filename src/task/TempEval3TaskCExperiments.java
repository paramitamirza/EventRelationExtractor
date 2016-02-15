package task;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import evaluator.PairEvaluator;
import evaluator.TempEval3;
import model.classifier.EventDctRelationClassifier;
import model.classifier.EventEventRelationClassifier;
import model.classifier.EventTimexRelationClassifier;
import model.classifier.PairClassifier;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.FeatureName;
import model.feature.FeatureEnum.PairType;
import model.rule.EventEventRelationRule;
import model.rule.EventTimexRelationRule;
import model.rule.TimeGraph;
import model.rule.TimexTimexRelationRule;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.TimeMLParser;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.TimeMLDoc;
import parser.entities.Timex;
import server.RemoteServer;

class TempEval3TaskCExperiments {
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	private List<String> labelList = Arrays.asList(label);
	
	private int numTotalCandidate;
	private int numCorrectCandidate;
	private String taskName;
	
	public TempEval3TaskCExperiments() {
		numTotalCandidate = 0;
		numCorrectCandidate = 0;
		taskName = "te3-c";
	}
	
	public Map<String,String> getTimexTimexRuleRelation(Doc doc) {
		Object[] entArr = doc.getEntities().keySet().toArray();
		Map<String,String> ttlinks = new HashMap<String,String>();
		String pair = null, pairInv = null;
		for (int i = 0; i < entArr.length; i++) {
			for (int j = i; j < entArr.length; j++) {
				if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
						doc.getEntities().get(entArr[j]) instanceof Timex) {
					TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
							((Timex)doc.getEntities().get(entArr[j])), doc.getDct(), false);
					if (!timextimex.getRelType().equals("O")) {
						pair = ((String) entArr[i]) + "\t" + ((String) entArr[j]);
						pairInv = ((String) entArr[j]) + "\t" + ((String) entArr[i]);
						ttlinks.put(pair, timextimex.getRelType());
						ttlinks.put(pairInv, TemporalRelation.getInverseRelation(timextimex.getRelType()));
					}
				}
			}
		}
		return ttlinks;
	}
	
	public List<String> getTimexTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, boolean goldCandidate) throws Exception {
		List<String> tt = new ArrayList<String>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<String,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate) candidateTlinks = docTml.getTlinks();	//gold annotated pairs
		else candidateTlinks = docTxp.getTlinks();									//candidate pairs
		
		for (TemporalRelation tlink : candidateTlinks) {	
			
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					String st = tlink.getSourceID() + "\t" + tlink.getTargetID();
					if (ttlinks.containsKey(st)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\tNONE");
					}
				}
			}
		}
		return tt;
	}
	
	public List<PairFeatureVector> getEventDctTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) candidateTlinks = docTml.getTlinks();	//gold annotated pairs
		else candidateTlinks = docTxp.getTlinks();									//candidate pairs
		
		for (TemporalRelation tlink : candidateTlinks) {	
			
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					
					if (((Timex) etfv.getE2()).isDct()) {
						
						//Add features to feature vector
						if (etRelCls.classifier.equals(VectorClassifier.yamcha)) {
							etfv.addToVector(FeatureName.id);
						}
						
						for (FeatureName f : etRelCls.featureList) {
							if (etRelCls.classifier.equals(VectorClassifier.libsvm) ||
									etRelCls.classifier.equals(VectorClassifier.liblinear) ||
									etRelCls.classifier.equals(VectorClassifier.weka)) {
								etfv.addBinaryFeatureToVector(f);
							} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
									etRelCls.classifier.equals(VectorClassifier.none)) {
								etfv.addToVector(f);
							}
						}
						
						if (etRelCls.classifier.equals(VectorClassifier.libsvm) || 
								etRelCls.classifier.equals(VectorClassifier.liblinear)) {
							if (train) etfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
							else etfv.addBinaryFeatureToVector(FeatureName.label);
						} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
								etRelCls.classifier.equals(VectorClassifier.weka) ||
								etRelCls.classifier.equals(VectorClassifier.none)){
							if (train) etfv.addToVector(FeatureName.labelCollapsed);
							else etfv.addToVector(FeatureName.label);
						}
						
						fvList.add(etfv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) candidateTlinks = docTml.getTlinks();	//gold annotated pairs
		else candidateTlinks = docTxp.getTlinks();									//candidate pairs
		
		for (TemporalRelation tlink : candidateTlinks) {	
			
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					
					if (!((Timex) etfv.getE2()).isDct()) {
					
						//Add features to feature vector
						if (etRelCls.classifier.equals(VectorClassifier.yamcha)) {
							etfv.addToVector(FeatureName.id);
						}
						
						for (FeatureName f : etRelCls.featureList) {
							if (etRelCls.classifier.equals(VectorClassifier.libsvm) ||
									etRelCls.classifier.equals(VectorClassifier.liblinear) ||
									etRelCls.classifier.equals(VectorClassifier.weka)) {
								etfv.addBinaryFeatureToVector(f);
							} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
									etRelCls.classifier.equals(VectorClassifier.none)) {
								etfv.addToVector(f);
							}
						}
						
						if (etRelCls.classifier.equals(VectorClassifier.libsvm) || 
								etRelCls.classifier.equals(VectorClassifier.liblinear)) {
							if (train) etfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
							else etfv.addBinaryFeatureToVector(FeatureName.label);
						} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
								etRelCls.classifier.equals(VectorClassifier.weka) ||
								etRelCls.classifier.equals(VectorClassifier.none)){
							if (train) etfv.addToVector(FeatureName.labelCollapsed);
							else etfv.addToVector(FeatureName.label);
						}
						
						if (train && !fv.getVectors().get(fv.getVectors().size()-1).equals("0")
								&& !fv.getVectors().get(fv.getVectors().size()-1).equals("NONE")) {
							fvList.add(etfv);
						} else if (!train){ //test
							//add all
							fvList.add(etfv);
						}
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier eeRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) candidateTlinks = docTml.getTlinks();	//gold annotated pairs
		else candidateTlinks = docTxp.getTlinks();									//candidate pairs
		
		for (TemporalRelation tlink : candidateTlinks) {	
			
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
					
					//Add features to feature vector
					if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
						eefv.addToVector(FeatureName.id);
					}
					
					for (FeatureName f : eeRelCls.featureList) {
						if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
								eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
								eeRelCls.classifier.equals(VectorClassifier.weka)) {
							eefv.addBinaryFeatureToVector(f);
						} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
								eeRelCls.classifier.equals(VectorClassifier.none)) {
							eefv.addToVector(f);
						}
					}
					
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
							eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
						if (train) eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else eefv.addBinaryFeatureToVector(FeatureName.label);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						if (train) eefv.addToVector(FeatureName.labelCollapsed);
						else eefv.addToVector(FeatureName.label);
					}
					
					fvList.add(eefv);
				}
			}
		}
		return fvList;
	}
	
	public void writeTimeMLFile(TimeMLParser tmlParser, File tmlFile,
			List<String> ttResult, List<String> etResult, List<String> eeResult,
			String systemTMLPath) 
					throws Exception {
		Doc dTml = tmlParser.parseDocument(tmlFile.getPath());
		TimeMLDoc tml = new TimeMLDoc(tmlFile.getPath());
		tml.removeLinks();
		
		HashSet<String> goldTlinks = new HashSet<String>();
		for (TemporalRelation tlink : dTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			goldTlinks.add(tlink.getSourceID()+"\t"+tlink.getTargetID());
			goldTlinks.add(tlink.getTargetID()+"\t"+tlink.getSourceID());
		}
		
		int linkId = 1;
		TemporalRelation tlink = new TemporalRelation();
		for (String ttStr : ttResult) {
			if (!ttStr.isEmpty()) {
				String[] cols = ttStr.split("\t");
				if (!cols[3].equals("NONE")) {
					tlink.setSourceID(cols[0].replace("tmx", "t"));
					tlink.setTargetID(cols[1].replace("tmx", "t"));
					tlink.setRelType(cols[3]);
					tlink.setSourceType("Timex");
					tlink.setTargetType("Timex");
	//				if (goldTlinks.contains(cols[0]+"\t"+cols[1])) {
						tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
						linkId += 1;
	//				}
				}
			}
		}
		for (String etStr : etResult) {
			if (!etStr.isEmpty()) {
				String[] cols = etStr.split("\t");
				if (!cols[3].equals("NONE")) {
					tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
					tlink.setTargetID(cols[1].replace("tmx", "t"));
					tlink.setRelType(cols[3]);
					tlink.setSourceType("Event");
					tlink.setTargetType("Timex");
	//				if (goldTlinks.contains(cols[0]+"\t"+cols[1])) {
						tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
						linkId += 1;
	//				}
				}
			}
		}
		for (String eeStr : eeResult) {
			if (!eeStr.isEmpty()) {
				String[] cols = eeStr.split("\t");
				if (!cols[3].equals("NONE")) {
					tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
					tlink.setTargetID(dTml.getInstancesInv().get(cols[1]));
					tlink.setRelType(cols[3]);
					tlink.setSourceType("Event");
					tlink.setTargetType("Event");
	//				if (goldTlinks.contains(cols[0]+"\t"+cols[1])) {
						tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
						linkId += 1;
	//				}
				}
			}
		}
		
		File sysTmlPath = new File(systemTMLPath + "/" + tmlFile.getName());
		PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
		sysTML.write(tml.toString());
		sysTML.close();
	}
	
	public void writeTimeMLFile(TimeMLParser tmlParser, File tmlFile,
			Map<String, String> tlinks,
			String systemTMLPath) 
					throws Exception {
		Doc dTml = tmlParser.parseDocument(tmlFile.getPath());
		TimeMLDoc tml = new TimeMLDoc(tmlFile.getPath());
		tml.removeLinks();
		
		int linkId = 1;
		TemporalRelation tlink = new TemporalRelation();
		String[] cols;
		for (String key : tlinks.keySet()) {
			if (!tlinks.get(key).equals("NONE")) {
				cols = key.split("\t");
				if (cols[0].startsWith("t") && cols[1].startsWith("t")) {	//timex-timex
					tlink.setSourceID(cols[0].replace("tmx", "t"));
					tlink.setTargetID(cols[1].replace("tmx", "t"));
					tlink.setRelType(tlinks.get(key));
					tlink.setSourceType("Timex");
					tlink.setTargetType("Timex");
					tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
					linkId += 1;
				} else if (cols[0].startsWith("e") && cols[1].startsWith("t")) {	//event-timex
					tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
					tlink.setTargetID(cols[1].replace("tmx", "t"));
					tlink.setRelType(tlinks.get(key));
					tlink.setSourceType("Event");
					tlink.setTargetType("Timex");
					tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
					linkId += 1;
				} else if (cols[0].startsWith("e") && cols[1].startsWith("e")) {	//event-event
					tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
					tlink.setTargetID(dTml.getInstancesInv().get(cols[1]));
					tlink.setRelType(tlinks.get(key));
					tlink.setSourceType("Event");
					tlink.setTargetType("Event");
					tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
					linkId += 1;
				}
			}
		}
		
		File sysTmlPath = new File(systemTMLPath + "/" + tmlFile.getName());
		PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
		sysTML.write(tml.toString());
		sysTML.close();
	}
	
	private String getInverseClinkLabel(String clink) {
		if (clink.equals("CLINK")) return "CLINK-R";
		else if (clink.equals("CLINK-R")) return "CLINK";
		return "NONE";
	}
	
	public Map<String, Map<String, String>> getCLINKs(String causalResultPath) throws Exception {
		Map<String, Map<String, String>> clinkPerFile = new HashMap<String, Map<String, String>>();
		
		BufferedReader br = new BufferedReader(new FileReader(new File(causalResultPath)));
		String line;
		String filename, e1, e2, clink;
	    while ((line = br.readLine()) != null) {
	    	String[] cols = line.split("\t");
	    	filename = cols[0];
	    	e1 = cols[1]; e2 = cols[2];
	    	clink = cols[3];
	    	
	    	if (!clinkPerFile.containsKey(filename)) {
	    		clinkPerFile.put(filename, new HashMap<String, String>());
	    	}
    		clinkPerFile.get(filename).put(e1+"-"+e2, clink);
    		clinkPerFile.get(filename).put(e2+"-"+e1, getInverseClinkLabel(clink));
	    }
	    br.close();
		
		return clinkPerFile;
	}
	
	public List<PairFeatureVector> getEventTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier etRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventTimexTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train, goldCandidate));
		}
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventTimexSplitTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, 
			PairClassifier dctRelCls, PairClassifier etRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		List<PairFeatureVector> fvDctList = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvNonDctList = new ArrayList<PairFeatureVector>();
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvDctList.addAll(getEventDctTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, dctRelCls, train, goldCandidate));
			fvNonDctList.addAll(getEventTimexTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train, goldCandidate));
		}
		fvList.add(fvDctList);
		fvList.add(fvNonDctList);
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier eeRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventEventTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, eeRelCls, train, goldCandidate));
		}
		return fvList;
	}
	
	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		
		TempEval3TaskCExperiments task = new TempEval3TaskCExperiments();
		
//		PrintStream out = new PrintStream(new FileOutputStream("temporal_output.txt"));
//		System.setOut(out);
//		PrintStream log = new PrintStream(new FileOutputStream("temporal_log.txt"));
//		System.setErr(log);
		
		boolean taskCRelOnly = false;
		boolean trainModels = true;
		boolean precisionOnly = true;
		
		boolean tlinkFromDCTRules = true;
		boolean tlinkFromEERules = true;
		boolean tlinkFromETRules = true;
		
		boolean tlinkFromDCTClassifier = true;
		boolean tlinkFromEEClassifier = true;
		boolean tlinkFromETClassifier = true;
		
		boolean tlinkFromInferredMLN = false;
		boolean tlinkFromRESTReasoner = true;
		boolean tlinkNonCandidateFromInferred = false;
		
		boolean postTimeGraph = false;
		boolean evaluateTempEval3 = false;
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String trainTxpDirpath = "./data/TempEval3-train_TXP2/";
		String trainTmlDirpath = "./data/TempEval3-train_TML/";
		
		String evalTxpDirpath = "./data/TempEval3-eval_TXP/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
		//TimeML directory for system result files
		String systemTMLPath = "data/TempEval3-system_TML";
		File sysDir = new File(systemTMLPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		//Init classifiers
		
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("te3", "liblinear");
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("te3", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("te3", "liblinear");
		
		
		//Train classifiers
		if (trainModels) {
			List<List<PairFeatureVector>> etTrainFvList = task.getEventTimexSplitTlinks(txpParser, tmlParser, 
					trainTxpDirpath, trainTmlDirpath, dctCls, etCls, true, true);
			List<PairFeatureVector> eeTrainFvList = task.getEventEventTlinks(txpParser, tmlParser, 
					trainTxpDirpath, trainTmlDirpath, eeCls, true, true);
			
			dctCls.train(etTrainFvList.get(0), "models/" + task.taskName + "-dct.model");
			etCls.train(etTrainFvList.get(1), "models/" + task.taskName + "-et.model");   
			eeCls.train(eeTrainFvList, "models/" + task.taskName + "-ee.model");
		}
		
		Map<String, Map<String, String>> clinkPerFile = task.getCLINKs("causality_result_all_auto_tlinks.txt");
		
		List<String> ttResult = new ArrayList<String>();
		List<String> dctResult = new ArrayList<String>();
		List<String> etResult = new ArrayList<String>();
		List<String> eeResult = new ArrayList<String>();
		
		int numCorrected=0, dctNum=0, etNum=0;
		
		File[] txpFiles = new File(evalTxpDirpath).listFiles();
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				File tmlFile = new File(evalTmlDirpath, txpFile.getName().replace(".txp", ""));
				System.err.println(tmlFile.getName());
				Doc docTxp = txpParser.parseDocument(txpFile.getPath());
				Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
				
				BufferedWriter bw = new BufferedWriter(new FileWriter("./data/mln/TempEval3/"+txpFile.getName()+".db"));
				String ttStr = "", etStr = "", eeStr = "";
				String ttTG = "", etTG = "", eeTG = "";
				List<String> dctTestList = new ArrayList<String>();
				List<String> etTestList = new ArrayList<String>();
				List<String> eeTestList = new ArrayList<String>();
				
				Set<String> extracted = new HashSet<String>();
				
				//CANDIDATE PAIRS
				
				//event-DCT
				List<PairFeatureVector> dctFvList = task.getEventDctTlinksPerFile(txpParser, tmlParser, 
						txpFile, tmlFile, dctCls, false, taskCRelOnly);
				dctNum += dctFvList.size();
				task.numTotalCandidate += dctFvList.size();
				for (PairFeatureVector fv : dctFvList) {
					if (!fv.getLabel().equals("NONE")) task.numCorrectCandidate ++;
				}
				List<PairFeatureVector> dctFvListCls = new ArrayList<PairFeatureVector>();
				
				//event-timex
				List<PairFeatureVector> etFvList = task.getEventTimexTlinksPerFile(txpParser, tmlParser, 
						txpFile, tmlFile, etCls, false, taskCRelOnly);
				etNum += etFvList.size();
				task.numTotalCandidate += etFvList.size();
				for (PairFeatureVector fv : etFvList) {
					if (!fv.getLabel().equals("NONE")) task.numCorrectCandidate ++;
				}
				List<PairFeatureVector> etFvListCls = new ArrayList<PairFeatureVector>();
				
				//event-event
				List<PairFeatureVector> eeFvList = task.getEventEventTlinksPerFile(txpParser, tmlParser, 
						txpFile, tmlFile, eeCls, false, taskCRelOnly);
				task.numTotalCandidate += eeFvList.size();
				for (PairFeatureVector fv : eeFvList) {
					if (!fv.getLabel().equals("NONE")) task.numCorrectCandidate ++;
				}
				List<PairFeatureVector> eeFvListCls = new ArrayList<PairFeatureVector>();
			
				
				//RULE-BASED
				
				//timex-timex pairs
				List<String> ttPerFile = new ArrayList<String>();
				if (taskCRelOnly) {
					//gold candidate timex-timex
					ttPerFile = task.getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile, taskCRelOnly);
					for (String tt : ttPerFile) {
						ttTG = "gold\t" + tt.split("\t")[0] + "\t" + tt.split("\t")[1] + "\t"+ tt.split("\t")[3] + "\n" + ttTG;
						ttStr += "RelTT(" + tt.split("\t")[0] + ", " + tt.split("\t")[1] + ", "+ tt.split("\t")[3] + ")\n";
						extracted.add(tt.split("\t")[0]+"-"+tt.split("\t")[1]);
					}
				
				} else {
					//rule-based timex-timex
					ttPerFile = new ArrayList<String>();
					Map<String,String> ttlinks = task.getTimexTimexRuleRelation(docTxp);
					for (String tt : ttlinks.keySet()) {
						TemporalRelation trel = new TemporalRelation(tt.split("\t")[0], tt.split("\t")[1]);
						trel.setRelType(ttlinks.get(tt));
						
						String goldLabel = "NONE";
						if (docTml.getTlinks().contains(trel)) {
							int idx = docTml.getTlinks().indexOf(trel);
							goldLabel = docTml.getTlinks().get(idx).getRelType();
							task.numCorrectCandidate ++;
						}
						
						ttPerFile.add(trel.getSourceID() + "\t" + trel.getTargetID() 
								+ "\t"+ goldLabel
								+ "\t"+ trel.getRelType());
						ttTG = "gold\t" + trel.getSourceID() + "\t" + trel.getTargetID() + "\t"+ trel.getRelType() + "\n" + ttTG;
						ttStr += "RelTT(" + trel.getSourceID() + ", " + trel.getTargetID() + ", "+ trel.getRelType() + ")\n";
						extracted.add(trel.getSourceID()+"-"+trel.getTargetID());
					}
				}
				
				//event-dct
				if (tlinkFromDCTRules) {
					for (PairFeatureVector fv : dctFvList) {
						//Find label according to rules
						EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
						EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
								docTxp, etfv.getMateDependencyPath());
						
						String label = etRule.getRelType();
						if (!label.equals("O")) {
							dctTestList.add(etfv.getE1().getID() 
									+ "\t" + etfv.getE2().getID()
									+ "\t" + etfv.getLabel()
									+ "\t" + label);
							etTG = "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n" + etTG;
							etStr += "RelET(" + etfv.getE1().getID() + ", " + etfv.getE2().getID() + ", " + label + ")\n";
							extracted.add(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						} else {
							dctFvListCls.add(fv);
						}
					}
				} else {
					dctFvListCls.addAll(dctFvList);
				}
				
				//event-timex
				if (tlinkFromETRules) {
					for (PairFeatureVector fv : etFvList) {
						//Find label according to rules
						EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
						EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
								docTxp, etfv.getMateDependencyPath());
						
						String label = etRule.getRelType();
						if (!label.equals("O")) {
							etTestList.add(etfv.getE1().getID() 
									+ "\t" + etfv.getE2().getID()
									+ "\t" + etfv.getLabel()
									+ "\t" + label);
							etTG = "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n" + etTG;
							etStr += "RelET(" + etfv.getE1().getID() + ", " + etfv.getE2().getID() + ", " + label + ")\n";
							extracted.add(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						} else {
							etFvListCls.add(fv);
						}
					}
				} else {
					etFvListCls.addAll(etFvList);
				}
				
				//event-event
				if (tlinkFromEERules) {
					for (PairFeatureVector fv : eeFvList) {
						//Find label according to rules
						EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
						EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
								docTxp, eefv.getMateDependencyPath());
						 
						String label = eeRule.getRelType();
						if (!label.equals("O")) {
							if (label.equals("IDENTITY")) label = "SIMULTANEOUS";
							eeTestList.add(eefv.getE1().getID() 
									+ "\t" + eefv.getE2().getID() 
									+ "\t" + eefv.getLabel()
									+ "\t" + label);
							eeTG = "gold\t" + eefv.getE1().getID() + "\t" + eefv.getE2().getID() + "\t" + label + "\n" + eeTG;
							eeStr += "RelEE(" + eefv.getE1().getID() + ", " + eefv.getE2().getID() + ", " + label + ")\n";
							extracted.add(eefv.getE1().getID()+"-"+eefv.getE2().getID());
						}
	//					else if (eefv.isCoreference()) label = "IDENTITY";	//--> doesn't work
						else {
							eeFvListCls.add(fv);
						}
					}
				} else {
					eeFvListCls.addAll(eeFvList);
				}
				
				//TEMPORAL CLOSURE
				
				Map<String, String> inferredPairs = new HashMap<String, String>();
				Set<String> inferred = inferredPairs.keySet();
				
				//MLN inferred TLINKs
				if (tlinkFromInferredMLN) {
					//Data preparation
					bw.write(ttStr + etStr + eeStr);
					bw.close();
					
					//Read inferred TLINKs
					BufferedReader br = new BufferedReader(new FileReader("./data/mln/TempEval3-inferred/" + docTxp.getFilename() + ".db.txt"));
					String line, rel;
					while ((line = br.readLine()) != null) {
						rel = line.replaceAll("\"", "");
						rel = rel.replace("(", "\t");
						rel = rel.replace(")", "");
						rel = rel.replaceAll(", ", "\t");
						String[] cols = rel.split("\t");
						if (Double.parseDouble(cols[0]) > 0.5) {
							inferredPairs.put(cols[2]+"-"+cols[3], cols[4]);
						}
					}
				}
				
				//RESTReasoner
				if (tlinkFromRESTReasoner) {
					//Data preparation
					etTestList.addAll(dctTestList);
					task.writeTimeMLFile(tmlParser, tmlFile, 
					ttPerFile, etTestList, eeTestList,
					systemTMLPath);
					
					//Read deduced TLINKs
					String systemDeducedTMLPath = "data/TempEval3-system_TML_deduced";
					File deducedTmlFile = new File(systemDeducedTMLPath, tmlFile.getName());
					Doc docDedTml = tmlParser.parseDocument(deducedTmlFile.getPath());
					for (TemporalRelation tlink : docDedTml.getTlinks()) {
						if (tlink.isDeduced()) {
							inferredPairs.put(tlink.getSourceID()+"-"+tlink.getTargetID(), tlink.getRelType());
							inferredPairs.put(tlink.getTargetID()+"-"+tlink.getSourceID(), TemporalRelation.getInverseRelation(tlink.getRelType()));
						}
					}
				}
				
				//CLASSIFIERS
				
				//event-dct
				List<String> dctClsTest = dctCls.predict(dctFvListCls, "models/" + task.taskName + "-dct.model");
				for (int i=0; i<dctFvListCls.size(); i++) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(dctFvListCls.get(i));
					String label = "NONE";
					
					if (tlinkFromDCTClassifier) label = dctClsTest.get(i);
					
					if (inferredPairs.containsKey(etfv.getE1().getID()+"-"+etfv.getE2().getID())) {
						label = inferredPairs.get(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						inferred.remove(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						etTG = "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n" + etTG;
					} else {
						etTG += "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n";
					}
					dctTestList.add(etfv.getE1().getID() 
							+ "\t" + etfv.getE2().getID()
							+ "\t" + etfv.getLabel()
							+ "\t" + label);
					extracted.add(etfv.getE1().getID()+"-"+etfv.getE2().getID());
				}
				
				//event-timex
				List<String> etClsTest = etCls.predict(etFvListCls, "models/" + task.taskName + "-et.model");
				for (int i=0; i<etFvListCls.size(); i++) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(etFvListCls.get(i));
					String label = "NONE";
					
					if (tlinkFromETClassifier) label = etClsTest.get(i);
					
					if (inferredPairs.containsKey(etfv.getE1().getID()+"-"+etfv.getE2().getID())) {
						label = inferredPairs.get(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						inferred.remove(etfv.getE1().getID()+"-"+etfv.getE2().getID());
						etTG = "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n" + etTG;
					} else {
						etTG += "gold\t" + etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + label + "\n";
					}
					etTestList.add(etfv.getE1().getID() 
							+ "\t" + etfv.getE2().getID()
							+ "\t" + etfv.getLabel()
							+ "\t" + label);
					extracted.add(etfv.getE1().getID()+"-"+etfv.getE2().getID());
				}
				
				//event-event
				List<String> eeClsTest = eeCls.predict(eeFvListCls, "models/" + task.taskName + "-ee.model");	
				for (int i=0; i<eeFvListCls.size(); i++) {
					EventEventFeatureVector eefv = new EventEventFeatureVector(eeFvListCls.get(i));
					String label = "NONE";
					
					if (tlinkFromEEClassifier) label = eeClsTest.get(i);
					
					if (inferredPairs.containsKey(eefv.getE1().getID()+"-"+eefv.getE2().getID())) {
						label = inferredPairs.get(eefv.getE1().getID()+"-"+eefv.getE2().getID());
						inferred.remove(eefv.getE1().getID()+"-"+eefv.getE2().getID());
						eeTG = "gold\t" + eefv.getE1().getID() + "\t" + eefv.getE2().getID() + "\t" + label + "\n" + eeTG;
					} else {
						eeTG += "gold\t" + eefv.getE1().getID() + "\t" + eefv.getE2().getID() + "\t" + label + "\n";
					}
					if (label.equals("IDENTITY")) label = "SIMULTANEOUS";
					eeTestList.add(eefv.getE1().getID() 
							+ "\t" + eefv.getE2().getID()
							+ "\t" + eefv.getLabel()
							+ "\t" + label);
					extracted.add(eefv.getE1().getID()+"-"+eefv.getE2().getID());
				}
				
				//ADD INFERRED/DEDUCED BUT NOT CANDIDATE
				if (tlinkNonCandidateFromInferred &&
						(tlinkFromInferredMLN || tlinkFromRESTReasoner)) {
					for (String key : inferred) {
						String source = key.split("-")[0];
						String target = key.split("-")[1];
						String tlink = inferredPairs.get(key);
						if (!extracted.contains(target+"-"+source)) {
							if (source.startsWith("t") && target.startsWith("t")) {
								ttPerFile.add(source + "\t" + target
										+ "\tNONE" + "\t" + tlink);
								ttTG = "gold\t" + source + "\t" + target + "\t" + tlink + "\n" + ttTG;
							} else if (source.startsWith("e") && target.startsWith("t")) {
								if (target.endsWith("0")) {
									dctTestList.add(source + "\t" + target
											+ "\tNONE" + "\t" + tlink);
								} else {
									etTestList.add(source + "\t" + target
											+ "\tNONE" + "\t" + tlink);
								}
								etTG = "gold\t" + source + "\t" + target + "\t" + tlink + "\n" + etTG;
							} else if (source.startsWith("e") && target.startsWith("e")) {
								eeTestList.add(source + "\t" + target
										+ "\tNONE" + "\t" + tlink);
								eeTG = "gold\t" + source + "\t" + target + "\t" + tlink + "\n" + eeTG;
							}
						}
					}
				}
				
				for (String tlink : ttPerFile) {
					if (precisionOnly) {
						if (!tlink.endsWith("NONE")) ttResult.add(tlink);
					} else ttResult.add(tlink);
				}
				for (String tlink : dctTestList) {
					if (precisionOnly) {
						if (!tlink.endsWith("NONE")) dctResult.add(tlink);
					} else dctResult.add(tlink);
				}
				for (String tlink : etTestList) {
					if (precisionOnly) {
						if (!tlink.endsWith("NONE")) etResult.add(tlink);
					} else etResult.add(tlink);
				}
				for (String tlink : eeTestList) {
					if (precisionOnly) {
						if (!tlink.endsWith("NONE")) eeResult.add(tlink);
					} else eeResult.add(tlink);
				}
				
				//Write the TimeML document with new TLINKs
				etTestList.addAll(dctTestList);
				task.writeTimeMLFile(tmlParser, tmlFile, 
						ttPerFile, etTestList, eeTestList,
						systemTMLPath);
				
				//TIMEGRAPH
				if (postTimeGraph) {
					TimeGraph tg = new TimeGraph(ttTG + etTG + eeTG);
					HashMap<String, String> finalTlinks = new HashMap<String, String>();
					finalTlinks.putAll(tg.finalRel);
					finalTlinks.putAll(tg.removeRel);
					for (String key : tg.violatedRel.keySet()) finalTlinks.remove(key);
					task.writeTimeMLFile(tmlParser, tmlFile, finalTlinks, systemTMLPath);
				}
			}
		}
		
		System.out.println("event-DCT: " + dctNum + " event-tmx: " + etNum);
		System.out.println("total candidate: " + task.numTotalCandidate
				+ " correct candidate: " + task.numCorrectCandidate);
		
		PairEvaluator ptt = new PairEvaluator(ttResult);
		ptt.evaluatePerLabel(task.label);
		PairEvaluator pdct = new PairEvaluator(dctResult);
		pdct.evaluatePerLabel(task.label);
		PairEvaluator pet = new PairEvaluator(etResult);
		pet.evaluatePerLabel(task.label);
		PairEvaluator pee = new PairEvaluator(eeResult);
		pee.evaluatePerLabel(task.label);
		
		if (evaluateTempEval3) {
			TempEval3 te3 = new TempEval3(evalTmlDirpath, systemTMLPath);
			te3.evaluate();
		}
	}

}
