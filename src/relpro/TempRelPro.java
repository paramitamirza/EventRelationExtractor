package relpro;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import evaluator.PairEvaluator;
import evaluator.TempEval3;
import model.classifier.EventEventCausalClassifier;
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
import parser.TimeMLParser;
import parser.TXPParser.Field;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.Timex;
import server.RemoteServer;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class TempRelPro {
	
	private String name;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	public TempRelPro() throws IOException {
		name = "temprelpro";
		
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
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
//					} else {
//						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
//								tlink.getRelType() + "\tNONE");
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
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) {
			Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
			candidateTlinks = docTml.getTlinks();	 //gold annotated pairs
		
		} else candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
						
						for (FeatureName f : etRelCls.featureList) {
							etfv.addBinaryFeatureToVector(f);
						}
						
						if (train) etfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else etfv.addBinaryFeatureToVector(FeatureName.label);
						
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
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) {
			Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
			candidateTlinks = docTml.getTlinks();	 //gold annotated pairs
		
		} else candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
					
						for (FeatureName f : etRelCls.featureList) {
							etfv.addBinaryFeatureToVector(f);
						}
						
						if (train) etfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else etfv.addBinaryFeatureToVector(FeatureName.label);
						
						fvList.add(etfv);
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
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) {
			Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
			candidateTlinks = docTml.getTlinks();	 //gold annotated pairs
		
		} else candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
					
					for (FeatureName f : eeRelCls.featureList) {
						eefv.addBinaryFeatureToVector(f);
					}
					
					if (train) eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
					else eefv.addBinaryFeatureToVector(FeatureName.label);
					
					fvList.add(eefv);
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventDctTlinksPerText(TXPParser txpParser, String[] lines, 
			PairClassifier etRelCls) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseLines(lines);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
						
						for (FeatureName f : etRelCls.featureList) {
							etfv.addBinaryFeatureToVector(f);
						}
						
						etfv.addBinaryFeatureToVector(FeatureName.label);
						
						fvList.add(etfv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerText(TXPParser txpParser, String[] lines, 
			PairClassifier etRelCls) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseLines(lines);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
					
						for (FeatureName f : etRelCls.featureList) {
							etfv.addBinaryFeatureToVector(f);
						}
						
						etfv.addBinaryFeatureToVector(FeatureName.label);
						
						fvList.add(etfv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinksPerText(TXPParser txpParser, String[] lines, 
			PairClassifier eeRelCls) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseLines(lines);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = docTxp.getTlinks(); //candidate pairs
		
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
					
					for (FeatureName f : eeRelCls.featureList) {
						eefv.addBinaryFeatureToVector(f);
					}
					
					eefv.addBinaryFeatureToVector(FeatureName.label);
					
					fvList.add(eefv);
				}
			}
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
	
	public void trainModel() throws Exception {
		Field[] trainFields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		
		TXPParser txpParserTrain = new TXPParser(EntityEnum.Language.EN, trainFields);
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("temprelpro", "liblinear");
		
		String trainTxpDirpath = "./data/QATempEval-train_TXP/";
		String trainTmlDirpath = "./data/QATempEval-train_TML/";
		
		List<List<PairFeatureVector>> etTrainFvList = getEventTimexSplitTlinks(txpParserTrain, tmlParser, 
				trainTxpDirpath, trainTmlDirpath, dctCls, etCls, true, true);
		List<PairFeatureVector> eeTrainFvList = getEventEventTlinks(txpParserTrain, tmlParser, 
				trainTxpDirpath, trainTmlDirpath, eeCls, true, true);
		
		dctCls.train(etTrainFvList.get(0), "models/" + name + "-dct.model");
		etCls.train(etTrainFvList.get(1), "models/" + name + "-et.model");   
		eeCls.train(eeTrainFvList, "models/" + name + "-ee.model");
	}
	
	public List<List<PairFeatureVector>> buildFeatureVectorFromFile(TXPParser txpParserFile, String filepath) throws Exception {
		
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("temprelpro", "liblinear");
		
		File txpFile = new File(filepath);
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		
		//event-DCT
		List<PairFeatureVector> dctFvList = getEventDctTlinksPerFile(txpParserFile, null, 
				txpFile, null, dctCls, false, false);
		
		//event-timex
		List<PairFeatureVector> etFvList = getEventTimexTlinksPerFile(txpParserFile, null, 
				txpFile, null, etCls, false, false);
		
		//event-event
		List<PairFeatureVector> eeFvList = getEventEventTlinksPerFile(txpParserFile, null, 
				txpFile, null, eeCls, false, false);
		
		fvList.add(dctFvList);
		fvList.add(etFvList);
		fvList.add(eeFvList);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> buildFeatureVectorFromText(TXPParser txpParserFile, String[] lines) throws Exception {
		
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("temprelpro", "liblinear");
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		
		//event-DCT
		List<PairFeatureVector> dctFvList = getEventDctTlinksPerText(txpParserFile, lines, dctCls);
		
		//event-timex
		List<PairFeatureVector> etFvList = getEventTimexTlinksPerText(txpParserFile, lines, etCls);
		
		//event-event
		List<PairFeatureVector> eeFvList = getEventEventTlinksPerText(txpParserFile, lines, eeCls);
		
		fvList.add(dctFvList);
		fvList.add(etFvList);
		fvList.add(eeFvList);
		
		return fvList;
	}
	
	public List<String> testModel(Doc docTxp, List<List<PairFeatureVector>> testFvList) throws Exception {
		List<String> predictions = new ArrayList<String>();
		
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("temprelpro", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("temprelpro", "liblinear");
		
		List<PairFeatureVector> dctFvListCls = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> etFvListCls = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> eeFvListCls = new ArrayList<PairFeatureVector>();
		
		//timex-timex pairs
		Map<String,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		for (String tt : ttlinks.keySet()) {
			TemporalRelation trel = new TemporalRelation(tt.split("\t")[0], tt.split("\t")[1]);
			trel.setRelType(ttlinks.get(tt));
			
			predictions.add(trel.getSourceID() + "\t" + trel.getTargetID() 
					+ "\t"+ trel.getRelType());
		}
		
		//event-dct
		for (PairFeatureVector fv : testFvList.get(0)) {
			//Find label according to rules
			EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
			EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
					docTxp, etfv.getMateDependencyPath());
			
			String label = etRule.getRelType();
			if (!label.equals("O")) {
				predictions.add(etfv.getE1().getID() 
						+ "\t" + etfv.getE2().getID()
						+ "\t" + label);
			} else {
				dctFvListCls.add(fv);
			}
		}
		
		//event-timex
		for (PairFeatureVector fv : testFvList.get(1)) {
			//Find label according to rules
			EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
			EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
					docTxp, etfv.getMateDependencyPath());
			
			String label = etRule.getRelType();
			if (!label.equals("O")) {
				predictions.add(etfv.getE1().getID() 
						+ "\t" + etfv.getE2().getID()
						+ "\t" + label);
			} else {
				etFvListCls.add(fv);
			}
		}
		
		//event-event
		for (PairFeatureVector fv : testFvList.get(2)) {
			//Find label according to rules
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
					docTxp, eefv.getMateDependencyPath());
			 
			String label = eeRule.getRelType();
			if (!label.equals("O")) {
				if (label.equals("IDENTITY")) label = "SIMULTANEOUS";
//						else if (eefv.isCoreference()) label = "IDENTITY";	//--> doesn't work				
				predictions.add(eefv.getE1().getID() 
						+ "\t" + eefv.getE2().getID() 
						+ "\t" + label);
			} else {
				eeFvListCls.add(fv);
			}
		}
		
		//CLASSIFIERS
		
		//event-dct
		List<String> dctClsTest = dctCls.predict(dctFvListCls, "models/" + name + "-dct.model");
		for (int i=0; i<dctFvListCls.size(); i++) {
			EventTimexFeatureVector etfv = new EventTimexFeatureVector(dctFvListCls.get(i));
			String label = dctClsTest.get(i);
			predictions.add(etfv.getE1().getID() 
					+ "\t" + etfv.getE2().getID()
					+ "\t" + label);
		}
		
		//event-timex
		List<String> etClsTest = etCls.predict(etFvListCls, "models/" + name + "-et.model");
		for (int i=0; i<etFvListCls.size(); i++) {
			EventTimexFeatureVector etfv = new EventTimexFeatureVector(etFvListCls.get(i));
			String label = etClsTest.get(i);
			predictions.add(etfv.getE1().getID() 
					+ "\t" + etfv.getE2().getID()
					+ "\t" + label);
		}
		
		//event-event
		List<String> eeClsTest = eeCls.predict(eeFvListCls, "models/" + name + "-ee.model");	
		for (int i=0; i<eeFvListCls.size(); i++) {
			EventEventFeatureVector eefv = new EventEventFeatureVector(eeFvListCls.get(i));
			String label = eeClsTest.get(i);
			predictions.add(eefv.getE1().getID() 
					+ "\t" + eefv.getE2().getID()
					+ "\t" + label);
		}
		
		return predictions;
	}
	
	public static void main(String [] args) throws Exception {
		TempRelPro task = new TempRelPro();
		
		/*****TRAIN****/
//		task.trainModel();
		
		/*****TEST****/
		
		//1. BUILD FEATURE VECTOR FROM TEXT
		String[] sampleText = {
				"Six	t70	4	CRD	six	t71:NMOD	O	O	O	O	O	O	B-NP	O	O	O",
				"of	t71	4	PRF	of	t74:PMOD	O	O	O	O	O	O	B-PP	O	O	O",
				"the	t72	4	AT0	the	O	O	O	O	O	O	O	B-NP	O	O	O",
				"pediatric	t73	4	AJ0	pediatric	O	O	O	O	O	O	O	I-NP	O	O	O",
				"deaths	t74	4	NN2	death	t72:NMOD||t73:NMOD	O	O	O	O	OCCURRENCE	e1000030	I-NP	O	O	e1000030:e8:NONE||e1000030:e9:NONE||e1000030:e10:NONE||e1000030:tmx2:NONE||e1000030:tmx3:NONE",
				"were	t75	4	VVD	be	t70:SBJ||t76:VC||t81:P||t82:COORD	O	O	O	O	O	O	B-VP	O	PAST+NONE+pos	O",
				"reported	t76	4	VVN	report	t77:TMP	O	O	O	O	REPORTING	e8	I-VP	O	PAST+NONE+pos	e8:tmx2:IS_INCLUDED||e8:tmx0:BEFORE||e8:e9:BEFORE||e8:e10:NONE||e8:tmx3:NONE",
				"in	t77	4	PRP	in	t80:PMOD	O	O	O	O	O	O	B-PP	O	O	O",
				"the	t78	4	AT0	the	O	tmx2	B-DATE	2013-W11	O	O	O	B-NP	O	O	O",
				"last	t79	4	AJ0	last	O	tmx2	I-DATE	2013-W11	O	O	O	I-NP	O	O	O",
				"week	t80	4	NN1	week	t78:NMOD||t79:NMOD	tmx2	I-DATE	2013-W11	O	O	O	I-NP	O	O	O",
				",	t81	4	PUN	,	O	O	O	O	O	O	O	O	O	O	O",
				"and	t82	4	CJC	and	t84:CONJ	O	O	O	O	O	O	O	O	O	O",
				"it	t83	4	PNP	it	O	O	O	O	O	O	O	B-NP	O	O	O",
				"'s	t84	4	NN0	have	t83:SBJ||t85:PRD||t87:EXTR	O	O	O	O	O	O	B-VP	O	PRESENT+NONE+pos	O",
				"possible	t85	4	AJ0	possible	O	O	O	O	O	O	O	B-ADJP	O	O	O",
				"there	t86	4	EX0	there	O	O	O	O	O	O	O	B-NP	O	O	O",
				"will	t87	4	VM0	will	t86:SBJ||t88:VC	O	O	O	O	O	O	B-VP	O	FUTURE+NONE+pos	O",
				"be	t88	4	VVB	be	t89:PRD	O	O	O	O	O	O	I-VP	O	FUTURE+NONE+pos	O",
				"more	t89	4	NN0	much	O	O	O	O	O	O	O	B-ADJP	O	O	O",
				",	t90	4	PUN	,	O	O	O	O	O	O	O	O	O	O	O",
				"said	t91	4	VVD	say	t75:OBJ||t90:P||t98:OBJ||t100:P	O	O	O	O	REPORTING	e9	B-VP	mainVb	PAST+NONE+pos	e9:tmx0:IS_INCLUDED||e9:tmx2:NONE||e9:tmx3:NONE",
				"the	t92	4	AT0	the	O	O	O	O	O	O	O	B-NP	O	O	O",
				"CDC	t93	4	NP0	cdc	t92:NMOD||t94:SUFFIX	O	O	O	organization	O	O	I-NP	O	O	O",
				"'s	t94	4	POS	's	O	O	O	O	O	O	O	I-NP	O	O	O",
				"Dr.	t95	4	NP0	dr.	O	O	O	O	O	O	O	I-NP	O	O	O",
				"Michael	t96	4	NP0	michael	O	O	O	O	person	O	O	I-NP	O	O	O",
				"Jhung	t97	4	NP0	jhung	t93:NMOD||t95:NAME||t96:NAME	O	O	O	person	O	O	I-NP	O	O	O",
				"said	t98	4	VVD	say	t97:SBJ||t99:TMP	O	O	O	O	REPORTING	e10	B-VP	O	PAST+NONE+pos	e10:tmx3:IS_INCLUDED||e10:tmx0:IS_INCLUDED||e10:e9:SIMULTANEOUS||e10:tmx2:NONE",
				"Friday	t99	4	NP0	friday	O	tmx3	B-DATE	3/22/2013	O	O	O	B-NP	O	O	O",
				".	t100	4	PUN	.	O	O	O	O	O	O	O	O	O	O	O"
		};
		Field[] fieldsText = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.chunk, Field.main_verb, 
				Field.tense_aspect_pol, Field.tlink};
		TXPParser txpParserText = new TXPParser(EntityEnum.Language.EN, fieldsText);
		List<List<PairFeatureVector>> testFvList = task.buildFeatureVectorFromText(txpParserText, sampleText);
		Doc docTxp = txpParserText.parseLines(sampleText);
		
		//or...
		
		//2. BUILD FEATURE VECTOR FROM FILE
		String filepath = "./data/TempEval3-eval_TXP/AP_20130322.tml.txp";
		Field[] fieldsFile = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		TXPParser txpParserFile = new TXPParser(EntityEnum.Language.EN, fieldsFile);
//		List<List<PairFeatureVector>> testFvList = task.buildFeatureVectorFromFile(txpParserFile, filepath);
//		Doc docTxp = txpParserFile.parseDocument(filepath);
		
		List<String> predictions = task.testModel(docTxp, testFvList);
		
		//RESULT
		for (String tlink : predictions) System.out.println(tlink);
	}

}
