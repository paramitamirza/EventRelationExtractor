package model.feature;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import model.feature.FeatureEnum.*;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.*;

public class testFeatureVector {
	
	public static void getFeatureVector(TXPParser parser, String filepath, TemporalSignalList tsignalList, CausalSignalList csignalList, PrintWriter event, PrintWriter timex) throws IOException {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		for (File file : files_TXP) {
			if (file.isDirectory()){
				
				getFeatureVector(parser, file.getPath(), tsignalList, csignalList, event, timex);
				
			} else if (file.isFile()) {				
				Document doc = parser.parseDocument(file.getPath());
				
				Object[] entArr = doc.getEntities().keySet().toArray();
				
				for (int i = 0; i < entArr.length; i++) {
					for (int j = i; j < entArr.length; j++) {
						if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
								doc.getEntities().get(entArr[j]) instanceof Timex) {
							TimexTimexRelationRule tt = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
									((Timex)doc.getEntities().get(entArr[j])), doc.getDct());
							System.out.println(entArr[i] + "\t" + entArr[j] + "\t" + 
									tt.getRelType());
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
						
						FeatureVector fv = new FeatureVector(doc, e1, e2, tlink.getRelType(), tsignalList, csignalList);		
						if (fv.getPairType().equals(PairType.event_event)) {
							fv = new EventEventFeatureVector(fv);
						} else if (fv.getPairType().equals(PairType.event_timex)) {
							fv = new EventTimexFeatureVector(fv);
						}
						
						fv.getVectors().add(fv.getE1().getID());
						fv.getVectors().add(fv.getE2().getID());
						
						//token attribute features
						fv.getVectors().addAll(fv.getTokenAttribute(Feature.token));
						fv.getVectors().addAll(fv.getTokenAttribute(Feature.lemma));
						fv.getVectors().addAll(fv.getCombinedTokenAttribute(Feature.pos));
						fv.getVectors().addAll(fv.getCombinedTokenAttribute(Feature.mainpos));
						fv.getVectors().addAll(fv.getCombinedTokenAttribute(Feature.chunk));
						fv.getVectors().addAll(fv.getCombinedTokenAttribute(Feature.ner));
						fv.getVectors().add(fv.isSameTokenAttribute(Feature.pos) ? "TRUE" : "FALSE");
						fv.getVectors().add(fv.isSameTokenAttribute(Feature.mainpos) ? "TRUE" : "FALSE");
						
						//context features
						fv.getVectors().add(fv.getEntityDistance().toString());
						fv.getVectors().add(fv.getSentenceDistance().toString());
						
						if (fv instanceof EventEventFeatureVector) {
							//WordNet similarity
							fv.getVectors().add(((EventEventFeatureVector) fv).getWordSimilarity().toString());
							
							//Entity attributes
							//fv.getVectors().addAll(((EventEventFeatureVector) fv).getEntityAttributes());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getCombinedEntityAttributes());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getSameEntityAttributes());
							
							//dependency information
							fv.getVectors().add(((EventEventFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getMateMainVerb());
							
							//temporal/causal signal & connective
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getTemporalMarker());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getCausalMarker());
							
							//event co-reference
							fv.getVectors().add(((EventEventFeatureVector) fv).isCoreference() ? "COREF" : "NOCOREF");
							
						} else if (fv instanceof EventTimexFeatureVector) {
							fv.getVectors().add(fv.getOrder());
							
							//Entity attributes
							fv.getVectors().addAll(((EventTimexFeatureVector) fv).getEntityAttributes());
							
							//dependency information
							fv.getVectors().add(((EventTimexFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().add(((EventTimexFeatureVector) fv).getMateMainVerb());
							
							//temporal signal & connective
							fv.getVectors().addAll(((EventTimexFeatureVector) fv).getTemporalMarker());
							
							//timex rule type
							fv.getVectors().add(((EventTimexFeatureVector) fv).getTimexRule());
						}
						
						fv.getVectors().add(fv.getLabel());
						
						if (fv instanceof EventEventFeatureVector) {
							event.println(fv.printVectors());
						} else if (fv instanceof EventTimexFeatureVector) {
							timex.println(fv.printVectors());
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
			
			PrintWriter event = new PrintWriter("data/event-event.tlink", "UTF-8");
			PrintWriter timex = new PrintWriter("data/event-timex.tlink", "UTF-8");
			
			getFeatureVector(parser, args[0], tsignalList, csignalList, event, timex);
			
			event.close();
			timex.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
