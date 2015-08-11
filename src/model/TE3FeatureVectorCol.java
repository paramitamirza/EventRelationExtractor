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

public class TE3FeatureVectorCol {
	public static void getFeatureVector(TXPParser parser, String filepath, TemporalSignalList tsignalList, CausalSignalList csignalList, 
			PrintWriter ee, PrintWriter et, PrintWriter tt) throws IOException {
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
								tt.println(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
										tlink.getRelType() + "\t" + ttlinks.get(st));
							} else if (ttlinks.containsKey(ts)) {
								tt.println(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
										tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
							}
							break;
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
							//Entity attributes
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getCombinedEntityAttributes());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getSameEntityAttributes());
							
							//dependency information
							fv.getVectors().add(((EventEventFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().add(((EventEventFeatureVector) fv).getMateMainVerb());
							
							//temporal/causal signal/connective/verb
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getTemporalMarker());
							fv.getVectors().addAll(((EventEventFeatureVector) fv).getCausalMarker());
							
							//WordNet similarity
							fv.getVectors().add(((EventEventFeatureVector) fv).getWordSimilarity().toString());
							
							//event co-reference
							fv.getVectors().add(((EventEventFeatureVector) fv).isCoreference() ? "COREF" : "NOCOREF");
							
						} else if (fv instanceof EventTimexFeatureVector) {
							fv.getVectors().add(fv.getOrder());
							
							//Entity attributes
							fv.getVectors().addAll(((EventTimexFeatureVector) fv).getEntityAttributes());
							
							//dependency information
							fv.getVectors().add(((EventTimexFeatureVector) fv).getMateDependencyPath());
							fv.getVectors().add(((EventTimexFeatureVector) fv).getMateMainVerb());
							
							//temporal signal/connective
							fv.getVectors().addAll(((EventTimexFeatureVector) fv).getTemporalMarker());
							
							//timex rule type
							fv.getVectors().add(((EventTimexFeatureVector) fv).getTimexRule());
						}
						
						fv.getVectors().add(fv.getLabel());
						
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
			
			String eeFeatures = "token_e1,token_e2,lemma_e1,lemma_e2,pos_e1|pos_e2,mainpos_e1|mainpos_e2,chunk_e1|chunk_e2,"
					+ "ner_e1|ner_e2,same_pos,same_mainpos,ent_distance,sent_distance,evclass_e1|evclass_e2,"
					+ "tense-aspect_e1|tense-aspect_e2,pol_e1|pol_e2,same_evclass,same_tense-aspect,same_pol,"
					+ "deprel,ismainverb,temp_marker-position,temp_marker_deprel,causal_marker-position, causal_marker_deprel,"
					+ "wnsim, coref,label";
			//ee.println(eeFeatures);
			String etFeatures = "token_e1,token_e2,lemma_e1,lemma_e2,pos_e1|pos_e2,mainpos_e1|mainpos_e2,chunk_e1|chunk_e2,"
					+ "ner_e1|ner_e2,same_pos,same_mainpos,ent_distance,sent_distance,ent_order,evclass_e1,"
					+ "tense-aspect_e1,pol_e1,tmxtype-value_e2,tmxdct_e2,"
					+ "deprel,ismainverb,temp_marker-position,temp_marker_deprel,"
					+ "timexrule, label";
			//et.println(etFeatures);
			
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
