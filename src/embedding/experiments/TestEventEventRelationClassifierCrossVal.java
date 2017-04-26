package embedding.experiments;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
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

public class TestEventEventRelationClassifierCrossVal {
	
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
	
	public TestEventEventRelationClassifierCrossVal() throws IOException {
		name = "te3-embedding";
		
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		features = new ArrayList<String>();
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
						if (train) {
							eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						}
						else {
							eefv.addBinaryFeatureToVector(FeatureName.label);
						}
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						if (train) {
							eefv.addToVector(FeatureName.labelCollapsed);
						}
						else {
							eefv.addToVector(FeatureName.label);
						}
					}
					
					if (eefv.getLabel().equals("BEFORE")) {
						fvListBefore.add(eefv);
					}
					else if (eefv.getLabel().equals("AFTER")) {
						fvListAfter.add(eefv);
					}
					else if (eefv.getLabel().equals("IBEFORE")) {
						fvListBefore.add(eefv);
					}
					else if (eefv.getLabel().equals("IAFTER")) {
						fvListAfter.add(eefv);
					}
					else if (eefv.getLabel().equals("IDENTITY")) {
						fvListIdentity.add(eefv);
					}
					else if (eefv.getLabel().equals("SIMULTANEOUS")) {
						fvListSimultaneous.add(eefv);
					}
					else if (eefv.getLabel().equals("DURING")) {
						fvListSimultaneous.add(eefv);
					}
					else if (eefv.getLabel().equals("DURING_INV")) {
						fvListSimultaneous.add(eefv);
					}
					else if (eefv.getLabel().equals("INCLUDES")) {
						fvListIncludes.add(eefv);
					}
					else if (eefv.getLabel().equals("IS_INCLUDED")) {
						fvListIsIncluded.add(eefv);
					}
					else if (eefv.getLabel().equals("BEGINS")) {
						fvListBegins.add(eefv);
					}
					else if (eefv.getLabel().equals("BEGUN_BY")) {
						fvListBegunBy.add(eefv);
					}
					else if (eefv.getLabel().equals("ENDS")) {
						fvListEnds.add(eefv);
					}
					else if (eefv.getLabel().equals("ENDED_BY")) {
						fvListEndedBy.add(eefv);
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
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventTlinks(TXPParser txpParser, String dirTxpPath, PairClassifier eeRelCls,
			boolean train, boolean goldCandidate, int numFold) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		for(int i=0; i<numFold; i++) fvList.add(new ArrayList<PairFeatureVector>());
		
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
				idxBefore ++;
			}
			for (int j=0; j<numAfterPerFold; j++) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			for (int j=0; j<numIdentityPerFold; j++) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			for (int j=0; j<numSimultaneousPerFold; j++) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			for (int j=0; j<numIncludesPerFold; j++) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			for (int j=0; j<numIsIncludedPerFold; j++) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			for (int j=0; j<numBeginsPerFold; j++) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));
				idxBegins ++;
			}
			for (int j=0; j<numBegunByPerFold; j++) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			for (int j=0; j<numEndsPerFold; j++) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));;
				idxEnds ++;
			}
			for (int j=0; j<numEndedByPerFold; j++) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
			}
		}
		for (int i=0; i<numFold; i++) {
			if (idxBefore < fvListBefore.size()) {
				fvList.get(i).add(fvListBefore.get(idxListBefore.get(idxBefore)));
				idxBefore ++;
			}
			if (idxAfter < fvListAfter.size()) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			if (idxIdentity < fvListIdentity.size()) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			if (idxSimultaneous < fvListSimultaneous.size()) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			if (idxIncludes < fvListIncludes.size()) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			if (idxIsIncluded < fvListIsIncluded.size()) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			if (idxBegins < fvListBegins.size()) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));;
				idxBegins ++;
			}
			if (idxBegunBy < fvListBegunBy.size()) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			if (idxEnds < fvListEnds.size()) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));
				idxEnds ++;
			}
			if (idxEndedBy < fvListEndedBy.size()) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
			}
		}
		
		return fvList;
	}
	
	public List<List<PairFeatureVector>> getEventEventTlinks(String embeddingFilePath, String labelFilePath,
			int numFold) throws Exception {
		
		List<List<PairFeatureVector>> fvList = new ArrayList<List<PairFeatureVector>>();
		for(int i=0; i<numFold; i++) fvList.add(new ArrayList<PairFeatureVector>());
		
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
		
		System.setProperty("line.separator", "\n");
		BufferedReader bremb = new BufferedReader(new FileReader(embeddingFilePath));
		BufferedReader brlbl = new BufferedReader(new FileReader(labelFilePath));
		String line, lbl;
		while ((line = bremb.readLine()) != null) {
			lbl = brlbl.readLine();
	    	String[] cols = line.split(",");
	    	
	    	PairFeatureVector fv = new PairFeatureVector(null, null, null, lbl, tsignalList, csignalList);
	    	for (String s : cols) fv.getVectors().add(s);
	    	fv.getVectors().add(String.valueOf(labelList.indexOf(lbl)+1));
	    	
	    	switch(lbl) {
	    		case "BEFORE": fvListBefore.add(fv); break;
	    		case "AFTER": fvListAfter.add(fv); break;
	    		case "IDENTITY": fvListIdentity.add(fv); break;
	    		case "SIMULTANEOUS": fvListSimultaneous.add(fv); break;
	    		case "INCLUDES": fvListIncludes.add(fv); break;
	    		case "IS_INCLUDED": fvListIsIncluded.add(fv); break;
	    		case "BEGINS": fvListBegins.add(fv); break;
	    		case "BEGUN_BY": fvListBegunBy.add(fv); break;
	    		case "ENDS": fvListEnds.add(fv); break;
	    		case "ENDED_BY": fvListEndedBy.add(fv); break;
	    	}    
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
				idxBefore ++;
			}
			for (int j=0; j<numAfterPerFold; j++) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			for (int j=0; j<numIdentityPerFold; j++) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			for (int j=0; j<numSimultaneousPerFold; j++) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			for (int j=0; j<numIncludesPerFold; j++) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			for (int j=0; j<numIsIncludedPerFold; j++) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			for (int j=0; j<numBeginsPerFold; j++) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));
				idxBegins ++;
			}
			for (int j=0; j<numBegunByPerFold; j++) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			for (int j=0; j<numEndsPerFold; j++) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));;
				idxEnds ++;
			}
			for (int j=0; j<numEndedByPerFold; j++) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
			}
		}
		for (int i=0; i<numFold; i++) {
			if (idxBefore < fvListBefore.size()) {
				fvList.get(i).add(fvListBefore.get(idxListBefore.get(idxBefore)));
				idxBefore ++;
			}
			if (idxAfter < fvListAfter.size()) {
				fvList.get(i).add(fvListAfter.get(idxListAfter.get(idxAfter)));
				idxAfter ++;
			}
			if (idxIdentity < fvListIdentity.size()) {
				fvList.get(i).add(fvListIdentity.get(idxListIdentity.get(idxIdentity)));
				idxIdentity ++;
			}
			if (idxSimultaneous < fvListSimultaneous.size()) {
				fvList.get(i).add(fvListSimultaneous.get(idxListSimultaneous.get(idxSimultaneous)));
				idxSimultaneous ++;
			}
			if (idxIncludes < fvListIncludes.size()) {
				fvList.get(i).add(fvListIncludes.get(idxListIncludes.get(idxIncludes)));
				idxIncludes ++;
			}
			if (idxIsIncluded < fvListIsIncluded.size()) {
				fvList.get(i).add(fvListIsIncluded.get(idxListIsIncluded.get(idxIsIncluded)));
				idxIsIncluded ++;
			}
			if (idxBegins < fvListBegins.size()) {
				fvList.get(i).add(fvListBegins.get(idxListBegins.get(idxBegins)));;
				idxBegins ++;
			}
			if (idxBegunBy < fvListBegunBy.size()) {
				fvList.get(i).add(fvListBegunBy.get(idxListBegunBy.get(idxBegunBy)));
				idxBegunBy ++;
			}
			if (idxEnds < fvListEnds.size()) {
				fvList.get(i).add(fvListEnds.get(idxListEnds.get(idxEnds)));
				idxEnds ++;
			}
			if (idxEndedBy < fvListEndedBy.size()) {
				fvList.get(i).add(fvListEndedBy.get(idxListEndedBy.get(idxEndedBy)));
				idxEndedBy ++;
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
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		
		TestEventEventRelationClassifierCrossVal task = new TestEventEventRelationClassifierCrossVal();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String trainTxpDirpath = "./data/TempEval3-train_TXP2/";
		String trainTmlDirpath = "./data/TempEval3-train_TML/";
		
		String evalTxpDirpath = "./data/TempEval3-eval_TXP/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
		//Init classifiers
		EventEventRelationClassifier eeCls = new EventEventRelationClassifier("te3", "logit");
		
		int numFold = 10;
			
		//Extract feature vectors 
//		List<List<PairFeatureVector>> fvListList = 
//				task.getEventEventTlinks(txpParser, trainTxpDirpath, 
//						eeCls, false, true, numFold);
		
		List<List<PairFeatureVector>> fvListList = 
				task.getEventEventTlinks("./data/embedding/te3-ee-train-embedding-word2vec-300.exp0-features.csv", 
						"./data/embedding/te3-ee-train-labels-str.gr1.csv", 
						numFold);

		for (int fold=0; fold<numFold; fold++) {
			
			System.err.println("Fold " + (fold+1) + "...");
			
			List<String> eeTestList = new ArrayList<String>();
			List<PairFeatureVector> evalFvList = new ArrayList<PairFeatureVector>();
			for (PairFeatureVector fv : fvListList.get(fold)) {
				evalFvList.add(fv);
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
			
//				BufferedWriter bwTrain = new BufferedWriter(new FileWriter("./data/train-ee-fold"+(fold+1)+".data"));
//				bwTrain.write(eeCls.printFeatureVector(trainFvList));
//				BufferedWriter bwEval = new BufferedWriter(new FileWriter("./data/eval-ee-fold"+(fold+1)+".data"));
//				bwEval.write(eeCls.printFeatureVector(evalFvList));
			
			if (eeCls.classifier.equals(VectorClassifier.liblinear)
					|| eeCls.classifier.equals(VectorClassifier.logit)) {
				eeCls.train(trainFvList, "models/" + task.name + ".model");
				
				List<String> eeClsTest = eeCls.predict(evalFvList, "models/" + task.name + ".model");
				for (int i=0; i<evalFvList.size(); i++) {
					if (evalFvList.get(i).getE1() == null && evalFvList.get(i).getE2() == null) {
						eeTestList.add("-" 
								+ "\t" + "-"
								+ "\t" + evalFvList.get(i).getLabel()
								+ "\t" + eeClsTest.get(i));
					} else {
						EventEventFeatureVector eefv = new EventEventFeatureVector(evalFvList.get(i)); 
						eeTestList.add(eefv.getE1().getID() 
								+ "\t" + eefv.getE2().getID()
								+ "\t" + eefv.getLabel()
								+ "\t" + eeClsTest.get(i));
					}
					
				}
				
			} 
			
			//Evaluate
			PairEvaluator pee = new PairEvaluator(eeTestList);
			pee.evaluatePerLabel(task.label);
		}
	}
}
