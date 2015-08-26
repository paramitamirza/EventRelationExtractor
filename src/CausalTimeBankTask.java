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
		
		StringBuilder[] folds = new StringBuilder[5];
		for (int i=0; i<5; i++) {
			folds[i] = new StringBuilder();
			getFeatureVector(txpParser, TXPPath + "-" + String.valueOf(i+1), folds[i]);
		}
		
		StringBuilder[] trainFolds = new StringBuilder[5];
		for (int i=0; i<5; i++) {
			trainFolds[i] = new StringBuilder();
			for (int j=0; j<5; j++) {
				if (i != j) {
					trainFolds[i].append(folds[j].toString());
				}
			}
		}
		
		//Field/column titles of features
		features.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) features.add(s);
		}
		System.out.println("event-event features: " + String.join(",", features));
		
		//Print feature vectors to file
		System.setProperty("line.separator", "\n");
		PrintWriter eePW;
		for (int i=0; i<5; i++) {
			eePW = new PrintWriter("data/" + name + "-ee-train-" + String.valueOf(i+1) + ".tlinks", "UTF-8");
			eePW.write(trainFolds[i].toString());
			eePW.close();
		}
		
		//Copy training data to server
		System.out.println("Copy training data...");
		RemoteServer rs = new RemoteServer();
		File eeFile;
		for (int i=0; i<5; i++) {
			eeFile = new File("data/" + name + "-ee-train-" + String.valueOf(i+1) + ".tlinks");
			rs.copyFile(eeFile, "data/");
		}
		
		//Train models using YamCha + TinySVM
		System.out.println("Train models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		StringBuilder cmdTrain = new StringBuilder();
		for (int i=0; i<5; i++) {
			cmdTrain.append("make CORPUS=~/data/"+name+"-ee-train-"+String.valueOf(i+1)+".tlinks "
					+ "MODEL=~/models/"+name+"-ee-"+String.valueOf(i+1)+ " "
					+ "FEATURE=\"F:0:2..\" "
					+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train"
					+ " && ");
		}
		rs.executeCommand(cmdCd + " && " + cmdTrain);
		
		rs.disconnect();
	}
	
	public void evaluate(TXPParser txpParser) throws Exception {
		File dir_TXP;
		File[] files_TXP;
		StringBuilder ee;
		PrintWriter eePW;
		File eeFile;
		//RemoteServer rs = new RemoteServer();
		
		for (int i=0; i<5; i++) {
			dir_TXP = new File(TXPPath + "-" + String.valueOf(i+1));
			files_TXP = dir_TXP.listFiles();
			
			//For each file in the evaluation dataset
			for (File file : files_TXP) {
				if (file.isFile()) {	
					System.out.println("Test " + file.getName() + "...");
					ee = new StringBuilder();
					getFeatureVectorPerFile(txpParser, file, ee);
					
					System.setProperty("line.separator", "\n");
					eePW = new PrintWriter("data/" + name + "-ee-eval.tlinks", "UTF-8");
					eePW.write(ee.toString());
					System.out.println(ee.toString());
					eePW.close();
					
					eeFile = new File("data/" + name + "-ee-eval.tlinks");
//					rs.copyFile(eeFile, "data/");
//					
//					String cmdCd = "cd tools/yamcha-0.33/";					
//					String cmdTest = "./usr/local/bin/yamcha "
//							+ "-m ~/models/"+name+"-ee-"+String.valueOf(i+1)+".model"
//							+ " < ~/data/"+name+"-ee-eval.tlinks "
//							+ " | cut -f1,2," + (this.features.size()) + "," + (this.features.size()+1);
//					List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTest);
//					
//					for (String s : eeResult) {
//						System.out.println(s);
//					}
					
					//TODO evaluate eeResult compared with annotated CLINKs
				}
			}
		}
		
		//rs.disconnect();
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
