package model;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

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
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.TemporalRelation;
import parser.entities.Timex;

public class TE3FeatureVectorCSV {
	public static void getFeatureVector(TXPParser parser, String filepath, TemporalSignalList tsignalList, CausalSignalList csignalList, 
			StringBuilder ee, StringBuilder et, StringBuilder tt) throws IOException {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){
				
				getFeatureVector(parser, file.getPath(), tsignalList, csignalList, ee, et, tt);
				
			} else if (file.isFile()) {				
				Doc doc = parser.parseDocument(file.getPath());
				
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
								tt.append(tlink.getSourceID() + "," + tlink.getTargetID() + "," + 
										tlink.getRelType() + "," + ttlinks.get(st) + "\n");
							} else if (ttlinks.containsKey(ts)) {
								tt.append(tlink.getSourceID() + "," + tlink.getTargetID() + "," + 
										tlink.getRelType() + "," + TemporalRelation.getInverseRelation(ttlinks.get(ts)) + "\n");
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
						fv.addToVector(Feature.sameMainPos);
						
						//context features
						fv.addToVector(Feature.entDistance);
						fv.addToVector(Feature.sentDistance);
						
						if (fv instanceof EventEventFeatureVector) {
							//Entity attributes
							fv.addToVector(Feature.eventClass);
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
							
						} else if (fv instanceof EventTimexFeatureVector) {
							fv.addToVector(Feature.entOrder);
							
							//Entity attributes
							fv.addToVector(Feature.eventClass);
							fv.addToVector(Feature.tense);
							fv.addToVector(Feature.aspect);
							fv.addToVector(Feature.polarity);
							fv.addToVector(Feature.timexType);
							fv.addToVector(Feature.timexValueTemplate);
							fv.addToVector(Feature.dct);
							
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
							ee.append(fv.printCSVVectors() + "\n");
						} else if (fv instanceof EventTimexFeatureVector) {
							et.append(fv.printCSVVectors() + "\n");
						}
					}
				}
			}
		}
		
		
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
			TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
			CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
			
			StringBuilder ee = new StringBuilder(""), et = new StringBuilder(""), tt = new StringBuilder("");
			getFeatureVector(parser, args[0], tsignalList, csignalList, ee, et, tt);
			
			PrintWriter eeFile = new PrintWriter("data/event-event.csv", "UTF-8");
			PrintWriter etFile = new PrintWriter("data/event-timex.csv", "UTF-8");
			PrintWriter ttFile = new PrintWriter("data/timex-timex.csv", "UTF-8");
			
			String eeFields = "", etFields = "";
			for (String s : EventEventFeatureVector.fields) {
				if (s != null) eeFields += s + ",";
			}
			for (String s : EventTimexFeatureVector.fields) {
				if (s!= null) etFields += s + ",";
			}
			eeFile.println(eeFields.substring(0,eeFields.length()-1));
			etFile.println(etFields.substring(0, etFields.length()-1));
			
			eeFile.print(ee);
			etFile.print(et);
			ttFile.print(tt);
			
			eeFile.close();
			etFile.close();
			ttFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
