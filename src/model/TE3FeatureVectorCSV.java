package model;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.FeatureVector;
import model.feature.TemporalSignalList;
import model.feature.TimexTimexRelationRule;
import model.feature.FeatureEnum.Feature;
import model.feature.FeatureEnum.PairType;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.Document;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.TemporalRelation;
import parser.entities.Timex;

public class TE3FeatureVectorCSV {
	public static void getFeatureVector(TXPParser parser, String filepath, TemporalSignalList tsignalList, CausalSignalList csignalList, 
			PrintWriter ee, PrintWriter et, PrintWriter tt) throws IOException {
		File dir_TXP = new File(filepath);
		File[] files_TXP = dir_TXP.listFiles();
		
		if (files_TXP == null) return;
		
		String eeFeatures = "token_e1,token_e2,lemma_e1,lemma_e2,pos_e1|pos_e2,mainpos_e1|mainpos_e2,chunk_e1|chunk_e2,ner_e1|ner_e2,samepos_e1_e2,samemainpos_e1_e2,";
		ee.println(eeFeatures);
		
		for (File file : files_TXP) {
			if (file.isDirectory()){
				
				getFeatureVector(parser, file.getPath(), tsignalList, csignalList, ee, et, tt);
				
			} else if (file.isFile()) {				
				Document doc = parser.parseDocument(file.getPath());
				
				Object[] entArr = doc.getEntities().keySet().toArray();
				
				for (int i = 0; i < entArr.length; i++) {
					for (int j = i; j < entArr.length; j++) {
						if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
								doc.getEntities().get(entArr[j]) instanceof Timex) {
							TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
									((Timex)doc.getEntities().get(entArr[j])), doc.getDct());
							if (!timextimex.getRelType().equals("O")) {
								tt.println(entArr[i] + "\t" + entArr[j] + "\t" + 
										timextimex.getRelType());
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
						
						FeatureVector fv = new FeatureVector(doc, e1, e2, tlink.getRelType(), tsignalList, csignalList);		
						if (fv.getPairType().equals(PairType.event_event)) {
							fv = new EventEventFeatureVector(fv);
						} else if (fv.getPairType().equals(PairType.event_timex)) {
							fv = new EventTimexFeatureVector(fv);
						}
						
						//fv.getVectors().add(fv.getE1().getID());
						//fv.getVectors().add(fv.getE2().getID());
						
						//token attribute features
						fv.getVectors().addAll(fv.getTokenAttribute(Feature.token));
						fv.getVectors().addAll(fv.getTokenAttribute(Feature.lemma));
						fv.getVectors().add(fv.getCombinedTokenAttribute(Feature.pos));
						fv.getVectors().add(fv.getCombinedTokenAttribute(Feature.mainpos));
						fv.getVectors().add(fv.getCombinedTokenAttribute(Feature.chunk));
						fv.getVectors().add(fv.getCombinedTokenAttribute(Feature.ner));
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
							ee.println(fv.printCSVVectors());
						} else if (fv instanceof EventTimexFeatureVector) {
							et.println(fv.printCSVVectors());
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
			
			PrintWriter ee = new PrintWriter("data/event-event.tlink", "UTF-8");
			PrintWriter et = new PrintWriter("data/event-timex.tlink", "UTF-8");
			PrintWriter tt = new PrintWriter("data/timex-timex.tlink", "UTF-8");
			
			getFeatureVector(parser, args[0], tsignalList, csignalList, ee, et, tt);
			
			ee.close();
			et.close();
			tt.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
