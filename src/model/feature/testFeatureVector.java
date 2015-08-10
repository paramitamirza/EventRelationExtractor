package model.feature;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import model.feature.FeatureEnum.*;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.*;

public class testFeatureVector {
	
	public static void getFeatureVector(TXPParser parser, String filepath, TemporalSignalList tsignalList, CausalSignalList csignalList) throws IOException {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){
				
				getFeatureVector(parser, file.getPath(), tsignalList, csignalList);
				
			} else if (file.isFile()) {				
				Document doc = parser.parseDocument(file.getPath());
				
				Object[] entArr = doc.getEntities().keySet().toArray();
				
				for (int i = 0; i < entArr.length; i++) {
					for (int j = i; j < entArr.length; j++) {
						if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
								doc.getEntities().get(entArr[j]) instanceof Timex) {
							TimexTimexRelationRule tt = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
									((Timex)doc.getEntities().get(entArr[j])), doc.getDct());
							//System.out.println(entArr[i] + "\t" + entArr[j] + "\t" + tt.getRelType());
						}
					}
				}
				
				for (TemporalRelation tlink : doc.getTlinks()) {
					if (!tlink.getSourceID().equals(tlink.getTargetID()) &&
							doc.getEntities().containsKey(tlink.getSourceID()) &&
							doc.getEntities().containsKey(tlink.getTargetID())) {
						//System.out.println(file.getName() + "\t " + tlink.getSourceID() + "-" + tlink.getTargetID());
						Entity e1 = doc.getEntities().get(tlink.getSourceID());
						Entity e2 = doc.getEntities().get(tlink.getTargetID());
						
						PairFeatureVector fv = new PairFeatureVector(doc, e1, e2, tlink.getRelType(), tsignalList, csignalList);		
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
						
						//if (fv instanceof EventEventFeatureVector) {
						//	System.out.println(fv.printVectors());
						//} else if (fv instanceof EventTimexFeatureVector) {
						//	System.out.println(fv.printVectors());
						//}
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
			
			getFeatureVector(parser, args[0], tsignalList, csignalList);
			
			for (String s : EventEventFeatureVector.fields) {
				System.out.println(s);
			}
			
			for (String s : EventTimexFeatureVector.fields) {
				System.out.println(s);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
