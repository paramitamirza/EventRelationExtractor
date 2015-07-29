package parser;

import java.io.File;
import java.io.IOException;

import parser.entities.Document;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.Timex;
import parser.entities.Token;
import parser.TXPParser.Field;

public class testParser {
	
	public static void main(String [] args) {
		
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coevent, Field.tlink};
		TXPParser parser = new TXPParser(EntityEnum.Language.EN, fields);
		
		File dir_TXP = new File(args[0]);
		File[] files_TXP = dir_TXP.listFiles();
		for (File file : files_TXP) {
			if (file.isFile()) {
				
				try {
					Document doc = parser.parseDocument(file.getPath());
					
					/*for (String tid : doc.getTokenArr()) {
						Token tok = doc.getTokens().get(tid);
						System.out.println(tok.getText() + "\t" + tok.getLemma() + "\t" + tok.getPos() + 
								"\t" + tok.getMainPos() + "\t" + tok.getChunk() + "\t" + tok.getNamedEntity());
						if (tok.getDependencyRel() != null) {
							for (String key : tok.getDependencyRel().keySet()) {
								System.out.println(key + "-" + tok.getDependencyRel().get(key));
							}
						}
					}*/
					
					/*for (String ent_id : doc.getEntityArr()) {
						Entity ent = doc.getEntities().get(ent_id);
						if (ent instanceof Timex) {
							System.out.println(ent.getID() + "\tTimex\t" + ent.getStartTokID() + 
									"\t" + ent.getEndTokID());
						} else if (ent instanceof Event) {
							System.out.println(ent.getID() + "\tEvent\t" + ent.getStartTokID() + 
									"\t" + ent.getEndTokID());
						}
					}*/
					
					/*for (TemporalRelation tlink : doc.getTlinks()) {
						System.out.println(tlink.getSourceID() + "\t" + tlink.getTargetID() + 
								"\t" + tlink.getRelType());
					}*/
					
					System.out.println();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
