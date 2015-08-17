import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

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
	
	public Map<Pair<String,String>,String> getCLINKs(Doc doc) {
		Map<Pair<String,String>,String> clinks = new HashMap<Pair<String,String>,String>();
		Pair<String,String> pair = null, pairInv = null;
		for (CausalRelation clink : doc.getClinks()) {
			pair = new Pair<String,String>(clink.getSourceID(), clink.getTargetID());
			pairInv = new Pair<String,String>(clink.getTargetID(), clink.getSourceID());
			clinks.put(pair, "CLINK");
			clinks.put(pairInv, "CLINK-R");
		}			
		return clinks;
	}
	
	public Map<Pair<String,String>,String> getCandidatePairs(Doc doc) {
		Map<Pair<String,String>,String> candidates = new HashMap<Pair<String,String>,String>();
		Map<Pair<String,String>,String> clinks = getCLINKs(doc);
		
		for (int s=0; s<doc.getSentenceArr().size()-1; s++) {
			Sentence s1 = doc.getSentences().get(doc.getSentenceArr().get(s));
			Sentence s2 = doc.getSentences().get(doc.getSentenceArr().get(s+1));
			
			//candidate pairs within the same sentence
			Entity e1, e2;
			Pair<String,String> pair = null;
			for (int i = 0; i < s1.getEntityArr().size()-1; i++) {
				for (int j = i+1; j < s1.getEntityArr().size(); j++) {
					e1 = doc.getEntities().get(s1.getEntityArr().get(i));
					e2 = doc.getEntities().get(s1.getEntityArr().get(j));
					if (e1 instanceof Event && e2 instanceof Event) {
						pair = new Pair<String,String>(e1.getID(), e2.getID());
						if (clinks.containsKey(pair)) {
							candidates.put(pair, clinks.get(pair));
						} else {
							candidates.put(pair, "NONE");
						}
					}
				}
			}
			for (int i = 0; i < s2.getEntityArr().size()-1; i++) {
				for (int j = i+1; j < s2.getEntityArr().size(); j++) {
					e1 = doc.getEntities().get(s2.getEntityArr().get(i));
					e2 = doc.getEntities().get(s2.getEntityArr().get(j));
					if (e1 instanceof Event && e2 instanceof Event) {
						pair = new Pair<String,String>(e1.getID(), e2.getID());
						if (clinks.containsKey(pair)) {
							candidates.put(pair, clinks.get(pair));
						} else {
							candidates.put(pair, "NONE");
						}
					}
				}
			}
			
			//candidate pairs in consecutive sentences
			for (int i = 0; i < s1.getEntityArr().size(); i++) {
				for (int j = 0; j < s2.getEntityArr().size(); j++) {
					e1 = doc.getEntities().get(s1.getEntityArr().get(i));
					e2 = doc.getEntities().get(s2.getEntityArr().get(j));
					if (e1 instanceof Event && e2 instanceof Event) {
						pair = new Pair<String,String>(e1.getID(), e2.getID());
						if (clinks.containsKey(pair)) {
							candidates.put(pair, clinks.get(pair));
						} else {
							candidates.put(pair, "NONE");
						}
					}
				}
			}
		}
		
		return candidates;
	}
	
	public void getFeatureVectorPerFile(TXPParser txpParser, File file, StringBuilder ee) 
			throws Exception {
		
		Doc docTxp = txpParser.parseDocument(file.getPath());
		Map<Pair<String,String>,String> candidates = getCandidatePairs(docTxp);
		
		for (Pair<String,String> clink : candidates.keySet()) {	//for every CLINK in TXP file: candidate pairs
			Entity e1 = docTxp.getEntities().get(clink.getKey());
			Entity e2 = docTxp.getEntities().get(clink.getValue());
			
			PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, candidates.get(clink), tsignalList, csignalList);	
			fv = new EventEventFeatureVector(fv);
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
			
			ee.append(fv.printVectors() + "\n");
		}
	}
	
	public void getFeatureVector(TXPParser parser, String filepath, StringBuilder ee) 
			throws Exception {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){				
				this.getFeatureVector(parser, file.getPath(), ee);				
			} else if (file.isFile()) {				
				getFeatureVectorPerFile(parser, file, ee);
				ee.append("\n");
			}
		}		
	}
	
	public void train(TXPParser txpParser) throws Exception {
		System.out.println("Building training data...");
		StringBuilder ee1 = new StringBuilder();
		StringBuilder ee2 = new StringBuilder();
		StringBuilder ee3 = new StringBuilder();
		StringBuilder ee4 = new StringBuilder();
		StringBuilder ee5 = new StringBuilder();
		getFeatureVector(txpParser, TXPPath + "-1", ee1);
		getFeatureVector(txpParser, TXPPath + "-2", ee2);
		getFeatureVector(txpParser, TXPPath + "-3", ee3);
		getFeatureVector(txpParser, TXPPath + "-4", ee4);
		getFeatureVector(txpParser, TXPPath + "-5", ee5);
		
		StringBuilder eetrain1 = new StringBuilder();
		StringBuilder eetrain2 = new StringBuilder();
		StringBuilder eetrain3 = new StringBuilder();
		StringBuilder eetrain4 = new StringBuilder();
		StringBuilder eetrain5 = new StringBuilder();
		eetrain1.append(ee2.toString()); eetrain1.append(ee3.toString()); eetrain1.append(ee4.toString()); eetrain1.append(ee5.toString());
		eetrain2.append(ee1.toString()); eetrain2.append(ee3.toString()); eetrain2.append(ee4.toString()); eetrain2.append(ee5.toString());
		eetrain3.append(ee1.toString()); eetrain3.append(ee2.toString()); eetrain3.append(ee4.toString()); eetrain3.append(ee5.toString());
		eetrain4.append(ee1.toString()); eetrain4.append(ee2.toString()); eetrain4.append(ee3.toString()); eetrain4.append(ee5.toString());
		eetrain5.append(ee1.toString()); eetrain5.append(ee2.toString()); eetrain5.append(ee3.toString()); eetrain5.append(ee4.toString());
		
		//Field/column titles of features
		features.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) features.add(s);
		}
		System.out.println("event-event features: " + String.join(",", features));
		
		//For training, only ee and et are needed
		System.setProperty("line.separator", "\n");
		PrintWriter eePW = new PrintWriter("data/" + name + "-ee-train-1.tlinks", "UTF-8");
		eePW.write(eetrain1.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-train-2.tlinks", "UTF-8");
		eePW.write(eetrain2.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-train-3.tlinks", "UTF-8");
		eePW.write(eetrain3.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-train-4.tlinks", "UTF-8");
		eePW.write(eetrain4.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-train-5.tlinks", "UTF-8");
		eePW.write(eetrain5.toString());
		eePW.close();
		
		//Copy training data to server
		System.out.println("Copy training data...");
		File eeFile1 = new File("data/" + name + "-ee-train-1.tlinks");
		File eeFile2 = new File("data/" + name + "-ee-train-2.tlinks");
		File eeFile3 = new File("data/" + name + "-ee-train-3.tlinks");
		File eeFile4 = new File("data/" + name + "-ee-train-4.tlinks");
		File eeFile5 = new File("data/" + name + "-ee-train-5.tlinks");
		RemoteServer rs = new RemoteServer();
		rs.copyFile(eeFile1, "data/");
		rs.copyFile(eeFile2, "data/");
		rs.copyFile(eeFile3, "data/");
		rs.copyFile(eeFile4, "data/");
		rs.copyFile(eeFile5, "data/");
		
		//Train models using YamCha + TinySVM
		System.out.println("Train models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		String cmdTrainEE1 = "make CORPUS=~/data/"+name+"-ee-train-1.tlinks "
				+ "MODEL=~/models/"+name+"-ee-1 "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		String cmdTrainEE2 = "make CORPUS=~/data/"+name+"-ee-train-2.tlinks "
				+ "MODEL=~/models/"+name+"-ee-2 "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		String cmdTrainEE3 = "make CORPUS=~/data/"+name+"-ee-train-3.tlinks "
				+ "MODEL=~/models/"+name+"-ee-3 "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		String cmdTrainEE4 = "make CORPUS=~/data/"+name+"-ee-train-4.tlinks "
				+ "MODEL=~/models/"+name+"-ee-4 "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		String cmdTrainEE5 = "make CORPUS=~/data/"+name+"-ee-train-5.tlinks "
				+ "MODEL=~/models/"+name+"-ee-5 "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		rs.executeCommand(cmdCd + " && " + cmdTrainEE1 + " && " + cmdTrainEE2 + " && " + cmdTrainEE3 + " && " + cmdTrainEE4 + " && " + cmdTrainEE5);
		
		rs.disconnect();
	}
	
	public void evaluate(TXPParser txpParser) throws Exception {
		System.out.println("Building testing data...");
		StringBuilder ee1 = new StringBuilder();
		StringBuilder ee2 = new StringBuilder();
		StringBuilder ee3 = new StringBuilder();
		StringBuilder ee4 = new StringBuilder();
		StringBuilder ee5 = new StringBuilder();
		getFeatureVector(txpParser, TXPPath + "-1", ee1);
		getFeatureVector(txpParser, TXPPath + "-2", ee2);
		getFeatureVector(txpParser, TXPPath + "-3", ee3);
		getFeatureVector(txpParser, TXPPath + "-4", ee4);
		getFeatureVector(txpParser, TXPPath + "-5", ee5);
		
		//Field/column titles of features
		features.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) features.add(s);
		}
		
		//For training, only ee and et are needed
		System.setProperty("line.separator", "\n");
		PrintWriter eePW = new PrintWriter("data/" + name + "-ee-eval-1.tlinks", "UTF-8");
		eePW.write(ee1.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-eval-2.tlinks", "UTF-8");
		eePW.write(ee2.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-eval-3.tlinks", "UTF-8");
		eePW.write(ee3.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-eval-4.tlinks", "UTF-8");
		eePW.write(ee4.toString());
		eePW.close();
		eePW = new PrintWriter("data/" + name + "-ee-eval-5.tlinks", "UTF-8");
		eePW.write(ee5.toString());
		eePW.close();	
		
		//Copy training data to server
		System.out.println("Copy training data...");
		File eeFile1 = new File("data/" + name + "-ee-eval-1.tlinks");
		File eeFile2 = new File("data/" + name + "-ee-eval-2.tlinks");
		File eeFile3 = new File("data/" + name + "-ee-eval-3.tlinks");
		File eeFile4 = new File("data/" + name + "-ee-eval-4.tlinks");
		File eeFile5 = new File("data/" + name + "-ee-eval-5.tlinks");
		RemoteServer rs = new RemoteServer();
		rs.copyFile(eeFile1, "data/");
		rs.copyFile(eeFile2, "data/");
		rs.copyFile(eeFile3, "data/");
		rs.copyFile(eeFile4, "data/");
		rs.copyFile(eeFile5, "data/");
		
		System.out.println("Test models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		
		String cmdTestEE1 = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee-1.model"
				+ " < ~/data/"+name+"-ee-eval-1.tlinks "
				+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
		String cmdTestEE2 = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee-2.model"
				+ " < ~/data/"+name+"-ee-eval-2.tlinks "
				+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
		String cmdTestEE3 = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee-3.model"
				+ " < ~/data/"+name+"-ee-eval-3.tlinks "
				+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
		String cmdTestEE4 = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee-4.model"
				+ " < ~/data/"+name+"-ee-eval-4.tlinks "
				+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
		String cmdTestEE5 = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee-5.model"
				+ " < ~/data/"+name+"-ee-eval-5.tlinks "
				+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
		List<String> eeResult1 = rs.executeCommand(cmdCd + " && " + cmdTestEE1);
		List<String> eeResult2 = rs.executeCommand(cmdCd + " && " + cmdTestEE2);
		List<String> eeResult3 = rs.executeCommand(cmdCd + " && " + cmdTestEE3);
		List<String> eeResult4 = rs.executeCommand(cmdCd + " && " + cmdTestEE4);
		List<String> eeResult5 = rs.executeCommand(cmdCd + " && " + cmdTestEE5);
		
		//TODO: evaluate per file
		
//		System.out.println("Accuracy event-event: " + String.format( "%.2f", accuracy(eeResult)*100) + "%");
//		System.out.println("Accuracy event-timex: " + String.format( "%.2f", accuracy(etResult)*100) + "%");
//		System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%");
		
		rs.disconnect();
	}
	
	public static void main(String [] args) {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.supersense, Field.ss_ner, Field.clink, Field.csignal};
		TXPParser parser = new TXPParser(EntityEnum.Language.EN, fields);
		
		//dir_TXP <-- data/example_TXP
		try {
			CausalTimeBankTask task = new CausalTimeBankTask();
			
			//task.train(parser);
			task.evaluate(parser);
			
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
