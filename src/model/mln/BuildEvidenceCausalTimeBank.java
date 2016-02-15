package model.mln;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import model.feature.PairFeatureVector;
import model.feature.FeatureEnum.PairType;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.entities.CausalRelation;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.TemporalRelation;
import task.CausalTimeBankTask;

public class BuildEvidenceCausalTimeBank {

	public static void main(String [] args) throws Exception {
		
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink, 
				Field.clink, Field.csignal};
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);
		
		String dirTxpPath = "./data/Causal-TimeBank_TXP/";
		
		File[] txpFiles = new File(dirTxpPath).listFiles();
		
//		BufferedWriter bw = new BufferedWriter(new FileWriter("./data/mln/causal-timebank.db"));
		Integer f = 1;
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			Doc docTxp = txpParser.parseDocument(txpFile.getPath());
//			BufferedWriter bw = new BufferedWriter(new FileWriter("./data/mln/timebank/causal-timebank"+f+".db"));
			BufferedWriter bw = new BufferedWriter(new FileWriter("./data/mln/timebank/"+txpFile.getName()+".db"));
			for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
				if (!tlink.getRelType().equals("NONE")) {
					Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
					Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
					PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), null, null);	
					
					if (fv.getPairType().equals(PairType.event_event)) {
						bw.write("RelEE("
//								+ "F" + f + tlink.getSourceID().toUpperCase() + ", "
//								+ "F" + f + tlink.getTargetID().toUpperCase() + ", "
								+ tlink.getSourceID().toUpperCase() + ", "
								+ tlink.getTargetID().toUpperCase() + ", "
								+ tlink.getRelType()
								+ ")\n");
					} else if (fv.getPairType().equals(PairType.event_timex)) {
						if (tlink.getSourceID().contains("tmx")) {
							bw.write("RelTE("
//									+ "F" + f + tlink.getSourceID().replace("tmx", "t").toUpperCase() + ", "
//									+ "F" + f + tlink.getTargetID().toUpperCase() + ", "
									+ tlink.getSourceID().replace("tmx", "t").toUpperCase() + ", "
									+ tlink.getTargetID().toUpperCase() + ", "
									+ tlink.getRelType()
									+ ")\n");
						} else {
							bw.write("RelET("
//									+ "F" + f + tlink.getSourceID().toUpperCase() + ", "
//									+ "F" + f + tlink.getTargetID().replace("tmx", "t").toUpperCase() + ", "
									+ tlink.getSourceID().toUpperCase() + ", "
									+ tlink.getTargetID().replace("tmx", "t").toUpperCase() + ", "
									+ tlink.getRelType()
									+ ")\n");
						}
					} else if (fv.getPairType().equals(PairType.timex_timex)) {
						bw.write("RelTT("
//								+ "F" + f + tlink.getSourceID().replace("tmx", "t").toUpperCase() + ", "
//								+ "F" + f + tlink.getTargetID().replace("tmx", "t").toUpperCase() + ", "
								+ tlink.getSourceID().replace("tmx", "t").toUpperCase() + ", "
								+ tlink.getTargetID().replace("tmx", "t").toUpperCase() + ", "
								+ tlink.getRelType()
								+ ")\n");
					}
				}
			}
			for (CausalRelation clink : docTxp.getClinks()) {
				bw.write("RelEE("
//						+ "F" + f + clink.getSourceID().toUpperCase() + ", "
//						+ "F" + f + clink.getTargetID().toUpperCase() + ", "
						+ clink.getSourceID().toUpperCase() + ", "
						+ clink.getTargetID().toUpperCase() + ", "
						+ "CAUSAL"
						+ ")\n");
			}
			bw.close();
			f ++;
		}
//		bw.close();
	}
}
