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


public class TempEval3Task {
	
	private ArrayList<String> eeFeatures;
	private ArrayList<String> etFeatures;
	private String name;
	private String trainTXPPath;
	private String trainTMLPath;
	private String evalTXPPath;
	private String evalTMLPath;
	private String systemTMLPath;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	
	public TempEval3Task() throws IOException {
		name = "te3";
		trainTXPPath = "data/TempEval3-train_TXP";
		trainTMLPath = "data/TempEval3-train_TML";
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
		
		eeFeatures = new ArrayList<String>();
		etFeatures = new ArrayList<String>();
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
	
	public void getFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File file, StringBuilder ee, StringBuilder et, StringBuilder tt, 
			StringBuilder eeCoref, StringBuilder etRule) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(file.getPath());
		String tmlPath = file.getPath().replace("TXP", "TML");
		tmlPath = tmlPath.replace(".txp", "");
		Doc docTml = tmlParser.parseDocument(tmlPath);
		
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<Pair<String,String>,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID()) &&
					docTxp.getEntities().containsKey(tlink.getSourceID()) &&
					docTxp.getEntities().containsKey(tlink.getTargetID()) &&
					!tlink.getRelType().equals("NONE")) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}
				fv.addToVector(Feature.id);
				
				//token attribute features
				fv.addToVector(Feature.token);
				fv.addToVector(Feature.lemma);
				fv.addToVector(Feature.pos);
				fv.addToVector(Feature.mainpos);
				fv.addToVector(Feature.chunk);
				//fv.addToVector(Feature.ner);
				fv.addToVector(Feature.samePos);
				fv.addToVector(Feature.sameMainPos);
				
				//context features
				fv.addToVector(Feature.entDistance);
				fv.addToVector(Feature.sentDistance);
				
				if (fv instanceof EventEventFeatureVector) {
					//Entity attributes
					fv.addToVector(Feature.eventClass);
					//fv.addToVector(Feature.tenseAspect);
					fv.addToVector(Feature.tense);
					fv.addToVector(Feature.aspect);
					fv.addToVector(Feature.polarity);
					fv.addToVector(Feature.sameEventClass);
					fv.addToVector(Feature.sameTense);
					fv.addToVector(Feature.sameAspect);
					fv.addToVector(Feature.samePolarity);
					
					//dependency information
					fv.addToVector(Feature.depPath);
					fv.addToVector(Feature.mainVerb);
					
					//temporal connective/signal
					fv.addToVector(Feature.tempMarker);
					
					//causal connective/signal/verb
					fv.addToVector(Feature.causMarker);
					
					//event co-reference
					fv.addToVector(Feature.coref);
					
					//WordNet similarity
					fv.addToVector(Feature.wnSim);
					
					fv.addToVector(Feature.label);
				} else if (fv instanceof EventTimexFeatureVector) {
					fv.addToVector(Feature.entOrder);
					
					//Entity attributes
					fv.addToVector(Feature.eventClassCombined);
					fv.addToVector(Feature.tenseCombined);
					fv.addToVector(Feature.aspectCombined);
					//fv.addToVector(Feature.polarityCombined);
					fv.addToVector(Feature.timexType);
					fv.addToVector(Feature.timexValueTemplate);
					//fv.addToVector(Feature.dct);	//no improv
					
					//dependency information
					fv.addToVector(Feature.depPath);
					//fv.addToVector(Feature.mainVerb);
					
					//temporal connective/signal
					fv.addToVector(Feature.tempMarker);
					
					//timex rule type
					fv.addToVector(Feature.timexRule);
					
					fv.addToVector(Feature.label);
				}
				
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					Pair<String,String> st = new Pair<String, String>(tlink.getSourceID(), tlink.getTargetID());
					Pair<String,String> ts = new Pair<String, String>(tlink.getTargetID(), tlink.getSourceID());
					if (ttlinks.containsKey(st)) {
						tt.append(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st) + "\n");
					} else if (ttlinks.containsKey(ts)) {
						tt.append(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)) + "\n");
					}
				} else if (fv instanceof EventEventFeatureVector) {
//					if (((EventEventFeatureVector) fv).isCoreference()) {
//						//skip event-event pairs with COREF for training, assign IDENTITY/SIMULTANEOUS directly
//						eeCoref.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
//								tlink.getRelType() + "\t" + "IDENTITY" + "\n");
//					} else {
						ee.append(fv.printVectors() + "\n");
//					}
				} else if (fv instanceof EventTimexFeatureVector) {
//					String timexRule = ((EventTimexFeatureVector) fv).getTimexRule();
//					if (!timexRule.equals("O")) {
//						//skip event-timex pairs identified using rules for training, assign the BEGUN_BY or ENDED_BY directly
//						if (timexRule.equals("TMX-BEGIN")) {
//							etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
//									tlink.getRelType() + "\t" + "BEGUN_BY" + "\n");
//						} else if (timexRule.equals("TMX-END")) {
//							etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
//									tlink.getRelType() + "\t" + "ENDED_BY" + "\n");
//						}
//					} else {
						et.append(fv.printVectors() + "\n");
//					}
				}
			}
		}
		ee.append("\n");
		et.append("\n");
	}
	
	public void getFeatureVector(TXPParser parser, TimeMLParser tmlParser, String filepath, 
			StringBuilder ee, StringBuilder et, StringBuilder tt, StringBuilder eeCoref, StringBuilder etRule) 
					throws Exception {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){				
				this.getFeatureVector(parser, tmlParser, file.getPath(), ee, et, tt, eeCoref, etRule);				
			} else if (file.isFile()) {				
				getFeatureVectorPerFile(parser, tmlParser, file, ee, et, tt, eeCoref, etRule);				
			}
		}		
	}
	
	public void train(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		System.out.println("Building training data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder tt  = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		getFeatureVector(txpParser, tmlParser, trainTXPPath, ee, et, tt, eeCoref, etRule);
		
		//Field/column titles of features
		eeFeatures.clear();
		etFeatures.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) eeFeatures.add(s);
		}
		for (String s : EventTimexFeatureVector.fields) {
			if (s!= null) etFeatures.add(s);
		}
		System.out.println("event-event features: " + String.join(",", eeFeatures));
		System.out.println("event-timex features: " + String.join(",", etFeatures));
		
		//For training, only ee and et are needed
		System.setProperty("line.separator", "\n");
		PrintWriter eePW = new PrintWriter("data/" + name + "-ee-train.tlinks", "UTF-8");
		PrintWriter etPW = new PrintWriter("data/" + name + "-et-train.tlinks", "UTF-8");
		eePW.write(ee.toString());
		etPW.write(et.toString());
		eePW.close();
		etPW.close();
		
		//Copy training data to server
		System.out.println("Copy training data...");
		File eeFile = new File("data/" + name + "-ee-train.tlinks");
		File etFile = new File("data/" + name + "-et-train.tlinks");
		File[] files = {eeFile, etFile};
		RemoteServer rs = new RemoteServer();
		rs.copyFiles(files, "data/");
		
		//Train models using YamCha + TinySVM
		System.out.println("Train models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		String cmdTrainEE = "make CORPUS=~/data/"+name+"-ee-train.tlinks "
				+ "MODEL=~/models/"+name+"-ee "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d4 -c1 -m 512\" train";
		String cmdTrainET = "make CORPUS=~/data/"+name+"-et-train.tlinks "
				+ "MODEL=~/models/"+name+"-et "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d3 -c1 -m 512\" train";
		rs.executeCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
		
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
	
	public void evaluate(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		System.out.println("Building testing data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder tt  = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		getFeatureVector(txpParser, tmlParser, evalTXPPath, ee, et, tt, eeCoref, etRule);
		
		//Field/column titles of features
		eeFeatures.clear();
		etFeatures.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) eeFeatures.add(s);
		}
		for (String s : EventTimexFeatureVector.fields) {
			if (s!= null) etFeatures.add(s);
		}
		
		//For training, only ee and et are needed
		System.setProperty("line.separator", "\n");
		PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval.tlinks", "UTF-8");
		PrintWriter etPW = new PrintWriter("data/" + name + "-et-eval.tlinks", "UTF-8");
		eePW.write(ee.toString());
		etPW.write(et.toString());
		eePW.close();
		etPW.close();		
		
		System.out.println("Copy testing data...");
		File eeFile = new File("data/" + name + "-ee-eval.tlinks");
		File etFile = new File("data/" + name + "-et-eval.tlinks");
		File[] files = {eeFile, etFile};
		RemoteServer rs = new RemoteServer();
		rs.copyFiles(files, "data/");
		
		System.out.println("Test models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		
		String cmdTestEE = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee.model"
				+ " < ~/data/"+name+"-ee-eval.tlinks "
				+ " | cut -f1,2," + (this.eeFeatures.size()) + "," + (this.eeFeatures.size()+1);
				//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
		String cmdTestET = "./usr/local/bin/yamcha -m ~/models/"+name+"-et.model"
				+ " < ~/data/"+name+"-et-eval.tlinks "
				+ " | cut -f1,2," + (this.etFeatures.size()) + "," + (this.etFeatures.size()+1);
				//+ " > ~/data/"+name+"-et-eval-tagged.tlinks";
		List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
		List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
		
		String[] eeCorefArr = eeCoref.toString().split("\\r?\\n");
		eeResult.addAll(Arrays.asList(eeCorefArr));
		
		String[] etRuleArr = etRule.toString().split("\\r?\\n");
		etResult.addAll(Arrays.asList(etRuleArr));
		
		String[] ttArr = tt.toString().split("\\r?\\n");
		List<String> ttResult = Arrays.asList(ttArr);
		
		System.out.println("Accuracy event-event: " + String.format( "%.2f", accuracy(eeResult)*100) + "%");
		System.out.println("Accuracy event-timex: " + String.format( "%.2f", accuracy(etResult)*100) + "%");
		System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%");
		
		rs.disconnect();
	}
	
	public void evaluateTE3(TXPParser txpParser, TimeMLParser tmlParser) throws Exception {
		File dir_TXP = new File(evalTXPPath);
		File[] files_TXP = dir_TXP.listFiles();
		
		RemoteServer rs = new RemoteServer();
		
		//(Delete if exist and) create gold/ and system/ directories in remote server
		rs.executeCommand("rm -rf ~/data/gold/ && mkdir ~/data/gold/");
		rs.executeCommand("rm -rf ~/data/system/ && mkdir ~/data/system/");
		
		File sysTmlPath;
		
		//For each file in the evaluation dataset
		for (File file : files_TXP) {
			if (file.isFile()) {	
				System.out.println("Test " + file.getName() + "...");
				
				StringBuilder ee = new StringBuilder();
				StringBuilder et = new StringBuilder();
				StringBuilder tt  = new StringBuilder();
				StringBuilder eeCoref = new StringBuilder();
				StringBuilder etRule = new StringBuilder();
				getFeatureVectorPerFile(txpParser, tmlParser, file, ee, et, tt, eeCoref, etRule);
				
				//Field/column titles of features
				eeFeatures.clear();
				etFeatures.clear();
				for (String s : EventEventFeatureVector.fields) {
					if (s!= null) eeFeatures.add(s);
				}
				for (String s : EventTimexFeatureVector.fields) {
					if (s!= null) etFeatures.add(s);
				}
				
				//For training, only ee and et are needed
				System.setProperty("line.separator", "\n");
				PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval.tlinks", "UTF-8");
				PrintWriter etPW = new PrintWriter("data/" + name + "-et-eval.tlinks", "UTF-8");
				eePW.write(ee.toString());
				etPW.write(et.toString());
				eePW.println();
				etPW.println();
				eePW.close();
				etPW.close();	
				
				File eeFile = new File("data/" + name + "-ee-eval.tlinks");
				File etFile = new File("data/" + name + "-et-eval.tlinks");
				File[] files = {eeFile, etFile};
				rs.copyFiles(files, "data/");
				
				String cmdCd = "cd tools/yamcha-0.33/";
				
				String cmdTestEE = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee.model"
						+ " < ~/data/"+name+"-ee-eval.tlinks "
						+ " | cut -f1,2," + (this.eeFeatures.size()) + "," + (this.eeFeatures.size()+1);
						//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
				String cmdTestET = "./usr/local/bin/yamcha -m ~/models/"+name+"-et.model"
						+ " < ~/data/"+name+"-et-eval.tlinks "
						+ " | cut -f1,2," + (this.etFeatures.size()) + "," + (this.etFeatures.size()+1);
						//+ " > ~/data/"+name+"-et-eval-tagged.tlinks";
				List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
				List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
				
				String[] eeCorefArr = eeCoref.toString().split("\\r?\\n");
				eeResult.addAll(Arrays.asList(eeCorefArr));
				
				String[] etRuleArr = etRule.toString().split("\\r?\\n");
				etResult.addAll(Arrays.asList(etRuleArr));
				
				String[] ttArr = tt.toString().split("\\r?\\n");
				List<String> ttResult = Arrays.asList(ttArr);
				
				//Write the TimeML document with new TLINKs
				Doc dTml = tmlParser.parseDocument(evalTMLPath + "/" + file.getName().replace(".txp", ""));
				TimeMLDoc tml = new TimeMLDoc(evalTMLPath + "/" + file.getName().replace(".txp", ""));
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
				
				sysTmlPath = new File(systemTMLPath + "/" + file.getName().replace(".txp", ""));
				PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
				sysTML.write(tml.toString());
				sysTML.close();
			}
		}
		rs.disconnect();
		
		TempEval3 te3 = new TempEval3(evalTMLPath, systemTMLPath);
		te3.evaluate();		
	}
	
	public static void main(String [] args) {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		TXPParser parser = new TXPParser(EntityEnum.Language.EN, fields);
		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		//dir_TXP <-- data/example_TXP
		try {
			TempEval3Task task = new TempEval3Task();
			task.train(parser, tmlParser);			
			
			task.evaluate(parser, tmlParser);
			//task.evaluateTE3(parser, tmlParser);
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SftpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSchException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

}
