package relpro;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.classifier.EventEventCausalClassifier;
import model.classifier.PairClassifier;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.Marker;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.FeatureName;
import model.rule.EventEventRelationRule;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.CausalRelation;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.Sentence;
import parser.entities.TemporalRelation;
import task.CausalTimeBankTaskExperiments;

public class CauseRelPro {
	
	private String name;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	public CauseRelPro() throws IOException {
		name = "causerelpro";
		
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
	}
	
	public Map<String,String> getCLINKs(Doc doc) {
		Map<String,String> clinks = new HashMap<String,String>();
		String pair = null, pairInv = null;
		for (CausalRelation clink : doc.getClinks()) {
			pair = clink.getSourceID() + "," + clink.getTargetID();
			pairInv = clink.getTargetID()+ "," + clink.getSourceID();
			clinks.put(pair, "CLINK");
			clinks.put(pairInv, "CLINK-R");
		}		
		return clinks;
	}
	
	public Boolean isContainCausalSignal(Sentence sent, Doc doc) {
		Map<String, String> signalList = csignalList.getList();
		
		Object[] sigKeys = signalList.keySet().toArray();
		Arrays.sort(sigKeys, Collections.reverseOrder());
		
		String str = " " + sent.toLowerString(doc) + " ";
		for (Object key : sigKeys) {
			Pattern pattern = Pattern.compile(" " + (String)key + " ");
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
								pair = e1.getID() + "," + e2.getID();
								if (clinks.containsKey(pair)) {
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
									pair = e1.getID() + "," + e2.getID();
									if (clinks.containsKey(pair)) {
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
			File txpFile, PairClassifier eeRelCls, boolean train, 
			Map<String,String> tlinks) throws Exception {
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListRule = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Map<String,String> candidates = getCandidatePairs(docTxp);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		String[] tlinksArr = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> tlinkTypes = Arrays.asList(tlinksArr);
		
//		System.err.println(txpFile.getName());
	    
		for (String clink : candidates.keySet()) {	//for every CLINK in TXP file: candidate pairs
			Entity e1 = docTxp.getEntities().get(clink.split(",")[0]);
			Entity e2 = docTxp.getEntities().get(clink.split(",")[1]);
			PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			
			String rule = EventEventRelationRule.getEventCausalityRule(eefv);
			if (!rule.equals("O")) {
				if (rule.contains("-R")) eefv.setPredLabel("CLINK-R");
				else eefv.setPredLabel("CLINK");
				fvListRule.add(eefv);
				
			} else {
			
				if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
					eefv.addToVector(FeatureName.id);
				}
				
				//Add features to feature vector
				for (FeatureName f : eeRelCls.featureList) {
					eefv.addBinaryFeatureToVector(f);
				}
				
				//Add TLINK type feature to feature vector
				String tlinkType = "O";
				if (tlinks.isEmpty()) {
					if (docTxp.getTlinkTypes().containsKey(e1.getID()+","+e2.getID())) {
						tlinkType = docTxp.getTlinkTypes().get(e1.getID()+","+e2.getID());
					} else if (docTxp.getTlinkTypes().containsKey(e2.getID()+","+e1.getID())) {
						tlinkType = TemporalRelation.getInverseRelation(docTxp.getTlinkTypes().get(e2.getID()+","+e1.getID()));
					} 			
				} else {
					if (tlinks.containsKey(e1.getID()+","+e2.getID())) {
						tlinkType = tlinks.get(e1.getID()+","+e2.getID());
					} 
				}
				eefv.addBinaryFeatureToVector("tlink", tlinkType, tlinkTypes);
				
				//Add label
				eefv.addBinaryFeatureToVector(FeatureName.labelCaus);
				
				Marker m = fv.getCausalSignal();
				if (!eefv.getSimplifiedSignalDependencyPath(m).equals("O|O")) {
					fvListNone.add(eefv);
				}
			}
		}
		
		fvList.add(fvListNone);
		if (train) {
			fvList.add(fvListClink);
			fvList.add(fvListClinkR);
		}
		fvList.add(fvListRule);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinksPerText(TXPParser txpParser, 
			String[] lines, PairClassifier eeRelCls,
			Map<String,String> tlinks) throws Exception {
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
//		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
//		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListRule = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseLines(lines);
		Map<String,String> candidates = getCandidatePairs(docTxp);
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		String[] tlinksArr = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> tlinkTypes = Arrays.asList(tlinksArr);
	    
		for (String clink : candidates.keySet()) {	//for every CLINK in TXP file: candidate pairs
			Entity e1 = docTxp.getEntities().get(clink.split(",")[0]);
			Entity e2 = docTxp.getEntities().get(clink.split(",")[1]);
			PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			
			String rule = EventEventRelationRule.getEventCausalityRule(eefv);
			if (!rule.equals("O")) {
				if (rule.contains("-R")) eefv.setPredLabel("CLINK-R");
				else eefv.setPredLabel("CLINK");
				fvListRule.add(eefv);
				
			} else {
				
				//Add features to feature vector
				for (FeatureName f : eeRelCls.featureList) {
					eefv.addBinaryFeatureToVector(f);
				}
				
				//Add TLINK type feature to feature vector
				String tlinkType = "O";
				if (tlinks.isEmpty()) {
					if (docTxp.getTlinkTypes().containsKey(e1.getID()+","+e2.getID())) {
						tlinkType = docTxp.getTlinkTypes().get(e1.getID()+","+e2.getID());
					} else if (docTxp.getTlinkTypes().containsKey(e2.getID()+","+e1.getID())) {
						tlinkType = TemporalRelation.getInverseRelation(docTxp.getTlinkTypes().get(e2.getID()+","+e1.getID()));
					} 			
				} else {
					if (tlinks.containsKey(e1.getID()+","+e2.getID())) {
						tlinkType = tlinks.get(e1.getID()+","+e2.getID());
					} 
				}
				eefv.addBinaryFeatureToVector("tlink", tlinkType, tlinkTypes);
				
				//Add label
				eefv.addBinaryFeatureToVector(FeatureName.labelCaus);
				
				Marker m = fv.getCausalSignal();
				if (!eefv.getSimplifiedSignalDependencyPath(m).equals("O|O")) {
					fvListNone.add(eefv);
				}
			}
		}
		
		fvList.add(fvListNone);
//		fvList.add(fvListClink);
//		fvList.add(fvListClinkR);
		fvList.add(fvListRule);
		
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls, boolean train,
			Map<String,String> tlinks) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCrule = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, eeRelCls, true, tlinks);
			
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
	
	public void trainModel() throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.supersense, Field.ss_ner, Field.clink, Field.csignal};
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);
		
		String txpDirpath = "./data/Causal-TimeBank_TXP2/";
		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causerelpro", "liblinear");
		
		List<PairFeatureVector> fvList = getEventEventClinks(txpParser, txpDirpath, 
				eeCls, true, new HashMap<String, String>());
		List<PairFeatureVector> trainFvList = new ArrayList<PairFeatureVector>();
		for (PairFeatureVector fv : fvList) {
			if (fv.getPredLabel() == null) trainFvList.add(fv);
		}
		eeCls.train(trainFvList, "models/" + name + ".model");
	}
	
	public List<List<PairFeatureVector>> buildFeatureVectorFromFile(TXPParser txpParserFile, 
			String filepath) throws Exception {
		
		File txpFile = new File(filepath);
		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causerelpro", "liblinear");
		
		return getEventEventClinksPerFile(txpParserFile, 
				txpFile, eeCls, false, new HashMap<String, String>());
	}
	
	public List<List<PairFeatureVector>> buildFeatureVectorFromText(TXPParser txpParserText,
			String[] text, Map<String, String> tlinks) throws Exception {
		
		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causerelpro", "liblinear");
		
		return getEventEventClinksPerText(txpParserText, 
				text, eeCls, tlinks);
	}
	
	public List<String> testModel(List<List<PairFeatureVector>> testFvList) throws Exception {
		List<String> predictions = new ArrayList<String>();
		
		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causerelpro", "liblinear");
		
		//RULE
		for (PairFeatureVector fv : testFvList.get(1)) {
			predictions.add(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t"
					+ fv.getPredLabel());
		}
		
		//CLASSIFIER
		List<PairFeatureVector> clsFvList = testFvList.get(0);
		List<String> clsResult = eeCls.predict(clsFvList, "models/" + name + ".model");
		for (int i=0; i<clsFvList.size(); i++) {
			if (!clsResult.get(i).equals("NONE"))
				predictions.add(clsFvList.get(i).getE1().getID() + "\t" + clsFvList.get(i).getE2().getID() + "\t"
						+ clsResult.get(i));
		}
		
		return predictions;
	}
	
	public static void main(String [] args) throws Exception {
		CauseRelPro task = new CauseRelPro();
		
		/*****TRAIN****/
		task.trainModel();
		
		
		/*****TEST****/
		
		//1. BUILD FEATURE VECTOR FROM TEXT
		String[] sampleText = {
				"Other	t185	8	AJ0	other	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"market-maker	t186	8	NN1	market-maker	O	O	O	O	O	O	O	I-NP	O	O	O	O	O",
				"gripes	t187	8	NN2	gripe	t185:NMOD||t186:NMOD||t188:P||t192:NMOD||t207:P	O	O	O	O	O	O	I-NP	O	O	O	O	O",
				":	t188	8	PUN	:	O	O	O	O	O	O	O	O	O	O	O	O	O",
				"Program	t189	8	NN1	program	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"trading	t190	8	NN1	trading	t189:NMOD	O	O	O	O	OCCURRENCE	e38	I-NP	O	O	O	O	O",
				"also	t191	8	AV0	also	O	O	O	O	O	O	O	B-ADVP	O	O	O	O	O",
				"causes	t192	8	NN0	cause	t190:SBJ||t191:ADV||t196:OBJ||t197:OPRD	O	O	O	O	OCCURRENCE	e39	B-VP	O	PRESENT+NONE+pos	O	O	O",
				"the	t193	8	AT0	the	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"Nasdaq	t194	8	NP0	nasdaq	O	O	O	O	organization	O	O	I-NP	O	O	O	O	O",
				"Composite	t195	8	NP0	composite	O	O	O	O	organization	O	O	I-NP	O	O	O	O	O",
				"Index	t196	8	NP0	index	t193:NMOD||t194:NAME||t195:NAME	O	O	O	organization	O	O	I-NP	O	O	O	O	O",
				"to	t197	8	TO0	to	t198:IM	O	O	O	O	O	O	B-VP	O	O	O	O	O",
				"lose	t198	8	VVB	lose	t199:OBJ||t200:ADV	O	O	O	O	OCCURRENCE	e41	I-VP	O	INFINITIVE+NONE+pos	O	O	O",
				"ground	t199	8	NN1	ground	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"against	t200	8	PRP	against	t202:PMOD	O	O	O	O	O	O	B-PP	O	O	O	O	O",
				"other	t201	8	AJ0	other	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"segments	t202	8	NN2	segment	t201:NMOD||t203:NMOD	O	O	O	O	O	O	I-NP	O	O	O	O	O",
				"of	t203	8	PRF	of	t206:PMOD	O	O	O	O	O	O	B-PP	O	O	O	O	O",
				"the	t204	8	AT0	the	O	O	O	O	O	O	O	B-NP	O	O	O	O	O",
				"stock	t205	8	NN1	stock	O	O	O	O	O	O	O	I-NP	O	O	O	O	O",
				"market	t206	8	NN1	market	t204:NMOD||t205:NMOD	O	O	O	O	O	O	I-NP	O	O	O	O	O",
				".	t207	8	PUN	.	O	O	O	O	O	O	O	O	O	O	O	O	O"
		};
		//Providing TLINK information
		Map<String, String> tlinks = new HashMap<String, String>();
		tlinks.put("e39,e41", "BEFORE");
		Field[] fieldsText = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.chunk, Field.main_verb, 
				Field.tense_aspect_pol, Field.tlink, Field.clink, Field.csignal};
		TXPParser txpParserText = new TXPParser(EntityEnum.Language.EN, fieldsText);
//		List<List<PairFeatureVector>> testFvList = task.buildFeatureVectorFromText(txpParserText,
//				sampleText, tlinks);
		
		//or...
		
		//2. BUILD FEATURE VECTOR FROM FILE
		String filepath = "./data/example_TXP/wsj_1014.tml.txp";
		Field[] fieldsFile = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.supersense, Field.ss_ner, Field.clink, Field.csignal};
		TXPParser txpParserFile = new TXPParser(EntityEnum.Language.EN, fieldsFile);
		List<List<PairFeatureVector>> testFvList = task.buildFeatureVectorFromFile(txpParserFile, filepath);
		
		
		List<String> predictions = task.testModel(testFvList);
		
		//PRINT RESULT
		for (String clink : predictions) System.out.println(clink);
		
	}

}
