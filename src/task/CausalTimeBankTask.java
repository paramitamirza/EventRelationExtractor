package task;
import java.io.File;
import java.io.FileOutputStream;
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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import evaluator.PairEvaluator;
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

public class CausalTimeBankTask {
	
	private ArrayList<String> features;
	private String name;
	private String TXPPath;
	private String CATPath;
	private String systemCATPath;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	public CausalTimeBankTask() throws IOException {
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
	
	public Map<String,String> getCandidatePairs(Doc doc) {
		Map<String,String> candidates = new HashMap<String,String>();
		Map<String,String> clinks = getCLINKs(doc);
		
		for (int s=0; s<doc.getSentenceArr().size(); s++) {
			Sentence s1 = doc.getSentences().get(doc.getSentenceArr().get(s));
			
			//candidate pairs within the same sentence
			Entity e1, e2;
			String pair = null;
			for (int i = 0; i < s1.getEntityArr().size()-1; i++) {
				for (int j = i+1; j < s1.getEntityArr().size(); j++) {
					e1 = doc.getEntities().get(s1.getEntityArr().get(i));
					e2 = doc.getEntities().get(s1.getEntityArr().get(j));
					if (e1 instanceof Event && e2 instanceof Event) {
						pair = e1.getID() + "-" + e2.getID();
						if (clinks.containsKey(pair)) {
							candidates.put(pair, clinks.get(pair));
						} else {
							candidates.put(pair, "NONE");
						}
					}
				}
			}
			
			//candidate pairs in consecutive sentences
			if (s < doc.getSentenceArr().size()-1) {
				Sentence s2 = doc.getSentences().get(doc.getSentenceArr().get(s+1));
				for (int i = 0; i < s1.getEntityArr().size(); i++) {
					for (int j = 0; j < s2.getEntityArr().size(); j++) {
						e1 = doc.getEntities().get(s1.getEntityArr().get(i));
						e2 = doc.getEntities().get(s2.getEntityArr().get(j));
						if (e1 instanceof Event && e2 instanceof Event) {
							pair = e1.getID() + "-" + e2.getID();
							if (clinks.containsKey(pair)) {
								candidates.put(pair, clinks.get(pair));
							} else {
								candidates.put(pair, "NONE");
							}
						}
					}
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
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Map<String,String> candidates = getCandidatePairs(docTxp);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		String[] tlinksArr = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> tlinkTypes = Arrays.asList(tlinksArr);
	    
		for (String clink : candidates.keySet()) {	//for every CLINK in TXP file: candidate pairs
			Entity e1 = docTxp.getEntities().get(clink.split("-")[0]);
			Entity e2 = docTxp.getEntities().get(clink.split("-")[1]);
			PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			
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
//					eefv.addBinaryFeatureToVector(f);
				}
			}
			
			//Add TLINK type feature
			if (docTxp.getTlinkTypes().containsKey(e1.getID()+","+e2.getID())) {
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
						eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
						eeRelCls.classifier.equals(VectorClassifier.weka)) {
					eefv.addBinaryFeatureToVector("tlink", docTxp.getTlinkTypes().get(e1.getID()+","+e2.getID()), tlinkTypes);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.none)) {
					eefv.addToVector("tlink", docTxp.getTlinkTypes().get(e1.getID()+","+e2.getID()));
				}
			} else if (docTxp.getTlinkTypes().containsKey(e2.getID()+","+e1.getID())) {
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
						eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
						eeRelCls.classifier.equals(VectorClassifier.weka)) {
					eefv.addBinaryFeatureToVector("tlink", docTxp.getTlinkTypes().get(e2.getID()+","+e1.getID()), tlinkTypes);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.none)) {
					eefv.addToVector("tlink", docTxp.getTlinkTypes().get(e2.getID()+","+e1.getID()));
				}
			} else {
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
						eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
						eeRelCls.classifier.equals(VectorClassifier.weka)) {
					eefv.addBinaryFeatureToVector("tlink","O", tlinkTypes);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.none)) {
					eefv.addToVector("tlink", "O");
				}
			}
			
			if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
					eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
				eefv.addBinaryFeatureToVector(FeatureName.labelCaus);
			} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
					eeRelCls.classifier.equals(VectorClassifier.weka) ||
					eeRelCls.classifier.equals(VectorClassifier.none)){
				eefv.addToVector(FeatureName.label);
				
			}
			
			if (eefv.getLabel().equals("NONE")) {
				fvListNone.add(eefv);
			} else {
				fvListClink.add(eefv);
			}
		}
		
		fvList.add(fvListNone);
		fvList.add(fvListClink);
		
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls,
			boolean train, double threshold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, eeRelCls, train, threshold);
			
			fvListNone.addAll(fvListList.get(0));
			fvList.addAll(fvListList.get(1));
		}
		
		int numClink = fvList.size();
		int numNone = fvListNone.size();
		
		if (train) {
			if (threshold > 0) {
				//Take a ratio (according to threshold) of random elements from fvListNone
				Collections.shuffle(fvListNone);
				//int cutoff = (int) Math.floor(threshold * numNone);
				//int cutoff = (int) Math.floor((1/threshold) * numClink);
				int cutoff = (int) (threshold * numClink);
				if (cutoff > numNone) cutoff = numNone;
				for (int i=0; i<cutoff; i++) {
					fvList.add(fvListNone.get(i));
				}
			} else {
				for (PairFeatureVector fv : fvListNone) {
					fvList.add(fv);
				}
			}
		} else {
			for (PairFeatureVector fv : fvListNone) {
				fvList.add(fv);
			}
		}
		
		return fvList;
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
		
		CausalTimeBankTask task = new CausalTimeBankTask();
		
		PrintStream out = new PrintStream(new FileOutputStream("causality_output.txt"));
		System.setOut(out);
		PrintStream log = new PrintStream(new FileOutputStream("causality_log.txt"));
		System.setErr(log);
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);
		
//		String trainTxpDirpath = "./data/Causal-TimeBank_TXP/";
//		String evalTxpDirpath = "./data/Causal-TimeBank_TXP/";
		
		String trainTxpDirpath1 = "./data/Causal-TimeBank-train_TXP-1/";
		String evalTxpDirpath1 = "./data/Causal-TimeBank_TXP-1/";
		
		String trainTxpDirpath2 = "./data/Causal-TimeBank-train_TXP-2/";
		String evalTxpDirpath2 = "./data/Causal-TimeBank_TXP-2/";
		
		String trainTxpDirpath3 = "./data/Causal-TimeBank-train_TXP-3/";
		String evalTxpDirpath3 = "./data/Causal-TimeBank_TXP-3/";
		
		String trainTxpDirpath4 = "./data/Causal-TimeBank-train_TXP-4/";
		String evalTxpDirpath4 = "./data/Causal-TimeBank_TXP-4/";
		
		String trainTxpDirpath5 = "./data/Causal-TimeBank-train_TXP-5/";
		String evalTxpDirpath5 = "./data/Causal-TimeBank_TXP-5/";
		
		String[] trainTxpFoldPath = {"./data/Causal-TimeBank-train_TXP-1/", "./data/Causal-TimeBank-train_TXP-2/",
				"./data/Causal-TimeBank-train_TXP-3/", "./data/Causal-TimeBank-train_TXP-4/", "./data/Causal-TimeBank-train_TXP-5/"};
		String[] evalTxpFoldPath = {"./data/Causal-TimeBank_TXP-1/", "./data/Causal-TimeBank_TXP-2/",
				"./data/Causal-TimeBank_TXP-3/", "./data/Causal-TimeBank_TXP-4/", "./data/Causal-TimeBank_TXP-5/"};
		
		String[] aquaintPath = {"./data/AQUAINT_TXP/1/", "./data/AQUAINT_TXP/2/", "./data/AQUAINT_TXP/3/", 
				"./data/AQUAINT_TXP/4/", "./data/AQUAINT_TXP/5/", "./data/AQUAINT_TXP/6/", "./data/AQUAINT_TXP/7/"};
		String aquaintAllPath = "./data/AQUAINT_TXP/all/";
		
		//Init classifiers
		PairClassifier eeCls = new EventEventCausalClassifier("causal", "yamcha");
		
		boolean labelProbs = true;
		
//		double threshold = 1000;
//		double[] thresholds = {1000,0.5,1,2,3,4,5,6,7,8,9,10,20,30,40,50,60,70,80,90,100};
		double[] thresholds = {0,1,10,20,30,40,50,60,70,80,90,100,1000};
		String[] label = {"CLINK", "CLINK-R", "NONE"};
		for (double threshold : thresholds) {
			
			System.err.println("Threshold " + (threshold) + "...");
			System.out.println("Threshold " + (threshold) + "...");
			
			for (int fold=0; fold<5; fold++) {
		
				System.err.println("Iteration 0...");
				System.err.println("Fold " + (fold+1) + "...");
				System.out.println("Fold " + (fold+1) + "...");
				//Train models
				List<PairFeatureVector> eeTrainFvList = task.getEventEventClinks(txpParser, trainTxpFoldPath[fold], eeCls, true, threshold);
				eeCls.train(eeTrainFvList, labelProbs);
				
				//Test models
				List<PairFeatureVector> eeEvalFvList = task.getEventEventClinks(txpParser, evalTxpFoldPath[fold], eeCls, false, 0);
				String eeClsTest = eeCls.test(eeEvalFvList, labelProbs, label);
				String[] eeClsTestList = eeClsTest.trim().split("\\r?\\n");
				List<String> eeTestList = new ArrayList<String>();
				for (int i=0; i<eeClsTestList.length; i++) {
					eeTestList.add(eeClsTestList[i]);
				}
				
				//Evaluate
				PairEvaluator pee = new PairEvaluator(eeTestList);
				pee.evaluateCausalPerLabelIdx(label);
				
//				for (int itr=0; itr<7; itr++) {
//					System.err.println("Iteration " + (itr+1) + "...");
//					System.out.println("Iteration " + (itr+1) + "...");
					
					//List<PairFeatureVector> eeAquaint = task.getEventEventClinks(txpParser, aquaintPath[itr], eeCls, false, 0);
					List<PairFeatureVector> eeAquaint = task.getEventEventClinks(txpParser, aquaintAllPath, eeCls, false, 0);
					
					List<PairFeatureVector> eeAquaintNone = new ArrayList<PairFeatureVector>();
					Map<Integer, Double> eeAquaintNoneProbs = new LinkedHashMap<Integer, Double>();
					
					String[] eeClsAquaint = eeCls.test(eeAquaint, labelProbs, label).trim().split("\\r?\\n");
					String labelAquaint; Double probAquaint = 1.0;
					int numAquaintClink = 0, numAquaintNone = 0;
					for (int i=0; i<eeAquaint.size(); i++) {
						labelAquaint = label[Integer.parseInt(eeClsAquaint[i].split("\t")[1])-1];
						if (labelProbs) probAquaint = Double.parseDouble(eeClsAquaint[i].split("\t")[2]);
						eeAquaint.get(i).setLabel(labelAquaint);
						if (!labelAquaint.equals("NONE")) {
							eeTrainFvList.add(eeAquaint.get(i));
							numAquaintClink ++;
						} else {
							eeAquaintNone.add(eeAquaint.get(i));
							eeAquaintNoneProbs.put(numAquaintNone, probAquaint);
							numAquaintNone ++;
						}
					}
					
					//Sort eeAquaintNoneProbs
					Map<Integer, Double> eeAquaintNoneProbsSorted = SortMapByValue.sortByComparator(eeAquaintNoneProbs, true);
					
					//Take a ratio (according to threshold) of sorted elements from None list
					//int cutoff = (int) Math.floor(threshold * numAquaintNone);
					//int cutoff = (int) Math.floor((1/threshold) * numAquaintClink);
					int cutoff = (int) (threshold * numAquaintClink);
					if (cutoff > numAquaintNone) cutoff = numAquaintNone;
					int z=0;
					for (Integer idx : eeAquaintNoneProbsSorted.keySet()) {
						if (z<cutoff) {
							eeTrainFvList.add(eeAquaintNone.get(idx));
						} else {
							break;
						}
						z++;
					}				
					eeCls.train(eeTrainFvList);
					
					//Test models					
					String eeClsTestItr = eeCls.test(eeEvalFvList, labelProbs, label);
					String[] eeClsTestListItr = eeClsTestItr.trim().split("\\r?\\n");
					List<String> eeTestListItr = new ArrayList<String>();
					for (int i=0; i<eeClsTestListItr.length; i++) {
						eeTestListItr.add(eeClsTestListItr[i]);
					}
					
					//Evaluate
					PairEvaluator peeItr = new PairEvaluator(eeTestListItr);
					peeItr.evaluateCausalPerLabelIdx(label);
//				}
			}
		}
	}
}
