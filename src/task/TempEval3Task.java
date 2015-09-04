package task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
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

import evaluator.TempEval3;
import javafx.util.Pair;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.TimexTimexRelationRule;
import model.feature.FeatureEnum.Feature;
import model.feature.FeatureEnum.PairType;
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
import weka.classifiers.functions.LibSVM;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

class TempEval3Task {
	
	private String name;
	private String trainTXPPath;
	private String trainTMLPath;
	private String trainTMLDeducedPath;
	private String evalTXPPath;
	private String evalTMLPath;
	private String systemTMLPath;
	private TemporalSignalList tsignalList;
	private CausalSignalList csignalList;
	
	private static int numDeduced = 0;
	private static enum VectorClassifier {yamcha, libsvm, weka};
	private VectorClassifier classifier;
	
	private Classifier eeCls;
	private Classifier etCls;
	
	private ArrayList<String> eeFeatures;
	private ArrayList<String> etFeatures;
	
	public TempEval3Task() throws IOException {
		name = "te3";
		trainTXPPath = "data/TempEval3-train_TXP";
		trainTMLPath = "data/TempEval3-train_TML";
		trainTMLDeducedPath = "data/TempEval3-train_TML_deduced";
		evalTXPPath = "data/TempEval3-eval_TXP";
		evalTMLPath = "data/TempEval3-eval_TML";
		
		//TimeML directory for system result files
		systemTMLPath = "data/TempEval3-system_TML";
		File sysDir = new File(systemTMLPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
		
		//set the classifier
		classifier = VectorClassifier.weka;
		
		eeFeatures = new ArrayList<String>();
		etFeatures = new ArrayList<String>();
	}
	
	public static void main(String [] args) {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		//dir_TXP <-- data/example_TXP
		try {
			TempEval3Task task = new TempEval3Task();
			task.train(txpParser, tmlParser);
			//task.evaluate(txpParser, tmlParser);
			//task.evaluateTE3(txpParser, tmlParser);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public Map<Pair<String,String>,String> getTimexTimexRuleRelation(Doc doc) {
		Object[] entArr = doc.getEntities().keySet().toArray();
		Map<Pair<String,String>,String> ttlinks = new HashMap<Pair<String,String>,String>();
		Pair<String,String> pair = null;
		for (int i = 0; i < entArr.length; i++) {
			for (int j = i; j < entArr.length; j++) {
				if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
						doc.getEntities().get(entArr[j]) instanceof Timex) {
					TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
							((Timex)doc.getEntities().get(entArr[j])), doc.getDct());
					if (!timextimex.getRelType().equals("O")) {
						pair = new Pair<String,String>(((String) entArr[i]), ((String) entArr[j]));
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
		
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<Pair<String,String>,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (tlink.isDeduced()) numDeduced += 1;
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					Pair<String,String> st = new Pair<String, String>(tlink.getSourceID(), tlink.getTargetID());
					Pair<String,String> ts = new Pair<String, String>(tlink.getTargetID(), tlink.getSourceID());
					if (ttlinks.containsKey(st)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else if (ttlinks.containsKey(ts)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
					}
				}
			}
		}
		return tt;
	}
	
	public List<String> getTimexTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath) throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();
		List<String> tt = new ArrayList<String>();
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			tt.addAll(getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile));
		}		
		return tt;
	}
	
	public void getPairIDPerFile (TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, StringBuilder ee, StringBuilder et,
			StringBuilder eeCoref, StringBuilder etRule) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (tlink.isDeduced()) numDeduced += 1;
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}				
				
				if (fv instanceof EventEventFeatureVector) {
					if (eeCoref != null) {
						if (((EventEventFeatureVector) fv).isCoreference()) {
							//skip event-event pairs with COREF for training, assign IDENTITY/SIMULTANEOUS directly
							eeCoref.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\t" + "IDENTITY" + "\n");
						} else {
							ee.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\n");
						}
					} else {
						ee.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								tlink.getRelType() + "\n");
					}
				} else if (fv instanceof EventTimexFeatureVector) {
					if (etRule != null) {
						String timexRule = ((EventTimexFeatureVector) fv).getTimexRule();
						if (!timexRule.equals("O")) {
							//skip event-timex pairs identified using rules for training, assign the BEGUN_BY or ENDED_BY directly
							if (timexRule.equals("TMX-BEGIN")) {
								etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
										tlink.getRelType() + "\t" + "BEGUN_BY" + "\n");
							} else if (timexRule.equals("TMX-END")) {
								etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
										tlink.getRelType() + "\t" + "ENDED_BY" + "\n");
							}
						} else {
							et.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\n");
						}
					} else {
						et.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								tlink.getRelType() + "\n");
					}
				}
			}
		}
	}
	
	public void printFeatureVector(StringBuilder pair, PairFeatureVector fv) {
		if (classifier.equals(VectorClassifier.libsvm)) {
			pair.append(fv.printLibSVMVectors() + "\n");
		} else if (classifier.equals(VectorClassifier.weka)) {
			pair.append(fv.printCSVVectors() + "\n");
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			pair.append(fv.printVectors() + "\n");
		}
	}
	
	public void printEventEventFeatureVector(List<PairFeatureVector> vectors, StringBuilder ee, StringBuilder eeCoref) {
		for (PairFeatureVector fv : vectors) {
			if (eeCoref != null) {
				if (((EventEventFeatureVector) fv).isCoreference()) {
					//skip event-event pairs with COREF for training, assign IDENTITY/SIMULTANEOUS directly
					eeCoref.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
							fv.getLabel() + "\t" + "IDENTITY" + "\n");
				} else {
					printFeatureVector(ee, fv);
				}
			} else {
				printFeatureVector(ee, fv);
			}
		}
		if (classifier.equals(VectorClassifier.yamcha)) ee.append("\n");
	}
	
	public void printEventTimexFeatureVector(List<PairFeatureVector> vectors, StringBuilder et, StringBuilder etRule) {
		for (PairFeatureVector fv : vectors) {
			if (etRule != null) {
				String timexRule = ((EventTimexFeatureVector) fv).getTimexRule();
				if (!timexRule.equals("O")) {
					//skip event-timex pairs identified using rules for training, assign the BEGUN_BY or ENDED_BY directly
					if (timexRule.equals("TMX-BEGIN")) {
						etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								fv.getLabel() + "\t" + "BEGUN_BY" + "\n");
					} else if (timexRule.equals("TMX-END")) {
						etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								fv.getLabel() + "\t" + "ENDED_BY" + "\n");
					}
				} else {
					printFeatureVector(et, fv);
				}
			} else {
				printFeatureVector(et, fv);
			}
		}
		if (classifier.equals(VectorClassifier.yamcha)) et.append("\n");
	}
	
	public void getEventEventFeatureVector(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath,
			StringBuilder ee, StringBuilder eeCoref) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			List<PairFeatureVector> vectors = getEventEventFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
			//Field/column titles of features
			eeFeatures.clear();
			for (String s : EventEventFeatureVector.fields) {
				if (s!= null) eeFeatures.add(s);
			}
			printEventEventFeatureVector(vectors, ee, eeCoref);
		}	
	}
	
	public void getEventTimexFeatureVector(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath,
			StringBuilder et, StringBuilder etRule) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			List<PairFeatureVector> vectors = getEventTimexFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
			etFeatures.clear();
			for (String s : EventTimexFeatureVector.fields) {
				if (s!= null) etFeatures.add(s);
			}
			printEventTimexFeatureVector(vectors, et, etRule);
		}	
	}
	
	public List<PairFeatureVector> getEventEventFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		List<PairFeatureVector> vectors = new ArrayList<PairFeatureVector>();
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (tlink.isDeduced()) numDeduced += 1;
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}
				
//				fv.addBinaryFeatureToVector(Feature.id);
				
//				//token attribute features
//				fv.addToVector(Feature.tokenSpace);
//				fv.addToVector(Feature.lemmaSpace);
//				fv.addToVector(Feature.tokenChunk);
				
				//TODO: get phrase embedding for token, lemma features
//				fv.addPhraseFeatureToVector(Feature.tokenSpace);
//				fv.addPhraseFeatureToVector(Feature.lemmaSpace);
//				fv.addPhraseFeatureToVector(Feature.tokenChunk);
				
				if (fv instanceof EventEventFeatureVector) {
					fv.addBinaryFeatureToVector(Feature.pos);
					//fv.addBinaryFeatureToVector(Feature.mainpos);
					fv.addBinaryFeatureToVector(Feature.chunk);
					fv.addBinaryFeatureToVector(Feature.samePos);
					//fv.addBinaryFeatureToVector(Feature.sameMainPos);
					
					//context features
					fv.addBinaryFeatureToVector(Feature.entDistance);
					fv.addBinaryFeatureToVector(Feature.sentDistance);
					
					//Entity attributes
					fv.addBinaryFeatureToVector(Feature.eventClass);
					fv.addBinaryFeatureToVector(Feature.tense);
					fv.addBinaryFeatureToVector(Feature.aspect);
					fv.addBinaryFeatureToVector(Feature.polarity);
					fv.addBinaryFeatureToVector(Feature.sameEventClass);
					fv.addBinaryFeatureToVector(Feature.sameTense);
					fv.addBinaryFeatureToVector(Feature.sameAspect);
					fv.addBinaryFeatureToVector(Feature.samePolarity);
					
					//dependency information
					//fv.addToVector(Feature.depPath);	//TODO dependency path to binary feature?
					fv.addBinaryFeatureToVector(Feature.mainVerb);
					
					//fv.addToVector(Feature.tempMarkerText);
					fv.addBinaryFeatureToVector(Feature.tempSignalClusText);
					//fv.addBinaryFeatureToVector(Feature.tempMarkerPos);
					//fv.addToVector(Feature.tempMarkerDep1Dep2);	//TODO dependency path to binary feature?
					
					//fv.addToVector(Feature.causMarkerText);
					fv.addBinaryFeatureToVector(Feature.causMarkerClusText);
					//fv.addBinaryFeatureToVector(Feature.causMarkerPos);
					//fv.addToVector(Feature.causMarkerDep1Dep2);	//TODO dependency path to binary feature?
					
					//TODO addToVector phrase embedding for temporal & causal signal
					//fv.addPhraseFeatureToVector(Feature.tempMarkerTextPhrase);
					//fv.addPhraseFeatureToVector(Feature.causMarkerTextPhrase);
					
					//event co-reference
					fv.addBinaryFeatureToVector(Feature.coref);
					
					//WordNet similarity
					fv.addBinaryFeatureToVector(Feature.wnSim);
					
					if (classifier.equals(VectorClassifier.libsvm)) {
						fv.addBinaryFeatureToVector(Feature.label);
					} else {
						fv.addToVector(Feature.label);
					}
					
					vectors.add(fv);
					
				}
			}
		}
		return vectors;
	}
	
	public List<PairFeatureVector> getEventTimexFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		List<PairFeatureVector> vectors = new ArrayList<PairFeatureVector>();
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (tlink.isDeduced()) numDeduced += 1;
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}
				
//				fv.addBinaryFeatureToVector(Feature.id);
				
//				//token attribute features
//				fv.addToVector(Feature.tokenSpace);
//				fv.addToVector(Feature.lemmaSpace);
//				fv.addToVector(Feature.tokenChunk);
				
				//TODO: get phrase embedding for token, lemma features
//				fv.addPhraseFeatureToVector(Feature.tokenSpace);
//				fv.addPhraseFeatureToVector(Feature.lemmaSpace);
//				fv.addPhraseFeatureToVector(Feature.tokenChunk);
				
				if (fv instanceof EventTimexFeatureVector) {
					fv.addBinaryFeatureToVector(Feature.pos);
					fv.addBinaryFeatureToVector(Feature.mainpos);
					fv.addBinaryFeatureToVector(Feature.chunk);
					fv.addBinaryFeatureToVector(Feature.samePos);
					fv.addBinaryFeatureToVector(Feature.sameMainPos);
					
					//context features
					fv.addBinaryFeatureToVector(Feature.entDistance);
					fv.addBinaryFeatureToVector(Feature.sentDistance);
					fv.addBinaryFeatureToVector(Feature.entOrder);
					
					//Entity attributes
					fv.addBinaryFeatureToVector(Feature.eventClass);
					fv.addBinaryFeatureToVector(Feature.tense);
					fv.addBinaryFeatureToVector(Feature.aspect);
					fv.addBinaryFeatureToVector(Feature.polarity);
					//fv.addBinaryFeatureToVector(Feature.timexType);
					
					//dependency information
					//fv.addToVector(Feature.depPath);	//TODO dependency path to binary feature?
					fv.addBinaryFeatureToVector(Feature.mainVerb);
					
					//TODO addToVector phrase embedding for temporal signal
					//fv.addToVector(Feature.tempMarkerText);
					//fv.addBinaryFeatureToVector(Feature.tempSignalClusText);
					//fv.addBinaryFeatureToVector(Feature.tempMarkerPos);
					//fv.addToVector(Feature.tempMarkerDep1Dep2);	//TODO dependency path to binary feature?
					
					//timex rule type
					fv.addBinaryFeatureToVector(Feature.timexRule);
					
					if (classifier.equals(VectorClassifier.libsvm)) {
						fv.addBinaryFeatureToVector(Feature.label);
					} else {
						fv.addToVector(Feature.label);
					}
					
					vectors.add(fv);
				}
			}
		}
		return vectors;
	}
	
	public void writeArffFile(PrintWriter eePW, PrintWriter etPW, StringBuilder eeVec, StringBuilder etVec) {
		//Header
		eePW.write("@relation " + name + "-ee\n\n");
		etPW.write("@relation " + name + "-et\n\n");
		
		//Field/column titles of features
		for (String s : eeFeatures) {
			if (s!= null) {
//				if (s.equals("entDistance") || s.equals("sentDistance")) {
//					eePW.write("@attribute " + s + " numeric\n");
//				} else if (s.equals("wnSim")) {
//					eePW.write("@attribute " + s + " numeric\n");
//				} else 
					if (s.equals("label")) {
					eePW.write("@attribute " + s + " {BEFORE, AFTER, IBEFORE, IAFTER, IDENTITY, SIMULTANEOUS, INCLUDES, IS_INCLUDED, DURING, DURING_INV, BEGINS, BEGUN_BY, ENDS, ENDED_BY}\n");
				} else {
					eePW.write("@attribute " + s + " {0,1}\n");
				}
			}
		}
		for (String s : etFeatures) {
			if (s!= null) {
//				if (s.equals("entDistance") || s.equals("sentDistance")) {
//					etPW.write("@attribute " + s + " numeric\n");
//				} else 
					if (s.equals("label")) {
					etPW.write("@attribute " + s + " {BEFORE, AFTER, IBEFORE, IAFTER, IDENTITY, SIMULTANEOUS, INCLUDES, IS_INCLUDED, DURING, DURING_INV, BEGINS, BEGUN_BY, ENDS, ENDED_BY}\n");
				} else {
					etPW.write("@attribute " + s + " {0,1}\n");
				}
			}
		}
		
		//Vectors
		eePW.write("\n@data\n");
		etPW.write("\n@data\n");
		eePW.write(eeVec.toString());
		etPW.write(etVec.toString());
		
		eePW.close();
		etPW.close();
	}
	
	public void train(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		System.out.println("Building training data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		
		getEventEventFeatureVector(txpParser, tmlParser, trainTXPPath, trainTMLPath, ee, null);
		getEventTimexFeatureVector(txpParser, tmlParser, trainTXPPath, trainTMLPath, et, null);
		
		//Field/column titles of features
		System.out.println("event-event features: " + String.join(",", eeFeatures));
		System.out.println("event-timex features: " + String.join(",", etFeatures));
		
		System.out.println("num deduced TLINKs: " + numDeduced);

		RemoteServer rs = new RemoteServer();
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.libsvm) || classifier.equals(VectorClassifier.yamcha)) {
			PrintWriter eePW = new PrintWriter("data/" + name + "-ee-train.data", "UTF-8");
			PrintWriter etPW = new PrintWriter("data/" + name + "-et-train.data", "UTF-8");		
			eePW.write(ee.toString());
			eePW.close();
			etPW.write(et.toString());
			etPW.close();
			
			//Copy training data to server
			System.out.println("Copy training data...");
			File eeFile = new File("data/" + name + "-ee-train.data");
			File etFile = new File("data/" + name + "-et-train.data");
			File[] files = {eeFile, etFile};
			rs.copyFiles(files, "data/");
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter eePW = new PrintWriter("data/" + name + "-ee-train.arff", "UTF-8");
			PrintWriter etPW = new PrintWriter("data/" + name + "-et-train.arff", "UTF-8");	
			writeArffFile(eePW, etPW, ee, et);
		}
		
		//Train models using LibSVM
		System.out.println("Train models...");
		if (classifier.equals(VectorClassifier.libsvm)) {
			String cmdCd = "cd tools/libsvm-3.20/";
			String cmdTrainEE = "./svm-train "
					+ "-s 0 -t 2 -d 3 -g 0.0 -r 0.0 -c 1 -n 0.5 -p 0.1 -m 128 -e 0.001 "
					+ "~/data/" + name + "-ee-train.data "
					+ "~/models/" + name + "-ee-svm.model";
			String cmdTrainET = "./svm-train "
					+ "-s 0 -t 2 -d 3 -g 0.0 -r 0.0 -c 1 -n 0.5 -p 0.1 -m 128 -e 0.001 "
					+ "~/data/" + name + "-et-train.data "
					+ "~/models/" + name + "-et-svm.model";
			
			rs.executeCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
		} else if (classifier.equals(VectorClassifier.weka)) {
			DataSource eeSource = new DataSource("data/" + name + "-ee-train.arff");
			Instances eeTrain = eeSource.getDataSet();
//			eeTrain.setClassIndex(eeFeatures.size() - 1); 
//			eeCls = new LibSVM();
//		    eeCls.buildClassifier(eeTrain);
		    
		    DataSource etSource = new DataSource("data/" + name + "-et-train.arff");
			Instances etTrain = etSource.getDataSet();
//			etTrain.setClassIndex(etFeatures.size() - 1);
//			etCls = new LibSVM();
//		    etCls.buildClassifier(etTrain);
		}
		
		rs.disconnect();
		
	}
	
	public double accuracy(List<String> pairs) {
		int eeCorrect = 0;
		int eeInstance = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				String[] cols = s.split("\t");
				if (cols[2].equals(cols[3])) eeCorrect += 1;
				eeInstance += 1;
			}
		}
		return (double)eeCorrect/(double)eeInstance;
	}
	
	public int numCorrect(List<String> pairs) {
		int eeCorrect = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				String[] cols = s.split("\t");
				if (cols[2].equals(cols[3])) eeCorrect += 1;
			}
		}
		return eeCorrect;
	}
	
	public int numInstance(List<String> pairs) {
		int eeInstance = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				eeInstance += 1;
			}
		}
		return eeInstance;
	}
	
	public void evaluate(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		System.out.println("Building testing data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder tt  = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		
		getEventEventFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, ee, null);
		getEventTimexFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, et, null);
		
		RemoteServer rs = new RemoteServer();
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.libsvm) || classifier.equals(VectorClassifier.yamcha)) {
			PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval.data", "UTF-8");
			PrintWriter etPW = new PrintWriter("data/" + name + "-et-eval.data", "UTF-8");		
			eePW.write(ee.toString());
			eePW.close();
			etPW.write(et.toString());
			etPW.close();
			
			//Copy training data to server
			System.out.println("Copy training data...");
			File eeFile = new File("data/" + name + "-ee-eval.data");
			File etFile = new File("data/" + name + "-et-eval.data");
			File[] files = {eeFile, etFile};
			rs.copyFiles(files, "data/");
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval.arff", "UTF-8");
			PrintWriter etPW = new PrintWriter("data/" + name + "-et-eval.arff", "UTF-8");	
			writeArffFile(eePW, etPW, ee, et);
		}
		
		System.out.println("Test models...");
		String eeAccuracy = "", etAccuracy = "";
		if (classifier.equals(VectorClassifier.libsvm)) {
			String cmdCd = "cd tools/libsvm-3.20/";		
			String cmdTestEE = "./svm-predict "
					+ "~/data/" + name + "-ee-eval.data "
					+ "~/models/" + name + "-ee-svm.model "
					+ "~/data/" + name + "-ee-eval.tagged";
			String cmdTestET = "./svm-predict "
					+ "~/data/" + name + "-et-eval.data "
					+ "~/models/" + name + "-et-svm.model "
					+ "~/data/" + name + "-et-eval.tagged";
			
			List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
			List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
			eeAccuracy = eeResult.get(0);
			etAccuracy = etResult.get(0);
		}
		
		List<String> ttResult = getTimexTimexTlinks(txpParser, tmlParser, evalTXPPath, evalTMLPath);

		System.out.println("Accuracy event-event: " + eeAccuracy);
		System.out.println("Accuracy event-timex: " + etAccuracy);
		System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%"
				+ " (" + numCorrect(ttResult) + "/" + numInstance(ttResult) + ")");
		
		rs.disconnect();
	}
	
	public String getLabelFromNum(String num) {
		String[] temp_rel_type = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> temp_rel_type_list = Arrays.asList(temp_rel_type);
		return temp_rel_type_list.get(Integer.valueOf(num)-1);
	}
	
	public void evaluateTE3(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		File[] txpFiles = new File(evalTXPPath).listFiles();
		
		RemoteServer rs = new RemoteServer();
		
		//(Delete if exist and) create gold/ and system/ directories in remote server
		rs.executeCommand("rm -rf ~/data/gold/ && mkdir ~/data/gold/");
		rs.executeCommand("rm -rf ~/data/system/ && mkdir ~/data/system/");
		
		File sysTmlPath;
		
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				System.out.println("Test " + txpFile.getName() + "...");
				File tmlFile = new File(evalTMLPath, txpFile.getName().replace(".txp", ""));
				
				StringBuilder ee = new StringBuilder();
				StringBuilder et = new StringBuilder();
				StringBuilder eeCoref = new StringBuilder();
				StringBuilder etRule = new StringBuilder();
				
				List<PairFeatureVector> eeVectors = getEventEventFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
				List<PairFeatureVector> etVectors = getEventTimexFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
				printEventEventFeatureVector(eeVectors, ee, null);
				printEventEventFeatureVector(etVectors, et, null);
				
				//For training, only ee and et are needed
				System.setProperty("line.separator", "\n");
				PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval.data", "UTF-8");
				PrintWriter etPW = new PrintWriter("data/" + name + "-et-eval.data", "UTF-8");
				eePW.write(ee.toString());
				etPW.write(et.toString());
				eePW.close();
				etPW.close();
				
				//Copy data to server
				File eeFile = new File("data/" + name + "-ee-eval.data");
				File etFile = new File("data/" + name + "-et-eval.data");
				File[] files = {eeFile, etFile};
				rs.copyFiles(files, "data/");
				
				String cmdCd = "cd tools/libsvm-3.20/";
				
				String cmdTestEE = "./svm-predict -q "
						+ "~/data/" + name + "-ee-eval.data "
						+ "~/models/" + name + "-ee-svm.model "
						+ "~/data/" + name + "-ee-eval.tagged";
				String cmdTestET = "./svm-predict -q "
						+ "~/data/" + name + "-et-eval.data "
						+ "~/models/" + name + "-et-svm.model "
						+ "~/data/" + name + "-et-eval.tagged";
				String cmdCatEE = "cat ~/data/" + name + "-ee-eval.tagged";
				String cmdCatET = "cat ~/data/" + name + "-et-eval.tagged";
				
				StringBuilder eePair = new StringBuilder();
				StringBuilder etPair = new StringBuilder();
				StringBuilder eeCorefPair = new StringBuilder();
				StringBuilder etRulePair = new StringBuilder();
				getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, eePair, etPair, null, null);
				
				List<String> eeLabel = rs.executeCommand(cmdCd + " && " + cmdTestEE + " && " + cmdCatEE);
				List<String> etLabel = rs.executeCommand(cmdCd + " && " + cmdTestET + " && " + cmdCatET);
				
				List<String> eeResult = new ArrayList<String>();
				List<String> etResult = new ArrayList<String>();
				int i = 0;
				for (String pair : eePair.toString().split("\n")) {
					eeResult.add(pair + "\t" + getLabelFromNum(eeLabel.get(i)));
					i += 1;
				}
				i = 0;
				for (String pair : etPair.toString().split("\n")) {
					etResult.add(pair + "\t" + getLabelFromNum(etLabel.get(i)));
					i += 1;
				}
				
				String[] eeCorefArr = eeCoref.toString().split("\\r?\\n");
				eeResult.addAll(Arrays.asList(eeCorefArr));
				
				String[] etRuleArr = etRule.toString().split("\\r?\\n");
				etResult.addAll(Arrays.asList(etRuleArr));
				
				List<String> ttResult = getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile);
				
				//Write the TimeML document with new TLINKs
				Doc dTml = tmlParser.parseDocument(tmlFile.getPath());
				TimeMLDoc tml = new TimeMLDoc(tmlFile.getPath());
				tml.removeLinks();
				
				int linkId = 1;
				TemporalRelation tlink = new TemporalRelation();
				for (String eeStr : eeResult) {
					if (!eeStr.isEmpty()) {
						String[] cols = eeStr.split("\t");
						tlink.setSourceID(dTml.getInstancesInv().get(cols[0]).replace("tmx", "t"));
						tlink.setTargetID(dTml.getInstancesInv().get(cols[1]).replace("tmx", "t"));
						tlink.setRelType(cols[3]);
						tlink.setSourceType("Event");
						tlink.setTargetType("Event");
						tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
						linkId += 1;
					}
				}
				for (String etStr : etResult) {
					if (!etStr.isEmpty()) {
						String[] cols = etStr.split("\t");
						tlink.setSourceID(dTml.getInstancesInv().get(cols[0]).replace("tmx", "t"));
						tlink.setTargetID(cols[1].replace("tmx", "t"));
						tlink.setRelType(cols[3]);
						tlink.setSourceType("Event");
						tlink.setTargetType("Timex");
						tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
						linkId += 1;
					}
				}
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
				
				sysTmlPath = new File(systemTMLPath + "/" + txpFile.getName().replace(".txp", ""));
				PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
				sysTML.write(tml.toString());
				sysTML.close();
			}
		}
		rs.disconnect();
		
		TempEval3 te3 = new TempEval3(evalTMLPath, systemTMLPath);
		te3.evaluate();		
	}

}
