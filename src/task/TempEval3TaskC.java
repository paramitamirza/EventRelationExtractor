package task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import evaluator.PairEvaluator;
import evaluator.TempEval3;
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

class TempEval3TaskC {
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	private List<String> labelList = Arrays.asList(label);
	
	public TempEval3TaskC() {
	}
	
	public Map<String,String> getTimexTimexRuleRelation(Doc doc) {
		Object[] entArr = doc.getEntities().keySet().toArray();
		Map<String,String> ttlinks = new HashMap<String,String>();
		String pair = null;
		for (int i = 0; i < entArr.length; i++) {
			for (int j = i; j < entArr.length; j++) {
				if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
						doc.getEntities().get(entArr[j]) instanceof Timex) {
					TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
							((Timex)doc.getEntities().get(entArr[j])), doc.getDct(), false);
					if (!timextimex.getRelType().equals("O")) {
						pair = ((String) entArr[i]) + "-" + ((String) entArr[j]);
						ttlinks.put(pair, timextimex.getRelType());
					}
				}
			}
		}
		return ttlinks;
	}
	
	public List<String> getTimexTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		List<String> tt = new ArrayList<String>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<String,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")	//classifying the relation task
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					String st = tlink.getSourceID() + "-" + tlink.getTargetID();
					String ts = tlink.getTargetID() + "-" + tlink.getSourceID();
					if (ttlinks.containsKey(st)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else if (ttlinks.containsKey(ts)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
					} else {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\tNONE");
					}
				}
			}
		}
		return tt;
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					
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
//						//add only if with DCT/EmptyTag or in the same sentence --> doesn't improve
//						if (((Timex) etfv.getE2()).isDct() || ((Timex) etfv.getE2()).isEmptyTag()
//								|| ((Event) etfv.getE1()).getSentID().equals(((Timex) etfv.getE2()).getSentID())) {
//							fvList.add(etfv);
//						} 
						
						//add all
						fvList.add(etfv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier eeRelCls,
			boolean train) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
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
					
					if (train && !fv.getVectors().get(fv.getVectors().size()-1).equals("0")
							&& !fv.getVectors().get(fv.getVectors().size()-1).equals("NONE")) {
						fvList.add(eefv);
					} else if (!train){ //test, add all
						fvList.add(eefv);
					}
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
		
		int linkId = 1;
		TemporalRelation tlink = new TemporalRelation();
		for (String ttStr : ttResult) {
			if (!ttStr.isEmpty()) {
				String[] cols = ttStr.split("\t");
				tlink.setSourceID(cols[0].replace("tmx", "t"));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Timex");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String etStr : etResult) {
			if (!etStr.isEmpty()) {
				String[] cols = etStr.split("\t");
				tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String eeStr : eeResult) {
			if (!eeStr.isEmpty()) {
				String[] cols = eeStr.split("\t");
				tlink.setSourceID(dTml.getInstancesInv().get(cols[0]));
				tlink.setTargetID(dTml.getInstancesInv().get(cols[1]));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Event");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		
		File sysTmlPath = new File(systemTMLPath + "/" + tmlFile.getName());
		PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
		sysTML.write(tml.toString());
		sysTML.close();
	}
	
	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		
		TempEval3TaskC task = new TempEval3TaskC();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
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
		
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("te3", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("te3", "liblinear");
		
		Map<String, Map<String, String>> clinkPerFile = new HashMap<String, Map<String, String>>();
		
		List<String> ttResult = new ArrayList<String>();
		List<String> etResult = new ArrayList<String>();
		List<String> eeResult = new ArrayList<String>();
		
		File[] txpFiles = new File(evalTxpDirpath).listFiles();
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				File tmlFile = new File(evalTmlDirpath, txpFile.getName().replace(".txp", ""));
//				System.err.println(tmlFile.getName());
				Doc docTxp = txpParser.parseDocument(txpFile.getPath());
				
				//timex-timex pairs
				List<String> ttPerFile = task.getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile);
				ttResult.addAll(ttPerFile);
				
				//event-timex pairs
				List<PairFeatureVector> etFvList = task.getEventTimexTlinksPerFile(txpParser, tmlParser, 
						txpFile, tmlFile, etCls, false);
				List<String> etClsTest = etCls.predict(etFvList, "et-model");
				
				List<String> etTestList = new ArrayList<String>();
				for (int i=0; i<etFvList.size(); i++) {
					//Find label according to rules
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(etFvList.get(i));
					EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
							docTxp, etfv.getMateDependencyPath());
					
					//Prefer labels from rules than classifier 
					String label;
//					if (!etRule.getRelType().equals("O")) {
//						label = etRule.getRelType();
//					} else {
						label = etClsTest.get(i);
//					}
					
					etTestList.add(etfv.getE1().getID() 
							+ "\t" + etfv.getE2().getID()
							+ "\t" + etfv.getLabel()
							+ "\t" + label);
				}
				
				etResult.addAll(etTestList);
				
				//event-event pairs
				List<PairFeatureVector> eeFvList = task.getEventEventTlinksPerFile(txpParser, tmlParser, 
							txpFile, tmlFile, eeCls, false);
				List<String> eeClsTest = eeCls.predict(eeFvList, "ee-model");
				
				List<String> eeTestList = new ArrayList<String>();
				for (int i=0; i<eeFvList.size(); i++) {
					//Find label according to rules
					EventEventFeatureVector eefv = new EventEventFeatureVector(eeFvList.get(i));
					EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
							docTxp, eefv.getMateDependencyPath());
					
					//Prefer labels from rules than classifier 
					String label;
					if (!eeRule.getRelType().equals("O")) label = eeRule.getRelType();
//					else if (eefv.isCoreference()) label = "IDENTITY";	//--> doesn't work
					else label = eeClsTest.get(i);
					
					eeTestList.add(eefv.getE1().getID() 
							+ "\t" + eefv.getE2().getID() 
							+ "\t" + eefv.getLabel()
							+ "\t" + label);
				}
				
				eeResult.addAll(eeTestList);
				
				//Write the TimeML document with new TLINKs
				task.writeTimeMLFile(tmlParser, tmlFile, 
						ttPerFile, etTestList, eeTestList,
						systemTMLPath);
			}
		}
		
		PairEvaluator ptt = new PairEvaluator(ttResult);
		ptt.evaluatePerLabel(task.label);
		PairEvaluator pet = new PairEvaluator(etResult);
		pet.evaluatePerLabel(task.label);
		PairEvaluator pee = new PairEvaluator(eeResult);
		pee.evaluatePerLabel(task.label);
		
		TempEval3 te3 = new TempEval3(evalTmlDirpath, systemTMLPath);
		te3.evaluate();	
	}

}
