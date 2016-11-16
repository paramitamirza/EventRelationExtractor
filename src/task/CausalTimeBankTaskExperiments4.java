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
import model.rule.EventTimexRelationRule;
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
import parser.entities.TimeMLDoc;
import parser.entities.Timex;
import server.RemoteServer;
import parser.entities.CausalRelation;

public class CausalTimeBankTaskExperiments4 {
	
	private ArrayList<String> features;
	private String name;
	private String TXPPath;
	private String CATPath;
	private String systemCATPath;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	private List<String> labelList = Arrays.asList(label);
	
	private String[] ruleTlinks = {"BEFORE", "AFTER", "SIMULTANEOUS", "INCLUDES", "IS_INCLUDED"};
	private List<String> ruleTlinkTypes = Arrays.asList(ruleTlinks);
	
	public CausalTimeBankTaskExperiments4() throws IOException {
		name = "te3-causal";
		
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
				
//				if (isContainCausalSignal(s1, doc) || isContainCausalVerb(s1, doc)) {
				
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
				
//				}
				
				//candidate pairs in consecutive sentences
				if (s < doc.getSentenceArr().size()-1) {
//					if (doc.getTokens().get(e1.getStartTokID()).isMainVerb()) {
						Sentence s2 = doc.getSentences().get(doc.getSentenceArr().get(s+1));
//						if (isContainCausalSignal(s2, doc)) {
						
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
//						}
//					}
				}
			}
		}
		
		return candidates;
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
	
	public List<List<PairFeatureVector>> getEventEventTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier eeRelCls,
			boolean train, boolean goldCandidate) throws Exception {
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		
		List<PairFeatureVector> fvListBefore = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListAfter = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIdentity = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListSimultaneous = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIncludes = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIsIncluded = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListBegins = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListBegunBy = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListEnds = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListEndedBy = new ArrayList<PairFeatureVector>();
		
		List<PairFeatureVector> fvListCBefore = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCAfter = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIdentity = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCSimultaneous = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIncludes = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIsIncluded = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCBegins = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCBegunBy = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCEnds = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCEndedBy = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		List<TemporalRelation> candidateTlinks = new ArrayList<TemporalRelation> ();
		if (goldCandidate || train) candidateTlinks = docTml.getTlinks();	//gold annotated pairs
		else candidateTlinks = docTxp.getTlinks();									//candidate pairs
		
		TimeMLDoc tmlDoc = new TimeMLDoc(tmlFile.getPath());
		List<String> arrEvents = tmlParser.getEvents(tmlDoc);
		Map<String, String> clinks = new HashMap<String, String>();
		for (CausalRelation clink : docTml.getClinks()) {
			int sourceIdx = arrEvents.indexOf(clink.getSourceID());
			int targetIdx = arrEvents.indexOf(clink.getTargetID());
			if (sourceIdx < targetIdx) {
				String key = clink.getSourceID()+"-"+clink.getTargetID();
				clinks.put(key, "CLINK");
			} else {
				String key = clink.getTargetID()+"-"+clink.getSourceID();
				clinks.put(key, "CLINK-R");
			}
		}
		
		//event-DCT rules
		EventTimexRelationClassifier dctCls = new EventTimexRelationClassifier("te3", "liblinear");
		List<PairFeatureVector> etFvList = getEventDctTlinksPerFile(txpParser, tmlParser, 
				txpFile, tmlFile, dctCls, false, false);
		
		Map<String, String> eDctRules = new HashMap<String, String>();
		for (PairFeatureVector fv : etFvList) {
			EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
			EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
					docTxp, etfv.getMateDependencyPath());
			if (!etRule.getRelType().equals("O")) {
				eDctRules.put(etfv.getE1().getID(), etRule.getRelType());
			}
		}
		
		String[] clinksArr = {"CLINK", "CLINK-R", "NONE"};
		List<String> clinkTypes = Arrays.asList(clinksArr);
		
		for (TemporalRelation tlink : candidateTlinks) {	
			
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				PairFeatureVector tfv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
					EventEventFeatureVector eetfv = new EventEventFeatureVector(tfv);
					
					//Add features to feature vector
					if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
						eefv.addToVector(FeatureName.id);
						eetfv.addToVector(FeatureName.id);
					}
					
					for (FeatureName f : eeRelCls.featureList) {
						if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
								eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
								eeRelCls.classifier.equals(VectorClassifier.weka)) {
							eefv.addBinaryFeatureToVector(f);
							eetfv.addBinaryFeatureToVector(f);
						} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
								eeRelCls.classifier.equals(VectorClassifier.none)) {
							eefv.addToVector(f);
							eetfv.addToVector(f);
						}
					}
					
					//Add event-timex/DCT TLINK type feature to feature vector
					String etRule1 = "O", etRule2 = "O";
					if (eDctRules.containsKey(eefv.getE1().getID())) etRule1 = eDctRules.get(eefv.getE1().getID());
					if (eDctRules.containsKey(eefv.getE2().getID())) etRule2 = eDctRules.get(eefv.getE2().getID());
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
							eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
						eefv.addBinaryFeatureToVector("etRule1", etRule1, ruleTlinkTypes);
						eefv.addBinaryFeatureToVector("etRule2", etRule2, ruleTlinkTypes);
						eetfv.addBinaryFeatureToVector("etRule1", etRule1, ruleTlinkTypes);
						eetfv.addBinaryFeatureToVector("etRule2", etRule2, ruleTlinkTypes);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						eefv.addToVector("etRule1", etRule1);
						eefv.addToVector("etRule2", etRule2);
						eetfv.addToVector("etRule1", etRule1);
						eetfv.addToVector("etRule2", etRule2);
					}
					
					String clinkType = "NONE";
					if (clinks.containsKey(e1.getID()+"-"+e2.getID())) {
						clinkType = clinks.get(e1.getID()+"-"+e2.getID());
					} 	
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
							eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
							eeRelCls.classifier.equals(VectorClassifier.weka)) {
						eetfv.addBinaryFeatureToVector("clink", clinkType, clinkTypes);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.none)) {
						eetfv.addToVector("tlink", clinkType);
					}
					
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
							eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
						if (train) {
							eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
							eetfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						}
						else {
							eefv.addBinaryFeatureToVector(FeatureName.label);
							eetfv.addBinaryFeatureToVector(FeatureName.label);
						}
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						if (train) {
							eefv.addToVector(FeatureName.labelCollapsed);
							eetfv.addToVector(FeatureName.labelCollapsed);
						}
						else {
							eefv.addToVector(FeatureName.label);
							eetfv.addToVector(FeatureName.label);
						}
					}
					
					if (eefv.getLabel().equals("BEFORE")) {
						fvListBefore.add(eefv);
						fvListCBefore.add(eetfv);
					}
					else if (eefv.getLabel().equals("AFTER")) {
						fvListAfter.add(eefv);
						fvListCAfter.add(eetfv);
					}
					else if (eefv.getLabel().equals("IBEFORE")) {
						fvListBefore.add(eefv);
						fvListCBefore.add(eetfv);
					}
					else if (eefv.getLabel().equals("IAFTER")) {
						fvListAfter.add(eefv);
						fvListCAfter.add(eetfv);
					}
					else if (eefv.getLabel().equals("IDENTITY")) {
						fvListIdentity.add(eefv);
						fvListCIdentity.add(eetfv);
					}
					else if (eefv.getLabel().equals("SIMULTANEOUS")) {
						fvListSimultaneous.add(eefv);
						fvListCSimultaneous.add(eetfv);
					}
					else if (eefv.getLabel().equals("DURING")) {
						fvListSimultaneous.add(eefv);
						fvListCSimultaneous.add(eetfv);
					}
					else if (eefv.getLabel().equals("DURING_INV")) {
						fvListSimultaneous.add(eefv);
						fvListCSimultaneous.add(eetfv);
					}
					else if (eefv.getLabel().equals("INCLUDES")) {
						fvListIncludes.add(eefv);
						fvListCIncludes.add(eetfv);
					}
					else if (eefv.getLabel().equals("IS_INCLUDED")) {
						fvListIsIncluded.add(eefv);
						fvListCIsIncluded.add(eetfv);
					}
					else if (eefv.getLabel().equals("BEGINS")) {
						fvListBegins.add(eefv);
						fvListCBegins.add(eetfv);
					}
					else if (eefv.getLabel().equals("BEGUN_BY")) {
						fvListBegunBy.add(eefv);
						fvListCBegunBy.add(eetfv);
					}
					else if (eefv.getLabel().equals("ENDS")) {
						fvListEnds.add(eefv);
						fvListCEnds.add(eetfv);
					}
					else if (eefv.getLabel().equals("ENDED_BY")) {
						fvListEndedBy.add(eefv);
						fvListCEndedBy.add(eetfv);
					}
				}
			}
		}
		fvList.add(fvListBefore);
		fvList.add(fvListAfter);
		fvList.add(fvListIdentity);
		fvList.add(fvListSimultaneous);
		fvList.add(fvListIncludes);
		fvList.add(fvListIsIncluded);
		fvList.add(fvListBegins);
		fvList.add(fvListBegunBy);
		fvList.add(fvListEnds);
		fvList.add(fvListEndedBy);
		
		fvList.add(fvListCBefore);
		fvList.add(fvListCAfter);
		fvList.add(fvListCIdentity);
		fvList.add(fvListCSimultaneous);
		fvList.add(fvListCIncludes);
		fvList.add(fvListCIsIncluded);
		fvList.add(fvListCBegins);
		fvList.add(fvListCBegunBy);
		fvList.add(fvListCEnds);
		fvList.add(fvListCEndedBy);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventTlinks(TXPParser txpParser, String dirTxpPath, PairClassifier eeRelCls,
			boolean train, boolean goldCandidate, int numFold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		for(int i=0; i<numFold*2; i++) fvList.add(new ArrayList<PairFeatureVector>());
		
		List<PairFeatureVector> fvListBefore = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListAfter = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIdentity = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListSimultaneous = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIncludes = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListIsIncluded = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListBegins = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListBegunBy = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListEnds = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListEndedBy = new ArrayList<PairFeatureVector>();
		
		List<PairFeatureVector> fvListCBefore = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCAfter = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIdentity = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCSimultaneous = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIncludes = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCIsIncluded = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCBegins = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCBegunBy = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCEnds = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCEndedBy = new ArrayList<PairFeatureVector>();
		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		String dirTML = "data/TempEval3-train_TML/";
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTML, txpFile.getName().replace(".txp", ""));
			List<List<PairFeatureVector>> fvListList = getEventEventTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, eeRelCls, train, goldCandidate);
			
			fvListBefore.addAll(fvListList.get(0));
			fvListAfter.addAll(fvListList.get(1));
			fvListIdentity.addAll(fvListList.get(2));
			fvListSimultaneous.addAll(fvListList.get(3));
			fvListIncludes.addAll(fvListList.get(4));
			fvListIsIncluded.addAll(fvListList.get(5));
			fvListBegins.addAll(fvListList.get(6));
			fvListBegunBy.addAll(fvListList.get(7));
			fvListEnds.addAll(fvListList.get(8));
			fvListEndedBy.addAll(fvListList.get(9));
			
			fvListCBefore.addAll(fvListList.get(10));
			fvListCAfter.addAll(fvListList.get(11));
			fvListCIdentity.addAll(fvListList.get(12));
			fvListCSimultaneous.addAll(fvListList.get(13));
			fvListCIncludes.addAll(fvListList.get(14));
			fvListCIsIncluded.addAll(fvListList.get(15));
			fvListCBegins.addAll(fvListList.get(16));
			fvListCBegunBy.addAll(fvListList.get(17));
			fvListCEnds.addAll(fvListList.get(18));
			fvListCEndedBy.addAll(fvListList.get(19));
		}
		
		int numBeforePerFold = (int)Math.floor(fvListBefore.size()/((double)numFold));
		int numAfterPerFold = (int)Math.floor(fvListAfter.size()/((double)numFold));
		int numIdentityPerFold = (int)Math.floor(fvListIdentity.size()/((double)numFold));
		int numSimultaneousPerFold = (int)Math.floor(fvListSimultaneous.size()/((double)numFold));
		int numIncludesPerFold = (int)Math.floor(fvListIncludes.size()/((double)numFold));
		int numIsIncludedPerFold = (int)Math.floor(fvListIsIncluded.size()/((double)numFold));
		int numBeginsPerFold = (int)Math.floor(fvListBegins.size()/((double)numFold));
		int numBegunByPerFold = (int)Math.floor(fvListBegunBy.size()/((double)numFold));
		int numEndsPerFold = (int)Math.floor(fvListEnds.size()/((double)numFold));
		int numEndedByPerFold = (int)Math.floor(fvListEndedBy.size()/((double)numFold));
		
		List<Integer> idxListBefore = new ArrayList<Integer>();
		for (int i=0; i<fvListBefore.size(); i++) {idxListBefore.add(i);}
		List<Integer> idxListAfter = new ArrayList<Integer>();
		for (int i=0; i<fvListAfter.size(); i++) {idxListAfter.add(i);}
		List<Integer> idxListIdentity = new ArrayList<Integer>();
		for (int i=0; i<fvListIdentity.size(); i++) {idxListIdentity.add(i);}
		List<Integer> idxListSimultaneous = new ArrayList<Integer>();
		for (int i=0; i<fvListSimultaneous.size(); i++) {idxListSimultaneous.add(i);}
		List<Integer> idxListIncludes = new ArrayList<Integer>();
		for (int i=0; i<fvListIncludes.size(); i++) {idxListIncludes.add(i);}
		List<Integer> idxListIsIncluded = new ArrayList<Integer>();
		for (int i=0; i<fvListIsIncluded.size(); i++) {idxListIsIncluded.add(i);}
		List<Integer> idxListBegins = new ArrayList<Integer>();
		for (int i=0; i<fvListBegins.size(); i++) {idxListBegins.add(i);}
		List<Integer> idxListBegunBy = new ArrayList<Integer>();
		for (int i=0; i<fvListBegunBy.size(); i++) {idxListBegunBy.add(i);}
		List<Integer> idxListEnds = new ArrayList<Integer>();
		for (int i=0; i<fvListEnds.size(); i++) {idxListEnds.add(i);}
		List<Integer> idxListEndedBy = new ArrayList<Integer>();
		for (int i=0; i<fvListEndedBy.size(); i++) {idxListEndedBy.add(i);}
		
		Collections.shuffle(idxListBefore);
		Collections.shuffle(idxListAfter);
		Collections.shuffle(idxListIdentity);
		Collections.shuffle(idxListSimultaneous);
		Collections.shuffle(idxListIncludes);
		Collections.shuffle(idxListIsIncluded);
		Collections.shuffle(idxListBegins);
		Collections.shuffle(idxListBegunBy);
		Collections.shuffle(idxListEnds);
		Collections.shuffle(idxListEndedBy);
		
		int idxBefore = 0, idxAfter = 0, idxIdentity = 0, idxSimultaneous = 0, idxIncludes = 0,
				idxIsIncluded = 0, idxBegins = 0, idxBegunBy = 0, idxEnds = 0, idxEndedBy = 0;
		for (int i=0; i<numFold; i++) {
			for (int j=0; j<numBeforePerFold; j++) {
				fvList.get(i).add(fvListBefore.get(idxListBefore.get(idxBefore)));
				fvList.get(i+numFold).add(fvListCBefore.get(idxListBefore.get(idxBefore)));
				idxBefore ++;
			}
			for (int j=0; j<numAfterPerFold; j++) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				fvList.get(i+numFold).add(fvListCAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			for (int j=0; j<numIdentityPerFold; j++) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				fvList.get(i+numFold).add(fvListCIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			for (int j=0; j<numSimultaneousPerFold; j++) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				fvList.get(i+numFold).add(fvListCSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			for (int j=0; j<numIncludesPerFold; j++) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				fvList.get(i+numFold).add(fvListCIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			for (int j=0; j<numIsIncludedPerFold; j++) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				fvList.get(i+numFold).add(fvListCIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			for (int j=0; j<numBeginsPerFold; j++) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));
				fvList.get(i+numFold).add(fvListCBegins.get(idxListBegins.get(idxBegins)));
				idxBegins ++;
			}
			for (int j=0; j<numBegunByPerFold; j++) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				fvList.get(i+numFold).add(fvListCBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			for (int j=0; j<numEndsPerFold; j++) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));
				fvList.get(i+numFold).add(fvListCEnds.get(idxListEnds.get(idxEnds)));
				idxEnds ++;
			}
			for (int j=0; j<numEndedByPerFold; j++) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				fvList.get(i+numFold).add(fvListCEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
			}
		}
		for (int i=0; i<numFold; i++) {
			if (idxBefore < fvListBefore.size()) {
				fvList.get(i).add(fvListBefore.get(idxListBefore.get(idxBefore)));
				fvList.get(i+numFold).add(fvListCBefore.get(idxListBefore.get(idxBefore)));
				idxBefore ++;
			}
			if (idxAfter < fvListAfter.size()) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				fvList.get(i+numFold).add(fvListCAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			if (idxIdentity < fvListIdentity.size()) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				fvList.get(i+numFold).add(fvListCIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			if (idxSimultaneous < fvListSimultaneous.size()) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				fvList.get(i+numFold).add(fvListCSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			if (idxIncludes < fvListIncludes.size()) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				fvList.get(i+numFold).add(fvListCIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			if (idxIsIncluded < fvListIsIncluded.size()) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				fvList.get(i+numFold).add(fvListCIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			if (idxBegins < fvListBegins.size()) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));
				fvList.get(i+numFold).add(fvListCBegins.get(idxListBegins.get(idxBegins)));
				idxBegins ++;
			}
			if (idxBegunBy < fvListBegunBy.size()) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				fvList.get(i+numFold).add(fvListCBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			if (idxEnds < fvListEnds.size()) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));
				fvList.get(i+numFold).add(fvListCEnds.get(idxListEnds.get(idxEnds)));
				idxEnds ++;
			}
			if (idxEndedBy < fvListEndedBy.size()) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				fvList.get(i+numFold).add(fvListCEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
			}
		}
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinksPerFile(TXPParser txpParser, 
			File txpFile, TimeMLParser tmlParser, File tmlFile, PairClassifier eeRelCls,
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
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		Map<String,String> candidates = getCandidatePairs(docTxp);
		Map<String, String> tlinks = new HashMap<String, String>();
		
		for (TemporalRelation tlink : docTml.getTlinks()) {
			tlinks.put(tlink.getSourceID()+"-"+tlink.getTargetID(), tlink.getRelType());
			tlinks.put(tlink.getTargetID()+"-"+tlink.getSourceID(), TemporalRelation.getInverseRelation(tlink.getRelType()));
		}
		
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
			PairFeatureVector tfv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			
			EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
			EventEventFeatureVector eetfv = new EventEventFeatureVector(tfv);
			
			String rule = EventEventRelationRule.getEventCausalityRule(eefv);
			if (!rule.equals("O") && !rule.equals("NONE")) {
				if (rule.contains("-R")) {
					eefv.setPredLabel("CLINK-R");
					eetfv.setPredLabel("CLINK-R");
				}
				else {
					eefv.setPredLabel("CLINK");
					eetfv.setPredLabel("CLINK");
				}
				fvListRule.add(eefv);
				fvListTRule.add(eetfv);
				
			} else if (rule.equals("O") 
					|| rule.equals("NONE")
					) {
			
				if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
					eefv.addToVector(FeatureName.id);
					eetfv.addToVector(FeatureName.id);
				}
				
				//Add features to feature vector
				for (FeatureName f : eeRelCls.featureList) {
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
							eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
							eeRelCls.classifier.equals(VectorClassifier.weka)) {
						eefv.addBinaryFeatureToVector(f);
						eetfv.addBinaryFeatureToVector(f);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.none)) {
						eefv.addToVector(f);
						eetfv.addToVector(f);
					}
				}
				
				String tlinkType = "NONE";
				if (tlinks.containsKey(e1.getID()+"-"+e2.getID())) {
					tlinkType = tlinks.get(e1.getID()+"-"+e2.getID());
				} 	
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
						eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
						eeRelCls.classifier.equals(VectorClassifier.weka)) {
					eetfv.addBinaryFeatureToVector("tlink", tlinkType, tlinkTypes);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.none)) {
					eetfv.addToVector("tlink", tlinkType);
				}
				
				if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
						eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
					eefv.addBinaryFeatureToVector(FeatureName.labelCaus);
					eetfv.addBinaryFeatureToVector(FeatureName.labelCaus);
				} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
						eeRelCls.classifier.equals(VectorClassifier.weka) ||
						eeRelCls.classifier.equals(VectorClassifier.none)){
					eefv.addToVector(FeatureName.labelCaus);
					eetfv.addToVector(FeatureName.labelCaus);
				}
				
				String depEvPathStr = eefv.getMateDependencyPath();
				Marker m = fv.getCausalSignal();
				if ((!m.getDepRelE1().equals("O") || !m.getDepRelE2().equals("O"))
						&& (!depEvPathStr.equals("SBJ")
								&& !depEvPathStr.equals("OBJ")
								&& !depEvPathStr.equals("COORD-CONJ")
								&& !depEvPathStr.equals("LOC-PMOD")
								&& !depEvPathStr.equals("VC")
								&& !depEvPathStr.equals("OPRD")
								&& !depEvPathStr.equals("OPRD-IM")
								)
						&& (eefv.getEntityDistance() < 5
//								&& eefv.getEntityDistance() >= 0
								)
						) {
				
					if (eefv.getLabel().equals("NONE")) {
						fvListNone.add(eefv);
						fvListTNone.add(eetfv);
					} else if (eefv.getLabel().equals("CLINK")) {
						fvListClink.add(eefv);
						fvListTClink.add(eetfv);
					} else if (eefv.getLabel().equals("CLINK-R")) {
						fvListClinkR.add(eefv);
						fvListTClinkR.add(eetfv);
					}
				}
			}
		}
		
		fvList.add(fvListNone);
		fvList.add(fvListClink);
		fvList.add(fvListClinkR);
		fvList.add(fvListRule);
		
		fvList.add(fvListTNone);
		fvList.add(fvListTClink);
		fvList.add(fvListTClinkR);
		fvList.add(fvListTRule);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls,
			boolean train, int numFold, double threshold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		for(int i=0; i<numFold*2; i++) fvList.add(new ArrayList<PairFeatureVector>());
		
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCrule = new ArrayList<PairFeatureVector>();
		
		List<PairFeatureVector> fvListTNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTCrule = new ArrayList<PairFeatureVector>();
		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		String dirTML = "data/TempEval3-train_TML_deduced/";
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTML, txpFile.getName().replace(".txp", ""));
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, tmlParser, tmlFile, eeRelCls, train, threshold);
			
			fvListNone.addAll(fvListList.get(0));
			fvListClink.addAll(fvListList.get(1));
			fvListClinkR.addAll(fvListList.get(2));
			fvListCrule.addAll(fvListList.get(3));
			
			fvListTNone.addAll(fvListList.get(4));
			fvListTClink.addAll(fvListList.get(5));
			fvListTClinkR.addAll(fvListList.get(6));
			fvListTCrule.addAll(fvListList.get(7));
		}
		
		int numNonePerFold = (int)Math.floor(fvListNone.size()/((double)numFold));
		int numClinkPerFold = (int)Math.floor(fvListClink.size()/((double)numFold));
		int numClinkRPerFold = (int)Math.floor(fvListClinkR.size()/((double)numFold));
		int numClinkRulePerFold = (int)Math.floor(fvListCrule.size()/((double)numFold));
		
		System.out.println("NONE: " + fvListNone.size() + ", CLINK: " + fvListClink.size() + ", CLINK-R: " + fvListClinkR.size() + ", CLINK (Rule): " + fvListCrule.size());
		
		List<Integer> idxListNone = new ArrayList<Integer>();
		for (int i=0; i<fvListNone.size(); i++) {idxListNone.add(i);}
		List<Integer> idxListClink = new ArrayList<Integer>();
		for (int i=0; i<fvListClink.size(); i++) {idxListClink.add(i);}
		List<Integer> idxListClinkR = new ArrayList<Integer>();
		for (int i=0; i<fvListClinkR.size(); i++) {idxListClinkR.add(i);}
		
		Collections.shuffle(idxListNone);
		Collections.shuffle(idxListClink);
		Collections.shuffle(idxListClinkR);
		
		int idxNone = 0, idxClink = 0, idxClinkR = 0, idxCrule = 0;
		for (int i=0; i<numFold; i++) {
			for (int j=0; j<numNonePerFold; j++) {
				fvList.get(i).add(fvListNone.get(idxListNone.get(idxNone)));
				fvList.get(i+numFold).add(fvListTNone.get(idxListNone.get(idxNone)));
				idxNone ++;
			}
			for (int j=0; j<numClinkPerFold; j++) {
				fvList.get(i).add(fvListClink.get(idxListClink.get(idxClink)));
				fvList.get(i+numFold).add(fvListTClink.get(idxListClink.get(idxClink)));
				idxClink ++;
			}
			for (int j=0; j<numClinkRPerFold; j++) {
				fvList.get(i).add(fvListClinkR.get(idxListClinkR.get(idxClinkR)));
				fvList.get(i+numFold).add(fvListTClinkR.get(idxListClinkR.get(idxClinkR)));
				idxClinkR ++;
			}
			for (int j=0; j<numClinkRulePerFold; j++) {
				fvList.get(i).add(fvListCrule.get(idxCrule));
				fvList.get(i+numFold).add(fvListTCrule.get(idxCrule));
				idxCrule ++;
			}
		}
		for (int i=0; i<numFold; i++) {
			if (idxNone < fvListNone.size()) {
				fvList.get(i).add(fvListNone.get(idxListNone.get(idxNone)));
				fvList.get(i+numFold).add(fvListTNone.get(idxListNone.get(idxNone)));
				idxNone ++;
			}
			if (idxClink < fvListClink.size()) {
				fvList.get(i).add(fvListClink.get(idxListClink.get(idxClink)));
				fvList.get(i+numFold).add(fvListTClink.get(idxListClink.get(idxClink)));
				idxClink ++;
			}
			if (idxClinkR < fvListClinkR.size()) {
				fvList.get(i).add(fvListClinkR.get(idxListClinkR.get(idxClinkR)));
				fvList.get(i+numFold).add(fvListTClinkR.get(idxListClinkR.get(idxClinkR)));
				idxClinkR ++;
			}
			if (idxCrule < fvListCrule.size()) {
				fvList.get(i).add(fvListCrule.get(idxCrule));
				fvList.get(i+numFold).add(fvListTCrule.get(idxCrule));
				idxCrule ++;
			}
		}
		
		System.out.println(idxCrule);
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventClinks(TXPParser txpParser, 
			String dirTxpPath, PairClassifier eeRelCls,
			boolean train, double threshold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		
		List<PairFeatureVector> fvListNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListCrule = new ArrayList<PairFeatureVector>();
		
		List<PairFeatureVector> fvListTNone = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTClink = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTClinkR = new ArrayList<PairFeatureVector>();
		List<PairFeatureVector> fvListTCrule = new ArrayList<PairFeatureVector>();
		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		String dirTML = "data/TempEval3-train_TML/";
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTML, txpFile.getName().replace(".txp", ""));
			List<List<PairFeatureVector>> fvListList = getEventEventClinksPerFile(txpParser, 
					txpFile, tmlParser, tmlFile, eeRelCls, train, threshold);
			
			fvListNone.addAll(fvListList.get(0));
			fvListClink.addAll(fvListList.get(1));
			fvListClinkR.addAll(fvListList.get(2));
			fvListCrule.addAll(fvListList.get(3));
			
			fvListTNone.addAll(fvListList.get(4));
			fvListTClink.addAll(fvListList.get(5));
			fvListTClinkR.addAll(fvListList.get(6));
			fvListTCrule.addAll(fvListList.get(7));
		}
		
		System.out.println("NONE: " + fvListNone.size() + ", CLINK: " + fvListClink.size() + ", CLINK-R: " + fvListClinkR.size() + ", CLINK (Rule): " + fvListCrule.size());
		
		fvListClink.addAll(fvListNone);
		fvListClink.addAll(fvListClinkR);
		fvListClink.addAll(fvListCrule);
		fvList.add(fvListClink);
		
		fvListTClink.addAll(fvListTNone);
		fvListTClink.addAll(fvListTClinkR);
		fvListTClink.addAll(fvListTCrule);
		fvList.add(fvListTClink);
		
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
		
		CausalTimeBankTaskExperiments4 task = new CausalTimeBankTaskExperiments4();
		
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
//		PairClassifier eeCls = new EventEventCausalClassifier("causal", "yamcha");
//		EventEventCausalClassifier eeCls = new EventEventCausalClassifier("causal", "liblinear");
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("te3-causal", "liblinear");
		
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
//			List<List<PairFeatureVector>> fvListList = 
//					task.getEventEventClinks(txpParser, txpDirpath, 
//							eeCls, true, numFold, threshold);
			
			List<List<PairFeatureVector>> fvListList = 
					task.getEventEventTlinks(txpParser, txpDirpath, 
							eeCls, false, true, numFold);

			for (int fold=0; fold<numFold; fold++) {
				
				System.err.println("Fold " + (fold+1) + "...");
				
				List<String> eeTestList = new ArrayList<String>();
				List<PairFeatureVector> evalFvList = new ArrayList<PairFeatureVector>();
				for (PairFeatureVector fv : fvListList.get(fold)) {
					evalFvList.add(fv);
				}
				
				List<String> eeTestCList = new ArrayList<String>();
				List<PairFeatureVector> evalFvCList = new ArrayList<PairFeatureVector>();
				for (PairFeatureVector fv : fvListList.get(fold+numFold)) {
					evalFvCList.add(fv);
				}
				
				List<PairFeatureVector> trainFvList = new ArrayList<PairFeatureVector>();
				for (int n=0; n<numFold; n++) {
					if (n != fold) {
						for (PairFeatureVector fv : fvListList.get(n)) {
							if (fv.getLabel().equals("IBEFORE")) {
								fv.setLabel("BEFORE");
							} else if (fv.getLabel().equals("IAFTER")) {
								fv.setLabel("AFTER");
							} else if (fv.getLabel().equals("DURING")) {
								fv.setLabel("SIMULTANEOUS");
							} else if (fv.getLabel().equals("DURING_INV")) {
								fv.setLabel("SIMULTANEOUS");
							} 
							trainFvList.add(fv);
						}
					}
				}
				
				List<PairFeatureVector> trainFvCList = new ArrayList<PairFeatureVector>();
				for (int n=0; n<numFold; n++) {
					if (n != fold) {
						for (PairFeatureVector fv : fvListList.get(n+numFold)) {
							if (fv.getLabel().equals("IBEFORE")) {
								fv.setLabel("BEFORE");
							} else if (fv.getLabel().equals("IAFTER")) {
								fv.setLabel("AFTER");
							} else if (fv.getLabel().equals("DURING")) {
								fv.setLabel("SIMULTANEOUS");
							} else if (fv.getLabel().equals("DURING_INV")) {
								fv.setLabel("SIMULTANEOUS");
							} 
							trainFvCList.add(fv);
						}
					}
				}
				
//				BufferedWriter bwTrain = new BufferedWriter(new FileWriter("./data/train-ee-fold"+(fold+1)+".data"));
//				bwTrain.write(eeCls.printFeatureVector(trainFvList));
//				BufferedWriter bwEval = new BufferedWriter(new FileWriter("./data/eval-ee-fold"+(fold+1)+".data"));
//				bwEval.write(eeCls.printFeatureVector(evalFvList));
				
				if (eeCls.classifier.equals(VectorClassifier.liblinear)) {
					eeCls.train(trainFvList, "models/" + task.name + ".model");
					eeCls.train(trainFvCList, "models/" + task.name + "-clink.model");
					
					List<String> eeClsTest = eeCls.predict(evalFvList, "models/" + task.name + ".model");
					for (int i=0; i<evalFvList.size(); i++) {
						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
						eeTestList.add(eefv.getE1().getID() 
								+ "\t" + eefv.getE2().getID()
								+ "\t" + eefv.getLabel()
								+ "\t" + eeClsTest.get(i));
					}
					
					List<String> eeClsTestC = eeCls.predict(evalFvCList, "models/" + task.name + "-clink.model");
					for (int i=0; i<evalFvCList.size(); i++) {
						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvCList.get(i)); 
						eeTestCList.add(eefv.getE1().getID() 
								+ "\t" + eefv.getE2().getID()
								+ "\t" + eefv.getLabel()
								+ "\t" + eeClsTestC.get(i));
					}
					
				} 
				
				//Evaluate
				PairEvaluator pee = new PairEvaluator(eeTestList);
				pee.evaluatePerLabel(task.label);
				
				PairEvaluator peec = new PairEvaluator(eeTestCList);
				peec.evaluatePerLabel(task.label);
			}

//		}
	}
}
