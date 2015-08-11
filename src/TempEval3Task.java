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


public class TempEval3Task {
	
	private ArrayList<String> eeFeatures;
	private ArrayList<String> etFeatures;
	private String name;
	private String trainDirPath;
	private String testDirPath;
	private String goldDirPath;
	private String systemDirPath;
	TemporalSignalList tsignalList;
	CausalSignalList csignalList;
	private int eeFeatLen;
	private int etFeatLen;
	
	public TempEval3Task() throws IOException {
		name = "te3";
		trainDirPath = "data/TempEval3-train_TXP";
		testDirPath = "data/TempEval3-eval_TXP";
		goldDirPath = "data/TempEval3-eval_TML";
		systemDirPath = "data/TempEval3-system_TML";
		
		File sysDir = new File(systemDirPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
	}
	
	public void getFeatureVectorPerFile(TXPParser parser, String filepath, PrintWriter ee, PrintWriter et, PrintWriter tt) throws IOException {
		Doc doc = parser.parseDocument(filepath);
		
		Object[] entArr = doc.getEntities().keySet().toArray();
		
		HashMap<Pair<String,String>,String> ttlinks = new HashMap<Pair<String,String>,String>();
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
		
		for (TemporalRelation tlink : doc.getTlinks()) {
			if (!tlink.getSourceID().equals(tlink.getTargetID()) &&
					doc.getEntities().containsKey(tlink.getSourceID()) &&
					doc.getEntities().containsKey(tlink.getTargetID()) &&
					!tlink.getRelType().equals("NONE")) {	//classifying the relation task
				//System.out.println(file.getName() + "\t " + tlink.getSourceID() + "-" + tlink.getTargetID());
				Entity e1 = doc.getEntities().get(tlink.getSourceID());
				Entity e2 = doc.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(doc, e1, e2, tlink.getRelType(), tsignalList, csignalList);		
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.timex_timex)) {
					Pair<String,String> st = new Pair<String, String>(tlink.getSourceID(), tlink.getTargetID());
					Pair<String,String> ts = new Pair<String, String>(tlink.getTargetID(), tlink.getSourceID());
					if (ttlinks.containsKey(st)) {
						tt.println(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else if (ttlinks.containsKey(ts)) {
						tt.println(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
					}
					break;
				}
				
				fv.addToVector(Feature.id);
				
				//token attribute features
				fv.addToVector(Feature.token);
				fv.addToVector(Feature.lemma);
				fv.addToVector(Feature.pos);
				fv.addToVector(Feature.mainpos);
				fv.addToVector(Feature.chunk);
				fv.addToVector(Feature.ner);
				fv.addToVector(Feature.samePos);
				//fv.addToVector(Feature.sameMainPos);
				
				//context features
				fv.addToVector(Feature.entDistance);
				fv.addToVector(Feature.sentDistance);
				
				if (fv instanceof EventEventFeatureVector) {
					//Entity attributes
					fv.addToVector(Feature.eventClass);
					fv.addToVector(Feature.tenseAspect);
					//fv.addToVector(Feature.aspect);
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
					//fv.addToVector(Feature.coref);
					
					//WordNet similarity
					fv.addToVector(Feature.wnSim);
					
				} else if (fv instanceof EventTimexFeatureVector) {
					fv.addToVector(Feature.entOrder);
					
					//Entity attributes
					fv.addToVector(Feature.eventClass);
					fv.addToVector(Feature.tense);
					fv.addToVector(Feature.aspect);
					fv.addToVector(Feature.polarity);
					fv.addToVector(Feature.timexType);
					fv.addToVector(Feature.timexValueTemplate);
					fv.addToVector(Feature.dct);	//no improv
					
					//dependency information
					fv.addToVector(Feature.depPath);
					fv.addToVector(Feature.mainVerb);
					
					//temporal connective/signal
					fv.addToVector(Feature.tempMarker);
					
					//timex rule type
					fv.addToVector(Feature.timexRule);
				}
				
				fv.addToVector(Feature.label);
				
				if (fv instanceof EventEventFeatureVector) {
					ee.println(fv.printVectors());
				} else if (fv instanceof EventTimexFeatureVector) {
					et.println(fv.printVectors());
				}
			}
		}
		ee.println();
		et.println();
	}
	
	public void getFeatureVector(TXPParser parser, String filepath, PrintWriter ee, PrintWriter et, PrintWriter tt) throws IOException {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){				
				this.getFeatureVector(parser, file.getPath(), ee, et, tt);				
			} else if (file.isFile()) {				
				getFeatureVectorPerFile(parser, file.getPath(), ee, et, tt);
			}
		}		
	}
	
	public void train(TXPParser parser) throws IOException, SftpException, JSchException {
		System.out.println("Building training data...");
		System.setProperty("line.separator", "\n");
		PrintWriter ee = new PrintWriter("data/" + name + "-ee-train.tlinks", "UTF-8");
		PrintWriter et = new PrintWriter("data/" + name + "-et-train.tlinks", "UTF-8");
		PrintWriter tt = new PrintWriter("data/" + name + "-tt-train.tlinks", "UTF-8");
		getFeatureVector(parser, trainDirPath, ee, et, tt);
		ee.close();
		et.close();
		tt.close();
		System.out.println("event-event features:");
		String fields = "";
		eeFeatLen = 0;
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) {
				fields += s + ", ";
				eeFeatLen += 1;
			}
		}
		System.out.println(fields.substring(0, fields.length()-1));
		System.out.println("event-timex features:");
		fields = "";
		etFeatLen = 0;
		for (String s : EventTimexFeatureVector.fields) {
			if (s!= null) {
				fields += s + ", ";
				etFeatLen += 1;
			}
		}
		System.out.println(fields.substring(0, fields.length()-1));
		
		System.out.println("Copy training data...");
		File eeFile = new File("data/" + name + "-ee-train.tlinks");
		File etFile = new File("data/" + name + "-et-train.tlinks");
		File[] files = {eeFile, etFile};
		RemoteServer rs = new RemoteServer();
		rs.copyFiles(files, "data/");
		
		System.out.println("Train models...");
		String cmdCd = "cd tools/yamcha-0.33/";
		String cmdTrainEE = "make CORPUS=~/data/"+name+"-ee-train.tlinks "
				+ "MODEL=~/models/"+name+"-ee "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		String cmdTrainET = "make CORPUS=~/data/"+name+"-et-train.tlinks "
				+ "MODEL=~/models/"+name+"-et "
				+ "FEATURE=\"F:0:2..\" "
				+ "SVM_PARAM=\"-t1 -d2 -c1 -m 512\" train";
		rs.executeYamchaCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
		
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
	
	public void evaluate(TXPParser parser) throws IOException, SftpException, JSchException {
		System.out.println("Building testing data...");
		System.setProperty("line.separator", "\n");
		PrintWriter ee = new PrintWriter("data/" + name + "-ee-eval.tlinks", "UTF-8");
		PrintWriter et = new PrintWriter("data/" + name + "-et-eval.tlinks", "UTF-8");
		PrintWriter tt = new PrintWriter("data/" + name + "-tt-eval.tlinks", "UTF-8");
		getFeatureVector(parser, testDirPath, ee, et, tt);
		ee.close();
		et.close();
		tt.close();
		
		eeFeatLen = 0;
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) eeFeatLen += 1;
		}
		etFeatLen = 0;
		for (String s : EventTimexFeatureVector.fields) {
			if (s!= null) etFeatLen += 1;
		}
		
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
				+ " | cut -f1,2," + (eeFeatLen) + "," + (eeFeatLen+1);
				//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
		String cmdTestET = "./usr/local/bin/yamcha -m ~/models/"+name+"-et.model"
				+ " < ~/data/"+name+"-et-eval.tlinks "
				+ " | cut -f1,2," + (etFeatLen) + "," + (etFeatLen+1);
				//+ " > ~/data/"+name+"-et-eval-tagged.tlinks";
		List<String> eeResult = rs.executeYamchaCommand(cmdCd + " && " + cmdTestEE);
		List<String> etResult = rs.executeYamchaCommand(cmdCd + " && " + cmdTestET);
		
		BufferedReader br = new BufferedReader(new FileReader("data/" + name + "-tt-eval.tlinks"));
		List<String> ttResult = new ArrayList<String>();
		String line;
		while ((line = br.readLine()) != null) { 
			ttResult.add(line);
		}
		br.close();
		
		System.out.println("Accuracy event-event: " + String.format( "%.2f", accuracy(eeResult)*100) + "%");
		System.out.println("Accuracy event-timex: " + String.format( "%.2f", accuracy(etResult)*100) + "%");
		System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%");
		
		rs.disconnect();
	}
	
	public void evaluateTE3(TXPParser parser, TimeMLParser tmlParser) throws ParserConfigurationException, SAXException, IOException, TransformerException, JSchException, SftpException {
		File dir_TXP = new File(testDirPath);
		File[] files_TXP = dir_TXP.listFiles();
		
		for (File file : files_TXP) {
			if (file.isFile()) {	
				System.setProperty("line.separator", "\n");
				PrintWriter ee = new PrintWriter("data/" + name + "-ee-eval.tlinks", "UTF-8");
				PrintWriter et = new PrintWriter("data/" + name + "-et-eval.tlinks", "UTF-8");
				PrintWriter tt = new PrintWriter("data/" + name + "-tt-eval.tlinks", "UTF-8");
				getFeatureVectorPerFile(parser, file.getPath(), ee, et, tt);
				ee.close();
				et.close();
				tt.close();
				
				eeFeatLen = 0;
				for (String s : EventEventFeatureVector.fields) {
					if (s!= null) eeFeatLen += 1;
				}
				etFeatLen = 0;
				for (String s : EventTimexFeatureVector.fields) {
					if (s!= null) etFeatLen += 1;
				}
				
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
						+ " | cut -f1,2," + (eeFeatLen) + "," + (eeFeatLen+1);
						//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
				String cmdTestET = "./usr/local/bin/yamcha -m ~/models/"+name+"-et.model"
						+ " < ~/data/"+name+"-et-eval.tlinks "
						+ " | cut -f1,2," + (etFeatLen) + "," + (etFeatLen+1);
						//+ " > ~/data/"+name+"-et-eval-tagged.tlinks";
				
				List<String> eeResult = rs.executeYamchaCommand(cmdCd + " && " + cmdTestEE);
				List<String> etResult = rs.executeYamchaCommand(cmdCd + " && " + cmdTestET);
				
				BufferedReader br = new BufferedReader(new FileReader("data/" + name + "-tt-eval.tlinks"));
				List<String> ttResult = new ArrayList<String>();
				String line;
				while ((line = br.readLine()) != null) { 
					ttResult.add(line);
				}
				br.close();
				
//				System.out.println("Accuracy event-event: " + String.format( "%.2f", accuracy(eeResult)*100) + "%");
//				System.out.println("Accuracy event-timex: " + String.format( "%.2f", accuracy(etResult)*100) + "%");
//				System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%");
				
				rs.disconnect();
				
				Doc dTml = tmlParser.parseDocument(goldDirPath + "/" + file.getName().replace(".txp", ""));
				Doc dTxp = parser.parseDocument(file.getPath());
				TimeMLDoc tml = new TimeMLDoc(goldDirPath + "/" + file.getName().replace(".txp", ""));
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
				
				PrintWriter sysTML = new PrintWriter(systemDirPath + "/" + file.getName().replace(".txp", ""));
				sysTML.write(tml.toString());
				sysTML.close();
			}
		}
		
		TempEval3 te3 = new TempEval3(goldDirPath, systemDirPath);
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
		
		//dir_TXP <-- data/example_TXP
		try {
			TempEval3Task task = new TempEval3Task();
			//task.train(parser);			
			//task.evaluate(parser);
			
			TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
			task.evaluateTE3(parser, tmlParser);
			
			
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
		} 
	}

}
