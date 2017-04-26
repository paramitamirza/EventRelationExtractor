package embedding.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
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
import task.SortMapByValue;

public class TestEventEventRelationClassifier {

	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	private List<String> labelList = Arrays.asList(label);
	
	public TestEventEventRelationClassifier() {
		
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
						if (eeRelCls.classifier.equals(VectorClassifier.libsvm)
								|| eeRelCls.classifier.equals(VectorClassifier.liblinear)
								|| eeRelCls.classifier.equals(VectorClassifier.logit)
								|| eeRelCls.classifier.equals(VectorClassifier.weka)) {
							eefv.addBinaryFeatureToVector(f);
						} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha)
								|| eeRelCls.classifier.equals(VectorClassifier.none)) {
							eefv.addToVector(f);
						}
					}
					
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm)
							|| eeRelCls.classifier.equals(VectorClassifier.liblinear)
							|| eeRelCls.classifier.equals(VectorClassifier.logit)) {
						if (train) eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else eefv.addBinaryFeatureToVector(FeatureName.label);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha)
							|| eeRelCls.classifier.equals(VectorClassifier.weka)
							|| eeRelCls.classifier.equals(VectorClassifier.none)){
						if (train) eefv.addToVector(FeatureName.labelCollapsed);
						else eefv.addToVector(FeatureName.label);
					}
					
					fvList.add(eefv);
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier etRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventEventTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train, goldCandidate));
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinks(String embeddingFilePath, String labelFilePath) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		System.setProperty("line.separator", "\n");
		BufferedReader bremb = new BufferedReader(new FileReader(embeddingFilePath));
		BufferedReader brlbl = new BufferedReader(new FileReader(labelFilePath));
		String line, lbl;
		int numCols;
		while ((line = bremb.readLine()) != null) {
			lbl = brlbl.readLine();
	    	numCols = line.split(",").length;
	    	PairFeatureVector fv = new PairFeatureVector(null, null, null, lbl, numCols+1, 
	    			tsignalList, csignalList);
	    	int i=0;
	    	for (String s : line.split(",")) {
	    		fv.getVectors().add(s);
	    		fv.getFeatures()[i] = Double.parseDouble(s);
	    		i++;
	    	}
	    	fv.getVectors().add(String.valueOf(labelList.indexOf(lbl)+1));
	    	fv.getFeatures()[i] = (double) labelList.indexOf(lbl)+1;
	    	
	    	fvList.add(fv);
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
		
		TestEventEventRelationClassifier test = new TestEventEventRelationClassifier();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String trainTxpDirpath = "./data/TempEval3-train_TXP2/";
		String trainTmlDirpath = "./data/TempEval3-train_TML/";
		
		String evalTxpDirpath = "./data/TempEval3-eval_TXP/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
		boolean goldCandidate = true;
		boolean deduced = false;
		int combine = 0;	//0: concat, 1: w1-w2, 2: w2-w1, 3: plus, 4: dot, 5: avg
		
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("te3", "logit");
		
//		List<PairFeatureVector> trainFvList = test.getEventEventTlinks(txpParser, tmlParser, 
//				trainTxpDirpath, trainTmlDirpath, eeCls, true, goldCandidate);
//		List<PairFeatureVector> evalFvList = test.getEventEventTlinks(txpParser, tmlParser, 
//				evalTxpDirpath, evalTmlDirpath, eeCls, false, goldCandidate);
		
		List<PairFeatureVector> trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp0.csv", 
				"./data/embedding/te3-ee-train-labels-str.gr1.csv");
		if (deduced) {
			switch (combine) {
				case 0: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp0.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
				case 1: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp1.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
				case 2: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp2.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
				case 3: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp3.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
				case 4: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp4.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
				case 5: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-deduced-embedding-word2vec-300.exp5.csv", 
								"./data/embedding/te3-ee-train-deduced-labels-str.gr1.csv");
					break;
			}
			
		} else {
			switch (combine) {
				case 0: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp0.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
				case 1: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp1.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
				case 2: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp2.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
				case 3: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp3.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
				case 4: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp4.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
				case 5: 
					trainFvList = test.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp5.csv", 
							"./data/embedding/te3-ee-train-labels-str.gr1.csv");
					break;
			}
		}
		
		List<PairFeatureVector> evalFvList = 
				test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp0.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
		switch (combine) {
			case 0: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp0-features.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
			case 1: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp1.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
			case 2: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp2.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
			case 3: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp3.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
			case 4: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp4.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
			case 5: 
				evalFvList = test.getEventEventTlinks("./data/embedding/te3-ee-eval-embedding-word2vec-300.exp5.csv", 
						"./data/embedding/te3-ee-eval-labels-str.gr1.csv");
				break;
		}
		
		eeCls.train(trainFvList, "./models/test-ee.model");
//		eeCls.train2(trainFvList, "./models/test-ee.model");   
//		eeCls.evaluate(evalFvList, "./models/test-ee.model");
		
		List<String> eeTestList = new ArrayList<String>();
		List<String> eeClsTest = eeCls.predict(evalFvList, "models/test-ee.model");
//		List<String> eeClsTest = eeCls.predict2(evalFvList, "models/test-ee.model", test.label);
		for (int i=0; i<evalFvList.size(); i++) {
			if (evalFvList.get(i).getE1() == null && evalFvList.get(i).getE2() == null) {
				eeTestList.add("-" 
						+ "\t" + "-"
						+ "\t" + evalFvList.get(i).getLabel()
						+ "\t" + eeClsTest.get(i));
//				System.out.println("-" 
//						+ "\t" + "-"
//						+ "\t" + evalFvList.get(i).getLabel()
//						+ "\t" + eeClsTest.get(i));
			} else {
				EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
				eeTestList.add(eefv.getE1().getID() 
						+ "\t" + eefv.getE2().getID()
						+ "\t" + eefv.getLabel()
						+ "\t" + eeClsTest.get(i));
			}
			
		}
		
		//Evaluate
		PairEvaluator pee = new PairEvaluator(eeTestList);
		pee.evaluatePerLabel(test.label);
		
//		List<String> eeTestList = new ArrayList<String>();
//		
//		File[] txpFiles = new File(evalTxpDirpath).listFiles();
//		//For each file in the evaluation dataset
//		for (File txpFile : txpFiles) {
//			if (txpFile.isFile()) {	
//				File tmlFile = new File(evalTmlDirpath, txpFile.getName().replace(".txp", ""));
////				System.err.println(tmlFile.getName());
//				Doc docTxp = txpParser.parseDocument(txpFile.getPath());
//				
//				//Predict labels
//				List<PairFeatureVector> eeFvList = test.getEventEventTlinksPerFile(txpParser, tmlParser, 
//							txpFile, tmlFile, eeCls, false, goldCandidate);
//				List<String> eeClsTest = eeCls.predict(eeFvList, "./models/test-ee.model");
//				
//				for (int i=0; i<eeFvList.size(); i++) {
//					//Find label according to rules
//					EventEventFeatureVector eefv = new EventEventFeatureVector(eeFvList.get(i));
//					EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
//							docTxp, eefv.getMateDependencyPath());
//					
//					//Prefer labels from rules than classifier 
//					String label = eeClsTest.get(i);
////					if (!eeRule.getRelType().equals("O")) label = eeRule.getRelType();
//					
//					System.out.println(txpFile.getName()
//							+ "\t" + eefv.getE1().getID() 
//							+ "\t" + eefv.getE2().getID() 
//							+ "\t" + eefv.getLabel()
//							+ "\t" + label);
//					
//					eeTestList.add(eefv.getE1().getID() 
//							+ "\t" + eefv.getE2().getID()
//							+ "\t" + eefv.getLabel()
//							+ "\t" + eeClsTest.get(i));
//				}
//			}
//		}
//		
//		//Evaluate
//		PairEvaluator pee = new PairEvaluator(eeTestList);
//		pee.evaluatePerLabel(test.label);
	}
}
