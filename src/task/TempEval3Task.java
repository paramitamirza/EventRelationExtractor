package task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import evaluator.PairEvaluator;
import evaluator.TempEval3;
import model.classifier.EventEventRelationClassifier;
import model.classifier.EventTimexRelationClassifier;
import model.classifier.PairClassifier;
import model.classifier.PairClassifier.VectorClassifier;
import model.classifier.TestEventTimexRelationClassifierTempEval3;
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
import libsvm.*;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibLINEAR;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

class TempEval3Task {
	
	public TempEval3Task() {
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train,
			StringBuilder etRuleRel) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> labelList = Arrays.asList(label);
				
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					
					//Rules
					EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
							docTxp, etfv.getMateDependencyPath());
					if (etRule.equals("O")) etRuleRel.append("0" + "\n");
					else etRuleRel.append((labelList.indexOf(etRule.getRelType())+1) + "\n");

					if (etRelCls.classifier.equals(VectorClassifier.yamcha)) {
						etfv.addToVector(FeatureName.id);
					}
					
					//Add features to feature vector
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
						etfv.addBinaryFeatureToVector(FeatureName.label);
					} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
							etRelCls.classifier.equals(VectorClassifier.weka) ||
							etRelCls.classifier.equals(VectorClassifier.none)){
						etfv.addToVector(FeatureName.label);
					}
						
					if (train && !etfv.getLabel().equals("0")
							&& !etfv.getLabel().equals("NONE")) {
						fvList.add(etfv);
					} else if (!train){ //test, add all
						fvList.add(etfv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier etRelCls,
			boolean train,
			StringBuilder etRuleRel) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventTimexTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train, etRuleRel));
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier eeRelCls,
			boolean train,
			StringBuilder eeRuleRel) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> labelList = Arrays.asList(label);
				
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
					
					//Rules
					EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
							docTxp, eefv.getMateDependencyPath());
					if (eeRule.getRelType().equals("O")) eeRuleRel.append("0" + "\n");
					else eeRuleRel.append((labelList.indexOf(eeRule.getRelType())+1) + "\n");
					
					if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
						eefv.addToVector(FeatureName.id);
					}
					
					//Add features to feature vector
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
						eefv.addBinaryFeatureToVector(FeatureName.label);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						eefv.addToVector(FeatureName.label);
					}
						
					if (train && !eefv.getLabel().equals("0")
							&& !eefv.getLabel().equals("NONE")) {
						fvList.add(eefv);
					} else if (!train){ //test, add all
						fvList.add(eefv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier eeRelCls,
			boolean train,
			StringBuilder eeRuleRel) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventEventTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, eeRelCls, train, eeRuleRel));
		}
		return fvList;
	}
	
	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		
		TempEval3Task task = new TempEval3Task();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String trainTxpDirpath = "./data/TempEval3-train_TXP2/";
		String trainTmlDirpath = "./data/TempEval3-train_TML/";
		
		String evalTxpDirpath = "./data/TempEval3-eval_TXP/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
//		PairClassifier etCls = new EventTimexRelationClassifier("te3", "liblinear");
//		StringBuilder etRuleTrain = new StringBuilder();
//		List<PairFeatureVector> etTrainFvList = task.getEventTimexTlinks(txpParser, tmlParser, 
//				trainTxpDirpath, trainTmlDirpath, etCls, true, etRuleTrain);
//		StringBuilder etRuleTest = new StringBuilder();
//		List<PairFeatureVector> etEvalFvList = task.getEventTimexTlinks(txpParser, tmlParser, 
//				evalTxpDirpath, evalTmlDirpath, etCls, false, etRuleTest);
//		etCls.train(etTrainFvList);  
//		
//		String etClsTest = etCls.test(etEvalFvList);
//		etRuleTest.append(etClsTest);
//		List<String> etTestList = new ArrayList<String>();
//		for (String s : etRuleTest.toString().split("\\r?\\n")) {
//			etTestList.add(s);
//		}
//		
//		PairEvaluator pet = new PairEvaluator(etTestList);
//		pet.evaluatePerLabelIdx();
		
		PairClassifier eeCls = new EventEventRelationClassifier("te3", "liblinear"
				,"convprob", "4", "data/probs/indexes-ep300-event-eval-glove-100d.gr4.exp5.csv"
				);
		
		StringBuilder eeRuleTrain = new StringBuilder();
		List<PairFeatureVector> eeTrainFvList = task.getEventEventTlinks(txpParser, tmlParser, 
				trainTxpDirpath, trainTmlDirpath, eeCls, true, eeRuleTrain);
		StringBuilder eeRuleTest = new StringBuilder();
		eeCls.train(eeTrainFvList); 
		
		List<PairFeatureVector> eeEvalFvList = task.getEventEventTlinks(txpParser, tmlParser, 
				evalTxpDirpath, evalTmlDirpath, eeCls, false, eeRuleTest);
		String eeClsTest = eeCls.test(eeEvalFvList);
		String[] eeRuleTestList = eeRuleTest.toString().trim().split("\\r?\\n");
		String[] eeClsTestList = eeClsTest.trim().split("\\r?\\n");
		List<String> eeTestList = new ArrayList<String>();
		
		for (int i=0; i<eeClsTestList.length; i++) {
			if (!eeRuleTestList[i].equals("0")) {
				String[] cols = eeClsTestList[i].split("\t");
				eeTestList.add(cols[0] + "\t" + eeRuleTestList[i]);
			} else {
				eeTestList.add(eeClsTestList[i]);
			}
		}
		
		PairEvaluator pee = new PairEvaluator(eeTestList);
		pee.evaluatePerLabelIdx();
	}

}
