package task;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import evaluator.PairEvaluator;
import libsvm.svm_model;
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
import model.feature.Marker;
import model.rule.EventEventRelationRule;
import model.rule.TimexTimexRelationRule;
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
import server.RemoteServer;
import parser.entities.CausalRelation;

public class CausalTimeBankTaskExperiments {
	
	private ArrayList<String> features;
	private String name;
	private String TXPPath;
	private String CATPath;
	private String systemCATPath;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	public CausalTimeBankTaskExperiments() throws IOException {
		name = "causal";
		TXPPath = "data/Causal-TimeBank_TXP";
		CATPath = "data/Causal-TimeBank_CAT";
		
		//TimeML directory for system result files
		systemCATPath = "data/Causal-TimeBank-system_CAT";
		File sysDir = new File(systemCATPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		features = new ArrayList<String>();
	}
	
	public Map<String,String> getCLINKs(Doc doc) {
		Map<String,String> clinks = new HashMap<String,String>();
		String pair = null, pairInv = null;
		for (CausalRelation clink : doc.getClinks()) {
			pair = clink.getSourceID() + "-" + clink.getTargetID();
			pairInv = clink.getTargetID()+ "-" + clink.getSourceID();
			clinks.put(pair, "CLINK");
			clinks.put(pairInv, "CLINK-R");
		}		
		return clinks;
	}
	
	public Boolean isContainCausalSignal(Sentence sent, Doc doc) {
		Map<String, String> signalList = csignalList.getList();
		Map<String, String> patternList = csignalList.getPatternList();
		
//		Object[] sigKeys = signalList.keySet().toArray();
//		Arrays.sort(sigKeys, Collections.reverseOrder());
		
		String str = " " + sent.toLowerString(doc) + " ";
		for (String key : signalList.keySet()) {
//		for (Object key : sigKeys) {
			Pattern pattern = Pattern.compile(" " + patternList.get(key) + " ");
			Matcher matcher = pattern.matcher(str);
			if (matcher.find()) {
				return true;
			}
		}
		
		return false;
	}
	
	public Boolean isContainCausalVerb(Sentence sent, Doc doc) {
		Map<String, String> verbList = csignalList.getVerbList();
		
		Object[] sigKeys = verbList.keySet().toArray();
		Arrays.sort(sigKeys, Collections.reverseOrder());
		
		String str = " " + sent.toLemmaString(doc) + " ";
		for (Object key : sigKeys) {
			Pattern pattern = Pattern.compile(" " + (String)key + " ");
			Matcher matcher = pattern.matcher(str);
			if (matcher.find()) {
				return true;
			}
		}
		
		return false;
	}
	
	public Map<String,String> getCandidatePairs(Doc doc) {
		Map<String,String> candidates = new HashMap<String,String>();
		Map<String,String> clinks = getCLINKs(doc);
		
		int numClink = 0;
		
		for (int s=0; s<doc.getSentenceArr().size(); s++) {
			Sentence s1 = doc.getSentences().get(doc.getSentenceArr().get(s));
			
			Entity e1, e2;
			String pair = null;
			for (int i = 0; i < s1.getEntityArr().size(); i++) {
				e1 = doc.getEntities().get(s1.getEntityArr().get(i));
				
				//candidate pairs within the same sentence
				
				if (isContainCausalSignal(s1, doc) || isContainCausalVerb(s1, doc)) {
				
					if (i < s1.getEntityArr().size()-1) {
						for (int j = i+1; j < s1.getEntityArr().size(); j++) {
							e2 = doc.getEntities().get(s1.getEntityArr().get(j));
							if (e1 instanceof Event && e2 instanceof Event) {
								pair = e1.getID() + "-" + e2.getID();
								if (clinks.containsKey(pair)) {
									numClink ++;
									candidates.put(pair, clinks.get(pair));
								} else {
									candidates.put(pair, "NONE");
								}
							}
						}
					}
				
				}
				
				//candidate pairs in consecutive sentences
				if (s < doc.getSentenceArr().size()-1) {
//					if (doc.getTokens().get(e1.getStartTokID()).isMainVerb()) {
						Sentence s2 = doc.getSentences().get(doc.getSentenceArr().get(s+1));
						if (isContainCausalSignal(s2, doc)) {
						
							for (int j = 0; j < s2.getEntityArr().size(); j++) {
								e2 = doc.getEntities().get(s2.getEntityArr().get(j));
								if (e1 instanceof Event && e2 instanceof Event) {
									pair = e1.getID() + "-" + e2.getID();
									if (clinks.containsKey(pair)) {
										numClink ++;
										candidates.put(pair, clinks.get(pair));
									} else {
										candidates.put(pair, "NONE");
									}
								}
							}
						}
//					}
				}
			}
		}
		
		return candidates;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinksPerFile(TXPParser txpParser, 
			File txpFile, PairClassifier eeRelCls,
			boolean train, double threshold) throws Exception {
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListRule = new ArrayList<PairFeatureVector>();
		
		List<PairFeatureVector> fvListTClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTRule = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Map<String,String> candidates = getCandidatePairs(docTxp);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		String[] tlinksArr = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> tlinkTypes = Arrays.asList(tlinksArr);
		
//		System.err.println(docTxp.getFilename());
	    
		for (String clink : candidates.keySet()) {	//for every CLINK in TXP file: candidate pairs
			Entity e1 = docTxp.getEntities().get(clink.split("-")[0]);
			Entity e2 = docTxp.getEntities().get(clink.split("-")[1]);
			PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			
			String rule = EventEventRelationRule.getEventCausalityRule(eefv);
			if (!rule.equals("O") && !rule.equals("NONE")) {
				if (rule.contains("-R")) {
					eefv.setPredLabel("CLINK-R");
				}
				else {
					eefv.setPredLabel("CLINK");
				}
				fvListRule.add(eefv);
				
			} else if (rule.equals("O") 
					|| rule.equals("NONE")
					) {
			
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
				
				String tlinkType = "O";
				if (docTxp.getTlinkTypes().containsKey(e1.getID()+","+e2.getID())) {
					tlinkType = docTxp.getTlinkTypes().get(e1.getID()+","+e2.getID());
				} else if (docTxp.getTlinkTypes().containsKey(e2.getID()+","+e1.getID())) {
					tlinkType = TemporalRelation.getInverseRelation(docTxp.getTlinkTypes().get(e2.getID()+","+e1.getID()));
				} 	
				if (tlinkType.equals("O")) tlinkType = "NONE";
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
						eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
						eeRelCls.classifier.equals(VectorClassifier.weka)) {
					eefv.addBinaryFeatureToVector("tlink", tlinkType, tlinkTypes);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.none)) {
					eefv.addToVector("tlink", tlinkType);
				}
				
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
						eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
					eefv.addBinaryFeatureToVector(FeatureName.labelCaus);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.weka) ||
						eeRelCls.classifier.equals(VectorClassifier.none)){
					eefv.addToVector(FeatureName.label);
				}
				
				Marker m = fv.getCausalSignal();
				if (!m.getDepRelE1().equals("O") || !m.getDepRelE2().equals("O")) {
//					System.out.println("--"+m.getText());
					if (eefv.getLabel().equals("NONE")) {
						fvListNone.add(eefv);
					} else if (eefv.getLabel().equals("CLINK")) {
						fvListClink.add(eefv);
					} else if (eefv.getLabel().equals("CLINK-R")) {
						fvListClinkR.add(eefv);
					}
				}
			}
		}
		
		fvList.add(fvListNone);
		fvList.add(fvListClink);
		fvList.add(fvListClinkR);
		fvList.add(fvListRule);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls,
			boolean train, int numFold, double threshold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		for(int i=0; i<numFold; i++) fvList.add(new ArrayList<PairFeatureVector>());
		
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCrule = new ArrayList<PairFeatureVector>();
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, eeRelCls, train, threshold);
			
			fvListNone.addAll(fvListList.get(0));
			fvListClink.addAll(fvListList.get(1));
			fvListClinkR.addAll(fvListList.get(2));
			fvListCrule.addAll(fvListList.get(3));
		}
		
		int numNonePerFold = (int)Math.floor(fvListNone.size()/((double)numFold));
		int numClinkPerFold = (int)Math.floor(fvListClink.size()/((double)numFold));
		int numClinkRPerFold = (int)Math.floor(fvListClinkR.size()/((double)numFold));
		int numClinkRulePerFold = (int)Math.floor(fvListCrule.size()/((double)numFold));
		
		System.out.println("NONE: " + fvListNone.size() + ", CLINK: " + fvListClink.size() + ", CLINK-R: " + fvListClinkR.size() + ", CLINK (Rule): " + fvListCrule.size());
		
		Collections.shuffle(fvListNone);
		Collections.shuffle(fvListClink);
		Collections.shuffle(fvListClinkR);
		
		int idxNone = 0, idxClink = 0, idxClinkR = 0, idxCrule = 0;
		for (int i=0; i<numFold; i++) {
			for (int j=0; j<numNonePerFold; j++) {
				fvList.get(i).add(fvListNone.get(idxNone));
				idxNone ++;
			}
			for (int j=0; j<numClinkPerFold; j++) {
				fvList.get(i).add(fvListClink.get(idxClink));
				idxClink ++;
			}
			for (int j=0; j<numClinkRPerFold; j++) {
				fvList.get(i).add(fvListClinkR.get(idxClinkR));
				idxClinkR ++;
			}
			for (int j=0; j<numClinkRulePerFold; j++) {
				fvList.get(i).add(fvListCrule.get(idxCrule));
				idxCrule ++;
			}
		}
		for (int i=0; i<numFold; i++) {
			if (idxNone < fvListNone.size()) {
				fvList.get(i).add(fvListNone.get(idxNone));
				idxNone ++;
			}
			if (idxClink < fvListClink.size()) {
				fvList.get(i).add(fvListClink.get(idxClink));
				idxClink ++;
			}
			if (idxClinkR < fvListClinkR.size()) {
				fvList.get(i).add(fvListClinkR.get(idxClinkR));
				idxClinkR ++;
			}
			if (idxCrule < fvListCrule.size()) {
				fvList.get(i).add(fvListCrule.get(idxCrule));
				idxCrule ++;
			}
		}
		
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls,
			boolean train, double threshold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCrule = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, eeRelCls, train, threshold);
			
			fvListNone.addAll(fvListList.get(0));
			fvListClink.addAll(fvListList.get(1));
			fvListClinkR.addAll(fvListList.get(2));
			fvListCrule.addAll(fvListList.get(3));
		}
		
		System.out.println("NONE: " + fvListNone.size() + ", CLINK: " + fvListClink.size() + ", CLINK-R: " + fvListClinkR.size() + ", CLINK (Rule): " + fvListCrule.size());
		
		fvListClink.addAll(fvListNone);
		fvListClink.addAll(fvListClinkR);
		fvListClink.addAll(fvListCrule);
		
		return fvListClink;
	}
	
	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.supersense, Field.ss_ner, Field.clink, Field.csignal};
		
//		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
//				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
//				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
//				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
//				Field.main_verb, Field.connective, Field.morpho, 
//				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
//				Field.clink, Field.csignal};
		
		CausalTimeBankTaskExperiments task = new CausalTimeBankTaskExperiments();
		
		PrintStream out = new PrintStream(new FileOutputStream("causality_output.txt"));
		System.setOut(out);
		PrintStream log = new PrintStream(new FileOutputStream("causality_log.txt"));
		System.setErr(log);
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);
		
		String txpDirpath = "./data/Causal-TimeBank_TXP2/";
		
		String[] aquaintPath = {"./data/AQUAINT_TXP/1/", "./data/AQUAINT_TXP/2/", "./data/AQUAINT_TXP/3/", 
				"./data/AQUAINT_TXP/4/", "./data/AQUAINT_TXP/5/", "./data/AQUAINT_TXP/6/", "./data/AQUAINT_TXP/7/"};
		String aquaintAllPath = "./data/AQUAINT_TXP/all/";
		
		//Init classifiers
		PairClassifier eeCls = new EventEventCausalClassifier("causal", "yamcha");
//		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causal", "liblinear");
		
		boolean labelProbs = false;
		int numFold = 10;
		
		int threshold = 1000;
//		double[] thresholds = {0, 10, 20, 30};
		String[] label = {"CLINK", "CLINK-R", "NONE"};
		List<String> labelList = Arrays.asList(label);
		
//		for (double threshold : thresholds) {
			
//			System.err.println("Threshold " + (threshold) + "...");
			
			//Train models 
			System.err.println("---------------Causal-TimeBank---------------");
			List<List<PairFeatureVector>> fvListList = 
					task.getEventEventClinks(txpParser, txpDirpath, 
							eeCls, true, numFold, threshold);
			
//			System.err.println("---------------AQUAINT---------------");
			List<PairFeatureVector> eeAquaint = task.getEventEventClinks(txpParser, aquaintAllPath, eeCls, false, 0);
			List<PairFeatureVector> evalAquaint = new ArrayList<PairFeatureVector>();
			
			for (int fold=0; fold<numFold; fold++) {
				
				System.err.println("Fold " + (fold+1) + "...");
				
				List<String> eeTestList = new ArrayList<String>();
				List<String> eeTestListSelfTrain = new ArrayList<String>();
								
				List<PairFeatureVector> evalFvList = new ArrayList<PairFeatureVector>();
				
				for (PairFeatureVector fv : fvListList.get(fold)) {
					if (fv.getPredLabel() != null) {
						eeTestList.add((labelList.indexOf(fv.getLabel())+1)
								+ "\t" + (labelList.indexOf(fv.getPredLabel())+1));
						eeTestListSelfTrain.add((labelList.indexOf(fv.getLabel())+1)
								+ "\t" + (labelList.indexOf(fv.getPredLabel())+1));
					} else {
						evalFvList.add(fv);
					}
				}
				
				List<PairFeatureVector> trainFvList = new ArrayList<PairFeatureVector>();
				for (int n=0; n<numFold; n++) {
					if (n != fold) {
						for (PairFeatureVector fv : fvListList.get(n)) {
							if (fv.getPredLabel() == null) {
								trainFvList.add(fv);
							}
						}
					}
				}
				
				BufferedWriter bwTrain = new BufferedWriter(new FileWriter("./data/train-ee-fold"+(fold+1)+".data"));
				bwTrain.write(eeCls.printFeatureVector(trainFvList));
				BufferedWriter bwEval = new BufferedWriter(new FileWriter("./data/eval-ee-fold"+(fold+1)+".data"));
				bwEval.write(eeCls.printFeatureVector(evalFvList));
				
				svm_model model = new svm_model();
				
				if (eeCls.classifier.equals(VectorClassifier.yamcha)) {
					eeCls.train(trainFvList, labelProbs);
					
					String eeClsTest = eeCls.test(evalFvList, labelProbs, label);
					eeTestList.addAll(Arrays.asList(eeClsTest.trim().split("\\r?\\n")));
				
//				} else if (eeCls.classifier.equals(VectorClassifier.liblinear)) {
//					eeCls.train(trainFvList, "models/" + task.name + ".model");
//					
//					List<String> eeClsTest = eeCls.predict(evalFvList, "models/" + task.name + ".model");
//					for (int i=0; i<evalFvList.size(); i++) {
//						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
//						eeTestList.add((labelList.indexOf(eefv.getLabel())+1)
//								+ "\t" + (labelList.indexOf(eeClsTest.get(i))+1));
//					}
//					
//				} else if (eeCls.classifier.equals(VectorClassifier.libsvm)) {
//					model = eeCls.trainSVM(trainFvList);
//					
//					List<String> eeClsTest = eeCls.predict(evalFvList, model);
//					for (int i=0; i<evalFvList.size(); i++) {
//						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
//						eeTestList.add((labelList.indexOf(eefv.getLabel())+1)
//								+ "\t" + (labelList.indexOf(eeClsTest.get(i))+1));
//					}
				}
				
				//Evaluate
				PairEvaluator pee = new PairEvaluator(eeTestList);
				pee.evaluateCausalPerLabelIdx(label);
				
				/*** SELF TRAINING ***/
				
				String labelAquaint; Double probAquaint = 1.0;
				int numAquaintClink = 0, numAquaintNone = 0;
				
				//filter pairs recognized via rules
				for (PairFeatureVector fv : eeAquaint) {
					if (fv.getPredLabel() == null) {
						evalAquaint.add(fv);
					}
				}
				
				List<String> eeClsAquaint = new ArrayList<String>();
				if (eeCls.classifier.equals(VectorClassifier.yamcha)) {
					eeClsAquaint = Arrays.asList(eeCls.test(evalAquaint, labelProbs, label).trim().split("\\r?\\n"));
				
//				} else if (eeCls.classifier.equals(VectorClassifier.liblinear)) {
//					eeClsAquaint = eeCls.predict(evalAquaint, "models/" + task.name + ".model");	
//				
//				} else if (eeCls.classifier.equals(VectorClassifier.libsvm)) {
//					eeClsAquaint = eeCls.predict(evalAquaint, model);
				}
								
				for (int i=0; i<evalAquaint.size(); i++) {
					labelAquaint = eeClsAquaint.get(i);
					evalAquaint.get(i).setLabel(labelAquaint);
					
					if (!labelAquaint.equals("NONE")) {
						trainFvList.add(evalAquaint.get(i));
						numAquaintClink ++;
					} else {
						trainFvList.add(evalAquaint.get(i));
						numAquaintNone ++;
					}
				}
				
				if (eeCls.classifier.equals(VectorClassifier.yamcha)) {
					eeCls.train(trainFvList, labelProbs);
					
					String eeClsTest = eeCls.test(evalFvList, labelProbs, label);
					eeTestListSelfTrain.addAll(Arrays.asList(eeClsTest.trim().split("\\r?\\n")));
				
//				} else if (eeCls.classifier.equals(VectorClassifier.liblinear)) {
//					eeCls.train(trainFvList, "models/" + task.name + ".model");
//					
//					List<String> eeClsTest = eeCls.predict(evalFvList, "models/" + task.name + ".model");
//					for (int i=0; i<evalFvList.size(); i++) {
//						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
//						eeTestListSelfTrain.add((labelList.indexOf(eefv.getLabel())+1)
//								+ "\t" + (labelList.indexOf(eeClsTest.get(i))+1));
//					}
//					
//				} else if (eeCls.classifier.equals(VectorClassifier.libsvm)) {
//					model = eeCls.trainSVM(trainFvList);
//					
//					List<String> eeClsTest = eeCls.predict(evalFvList, model);
//					for (int i=0; i<evalFvList.size(); i++) {
//						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
//						eeTestListSelfTrain.add((labelList.indexOf(eefv.getLabel())+1)
//								+ "\t" + (labelList.indexOf(eeClsTest.get(i))+1));
//					}
				}
				
				//Evaluate
				PairEvaluator peest = new PairEvaluator(eeTestListSelfTrain);
				peest.evaluateCausalPerLabelIdx(label);
			}

//		}
	}
}
