package model.classifier;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import evaluator.PairEvaluator;
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
import parser.TXPParser;
import parser.TimeMLParser;
import parser.TXPParser.Field;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.Sentence;
import parser.entities.TemporalRelation;
import parser.entities.Timex;
import simplifier.SentenceSimplifier;

public class TestEventTimexRelationClassifierTempEval3 {
	
	public TestEventTimexRelationClassifierTempEval3() {
		
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
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
					fv = new EventTimexFeatureVector(fv);
					
					if (etRelCls.classifier.equals(VectorClassifier.yamcha)) {
						fv.addToVector(FeatureName.id);
					}
					
					//Add features to feature vector
					for (FeatureName f : etRelCls.featureList) {
						if (etRelCls.classifier.equals(VectorClassifier.libsvm) ||
								etRelCls.classifier.equals(VectorClassifier.liblinear) ||
								etRelCls.classifier.equals(VectorClassifier.weka)) {
							fv.addBinaryFeatureToVector(f);
						} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
								etRelCls.classifier.equals(VectorClassifier.none)) {
							fv.addToVector(f);
						}
					}
					
					if (etRelCls.classifier.equals(VectorClassifier.libsvm) || 
							etRelCls.classifier.equals(VectorClassifier.liblinear)) {
						fv.addBinaryFeatureToVector(FeatureName.label);
					} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
							etRelCls.classifier.equals(VectorClassifier.weka) ||
							etRelCls.classifier.equals(VectorClassifier.none)){
						fv.addToVector(FeatureName.label);
					}
						
					if (train && !fv.getVectors().get(fv.getVectors().size()-1).equals("0")
							&& !fv.getVectors().get(fv.getVectors().size()-1).equals("NONE")) {
						fvList.add(fv);
					} else if (!train){ //test, add all
						fvList.add(fv);
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier etRelCls,
			boolean train) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventTimexTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train));
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
		
		TestEventTimexRelationClassifierTempEval3 test = new TestEventTimexRelationClassifierTempEval3();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String trainTxpDirpath = "./data/TempEval3-train_TXP2/";
		String trainTmlDirpath = "./data/TempEval3-train_TML/";
		
		String evalTxpDirpath = "./data/TempEval3-eval_TXP/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
		EventTimexRelationClassifier etCls = new EventTimexRelationClassifier("te3", "liblinear");
		
		List<PairFeatureVector> trainFvList = test.getEventTimexTlinks(txpParser, tmlParser, 
				trainTxpDirpath, trainTmlDirpath, etCls, true);
		List<PairFeatureVector> evalFvList = test.getEventTimexTlinks(txpParser, tmlParser, 
				evalTxpDirpath, evalTmlDirpath, etCls, false);
		
		etCls.train(trainFvList, "et-model");   
		etCls.evaluate(evalFvList, "et-model");
		
		File[] txpFiles = new File(evalTxpDirpath).listFiles();
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				File tmlFile = new File(evalTmlDirpath, txpFile.getName().replace(".txp", ""));
//				System.err.println(tmlFile.getName());
				Doc docTxp = txpParser.parseDocument(txpFile.getPath());
				
				//Predict labels
				List<PairFeatureVector> etFvList = test.getEventTimexTlinksPerFile(txpParser, tmlParser, 
							txpFile, tmlFile, etCls, false);
				List<String> etClsTest = etCls.predict(etFvList, "et-model");
				
				for (int i=0; i<etFvList.size(); i++) {
					//Find label according to rules
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(etFvList.get(i));
					EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
							docTxp, etfv.getMateDependencyPath());
					
					//Prefer labels from rules than classifier 
					String label;
					if (!etRule.getRelType().equals("O")) label = etRule.getRelType();
					else label = etClsTest.get(i);
					
					System.out.println(etfv.getE1().getID() 
							+ "\t" + etfv.getE2().getID()
//							+ "\t" + etfv.getLabel()
							+ "\t" + label);
				}
			}
		}
	}
}
