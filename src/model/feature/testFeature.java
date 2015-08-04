package model.feature;

import java.io.File;
import java.io.IOException;

import model.feature.FeatureEnum.*;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.*;

public class testFeature {
	
	public static void main(String [] args) {
		
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		TXPParser parser = new TXPParser(EntityEnum.Language.EN, fields);
		
		File dir_TXP = new File(args[0]);
		File[] files_TXP = dir_TXP.listFiles();
		for (File file : files_TXP) {
			if (file.isFile()) {
				
				try {
					Document doc = parser.parseDocument(file.getPath());
					TemporalSignalList tsignalList = new TemporalSignalList(doc.getLang());
					
					for (TemporalRelation tlink : doc.getTlinks()) {
						Entity e1 = doc.getEntities().get(tlink.getSourceID());
						Entity e2 = doc.getEntities().get(tlink.getTargetID());
						
						FeatureVector fv = new FeatureVector(doc, e1, e2, tlink.getRelType(), tsignalList);		
						if (fv.getPairType().equals(PairType.event_event)) {
							fv = new EventEventFeatureVector(fv);
						} else if (fv.getPairType().equals(PairType.event_timex)) {
							fv = new EventTimexFeatureVector(fv);
						}
						
						fv.getVectors().add(fv.getE1().getID());
						fv.getVectors().add(fv.getE2().getID());
						
						//token attribute features
						//fv.getVectors().addAll(fv.getTokenAttribute(Feature.token));
						fv.getVectors().addAll(fv.getTokenAttribute(Feature.lemma));
						//fv.getVectors().addAll(fv.getTokenAttribute(Feature.pos));
						//fv.getVectors().addAll(fv.getTokenAttribute(Feature.mainpos));
						//fv.getVectors().addAll(fv.getTokenAttribute(Feature.chunk));
						//fv.getVectors().addAll(fv.getTokenAttribute(Feature.ner));
						//fv.getVectors().add(fv.isSameTokenAttribute(Feature.pos) ? "TRUE" : "FALSE");
						//fv.getVectors().add(fv.isSameTokenAttribute(Feature.mainpos) ? "TRUE" : "FALSE");*/
						
						//context features
						/*fv.getVectors().add(fv.getEntityDistance().toString());
						fv.getVectors().add(fv.getSentenceDistance().toString());
						fv.getVectors().add(fv.getOrder());
						*/
						if (fv instanceof EventEventFeatureVector) {
							//WordNet similarity
							/*fv.getVectors().add(((EventEventFeatureVector) fv).getWordSimilarity().toString());
							
							//Entity attributes
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getEntityAttributes());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getCombinedEntityAttributes());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getSameEntityAttributes());
							
							//event co-reference
							fv.getVectors().add(((EventEventFeatureVector) fv).isCoreference() ? "COREF" : "O");
							
							//dependency information
							fv.getVectors().add(((EventEventFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getMateMainVerb());*/
							
							//temporal signal
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getTemporalSignal());
							
						} else if (fv instanceof EventTimexFeatureVector) {
							//Entity attributes
							//fv.getVectors().addAll(((EventTimexFeatureVector) fv).getEntityAttributes());
							
							//dependency information
							/*fv.getVectors().add(((EventTimexFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().add(((EventTimexFeatureVector) fv).getMateMainVerb());*/
						}
						
						fv.getVectors().add(fv.getLabel());
						
						if (fv instanceof EventEventFeatureVector)
							System.out.println(fv.printVectors());
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}

}
