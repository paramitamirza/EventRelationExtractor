package simplifier;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.PairType;
import parser.TXPParser;
import parser.TimeMLParser;
import parser.TXPParser.Field;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Sentence;
import parser.entities.TemporalRelation;
import parser.entities.Timex;

public class TestSentenceSimplifierTempEval3 {

	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String txpDirpath = "./data/TempEval3-train_TXP2/";
		String tmlDirpath = "./data/TempEval3-train_TML/";
		
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;	
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	    LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);
	    
	    int eeSame = 0;
	    int eeDiff = 0;
	    int etSame = 0;
	    int etDiff = 0;
	    int etDct = 0;
	    int tt = 0;
	    		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			
			Doc docTxp = txpParser.parseDocument(txpFile.getPath());
			Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
			
			//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
			for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
				if (!tlink.getSourceID().equals(tlink.getTargetID())
						&& docTxp.getEntities().containsKey(tlink.getSourceID())
						&& docTxp.getEntities().containsKey(tlink.getTargetID())
						&& !tlink.getRelType().equals("NONE")
						) {	//classifying the relation task
			
					Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
					Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
					
					PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
					
					if (fv.getPairType() == PairType.event_event) {
						fv = new EventEventFeatureVector(fv);
						
						String sid1 = fv.getE1().getSentID();
						String sid2 = fv.getE2().getSentID();
						
						if (sid1.equals(sid2)) {	//the same sentence
							eeSame ++;
							Sentence s = docTxp.getSentences().get(sid1);
							
							Integer fidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
									docTxp.getTokenArr().indexOf(s.getStartTokID());
							Integer fidx2 = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID()) - 
									docTxp.getTokenArr().indexOf(s.getStartTokID());
							
							String line = "";
							String sent = s.toString(docTxp);
//							line += sent + "|" + fidx1 + "|" + fidx2 + "|" + fv.getLabel() + "#";
//							
//							line += docTxp.getTokens().get(fv.getE1().getStartTokID()).getText();
//							line += "|";
//							line += docTxp.getTokens().get(fv.getE2().getStartTokID()).getText();
//							line += "|";
							
							String simpStr = "";
							SentenceSimplifier ss = new SentenceSimplifier(lp, sent);
//							line += ss.simplifiedFocus(fidx1, fidx2);
//							line += "|";
//							line += ss.simplifiedFocus(fidx2, fidx1);
//							line += "|";
							simpStr += ss.simplifiedString(fidx1, fidx2, false);
							while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
//							simpStr = simpStr.replace(") (", " ");
							
							line += simpStr;
							line += "|";
							line += fv.getLabel();
							
							System.out.println(docTml.getFilename() + "\t" + line);
						
						} else {	//different sentences
							eeDiff ++;
//							Sentence s1 = docTxp.getSentences().get(sid1);
//							Sentence s2 = docTxp.getSentences().get(sid2);
//							
//							Integer fidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
//									docTxp.getTokenArr().indexOf(s1.getStartTokID());
//							Integer fidx2 = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID()) - 
//									docTxp.getTokenArr().indexOf(s2.getStartTokID());
//							
//							String line = "";
//							String sent1 = s1.toString(docTxp);
//							String sent2 = s2.toString(docTxp);
////							line += sent1 + "|" + sent2 + "|" + fidx1 + "|" + fidx2 + "|" + fv.getLabel() + "#";
//							
//							line += docTxp.getTokens().get(fv.getE1().getStartTokID()).getText();
//							line += "|";
//							line += docTxp.getTokens().get(fv.getE2().getStartTokID()).getText();
//							line += "|";
//							
//							String simpStr = "";
//							SentenceSimplifier ss1 = new SentenceSimplifier(lp, sent1);
//							SentenceSimplifier ss2 = new SentenceSimplifier(lp, sent2);
//							String sim1 = ss1.simplifiedString(fidx1);
//							String sim2 = ss2.simplifiedString(fidx2);
//							if (sim1.startsWith(", ")) sim1 = sim1.substring(2);
//							if (sim2.startsWith(", ")) sim2 = sim2.substring(2);
//							simpStr += sim1;
//							simpStr += "; ";
//							simpStr += sim2;
//							while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
//							simpStr = simpStr.replace(") (", " ");
//							
//							line += simpStr;
//							line += "|";
//							line += fv.getLabel();
//							
//							System.out.println(line);
						}
							
					} else if (fv.getPairType() == PairType.event_timex) {
						fv = new EventTimexFeatureVector(fv);
						
						String sid1 = fv.getE1().getSentID();
						String sid2 = fv.getE2().getSentID();
						
						if (sid1.equals(sid2)) {	//the same sentence
							etSame ++;
							Sentence s = docTxp.getSentences().get(sid1);
							
							Integer fidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
									docTxp.getTokenArr().indexOf(s.getStartTokID());
							ArrayList<Integer> fidxs2 = new ArrayList<Integer>();
							ArrayList<String> fidxStr2 = new ArrayList<String>();
							int startTmx = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID());
							int endTmx = docTxp.getTokenArr().indexOf(fv.getE2().getEndTokID());
							for (int i=startTmx; i<endTmx+1; i++) {
								fidxs2.add(i-docTxp.getTokenArr().indexOf(s.getStartTokID()));
								fidxStr2.add(docTxp.getTokens().get(docTxp.getTokenArr().get(i)).getText());
							}
							
//							String line = "";
//							String sent = s.toString(docTxp);
////						line += sent + "|" + fidx1 + "|" + fidxs2.get(0) + "|" + fv.getLabel() + "#";
//							
//							line += docTxp.getTokens().get(fv.getE1().getStartTokID()).getText();
//							line += "|";
//							line += String.join(" ", fidxStr2);
//							line += "|";
//							
//							String simpStr = "";
//							SentenceSimplifier ss = new SentenceSimplifier(lp, sent);
//							simpStr += ss.simplifiedString(fidx1, fidxs2);
//							while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
//							simpStr = simpStr.replace(") (", " ");
//							
//							line += simpStr;
//							line += "|";
//							line += fv.getLabel();
//							
//							System.out.println(line);
						
						} else {	//different sentences
							if (((Timex)fv.getE2()).isDct()) {
								etDct ++;
								
							} else {
								etDiff ++;
								Sentence s1 = docTxp.getSentences().get(sid1);
								Sentence s2 = docTxp.getSentences().get(sid2);
								
								Integer fidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
										docTxp.getTokenArr().indexOf(s1.getStartTokID());
								ArrayList<Integer> fidxs2 = new ArrayList<Integer>();
								ArrayList<String> fidxStr2 = new ArrayList<String>();
								int startTmx = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID());
								int endTmx = docTxp.getTokenArr().indexOf(fv.getE2().getEndTokID());
								for (int i=startTmx; i<endTmx+1; i++) {
									fidxs2.add(i-docTxp.getTokenArr().indexOf(s2.getStartTokID()));
									fidxStr2.add(docTxp.getTokens().get(docTxp.getTokenArr().get(i)).getText());
								}
								
//								String line = "";
//								String sent1 = s1.toString(docTxp);
//								String sent2 = s2.toString(docTxp);
////								line += sent1 + "|" + sent2 + "|" + fidx1 + "|" + fidxs2.get(0) + "|" + fv.getLabel() + "#";
//								
//								line += docTxp.getTokens().get(fv.getE1().getStartTokID()).getText();
//								line += "|";
//								line += String.join(" ", fidxStr2);
//								line += "|";
//								
//								String simpStr = "";
//								SentenceSimplifier ss1 = new SentenceSimplifier(lp, sent1);
//								SentenceSimplifier ss2 = new SentenceSimplifier(lp, sent2);
//								simpStr += ss1.simplifiedString(fidx1);
//								simpStr += " ";
//								simpStr += ss2.simplifiedString(fidxs2);
//								while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
//								simpStr = simpStr.replace(") (", " ");
//								
//								line += simpStr;
//								line += "|";
//								line += fv.getLabel();
//																
//								System.out.println(line);
							}
						}
						
					} else if (fv.getPairType() == PairType.timex_timex) {
						tt ++;
					}
					
				}
			}
		}
		System.out.println(eeSame + "-" + eeDiff + "-" + etSame + "-" + etDiff + "-" + etDct + "-" + tt);
	}
	
}
