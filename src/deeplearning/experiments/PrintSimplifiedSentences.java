package deeplearning.experiments;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

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
import simplifier.SentenceSimplifier;

public class PrintSimplifiedSentences {
	
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
		String tmlDirpath = "./data/TempEval3-train_TML_deduced/";
		
//		String txpDirpath = "./data/TempEval3-eval_TXP/";
//		String tmlDirpath = "./data/TempEval3-eval_TML/";
		
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
	    
	    boolean replaceNouns = true;
	    
//	    File eeLblFile = new File("./data/tokens/te3-ee-train-labels.txt");
//		File eeFile = new File("./data/tokens/te3-ee-train-sentences.txt");
//		if (replaceNouns) {
//			eeLblFile = new File("./data/tokens/te3-ee-train-labels-nn.txt");
//			eeFile = new File("./data/tokens/te3-ee-train-sentences-nn.txt");
//	    }
		
		File eeLblFile = new File("./data/tokens/te3-ee-train-deduced-labels.txt");
		File eeFile = new File("./data/tokens/te3-ee-train-deduced-sentences.txt");
		if (replaceNouns) {
			eeLblFile = new File("./data/tokens/te3-ee-train-deduced-labels-nn.txt");
			eeFile = new File("./data/tokens/te3-ee-train-deduced-sentences-nn.txt");
	    }
		
//	    File eeLblFile = new File("./data/tokens/te3-ee-eval-labels.txt");
//		File eeFile = new File("./data/tokens/te3-ee-eval-sentences.txt");
//	    if (replaceNouns) {
//			eeLblFile = new File("./data/tokens/te3-ee-eval-labels-nn.txt");
//			eeFile = new File("./data/tokens/te3-ee-eval-sentences-nn.txt");
//	    } 		
		
		PrintWriter eeLblPw = new PrintWriter(eeLblFile.getPath());
		PrintWriter eePw = new PrintWriter(eeFile.getPath());
				
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			
			Doc docTxp = txpParser.parseDocument(txpFile.getPath());
			Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
			
			System.err.println(txpFile.getName());
			
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
							
							eeLblPw.write(fv.getLabel() + "\n");
							
							String sent = s.toString(docTxp);
							String simpStr = "";
							SentenceSimplifier ss = new SentenceSimplifier(lp, sent);
							simpStr += ss.simplifiedString(fidx1, fidx2, replaceNouns);
							while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
							
							simpStr = simpStr.replaceAll("[()]", "");
							
							if (replaceNouns) {
								simpStr = simpStr.replaceAll("NNPS", "NNP");
								simpStr = simpStr.replaceAll("NNS", "NN");
								simpStr = simpStr.replaceAll("(NNP )+NNP", "NNP");
								simpStr = simpStr.replaceAll("(NN )+NN", "NN");
								simpStr = simpStr.replaceAll("NN IN NN", "NN");
								simpStr = simpStr.replaceAll("NNP IN NNP", "NNP");
								simpStr = simpStr.replaceAll("NN POS NN", "NN");
								simpStr = simpStr.replaceAll("NNP POS NNP", "NNP");
								simpStr = simpStr.replaceAll("NNP POS NN", "DT NN");
								simpStr = simpStr.replaceAll("NNP NN", "NN");
							}
							
							eePw.write(simpStr + "\n");
						
						} else {	//different sentences
							eeDiff ++;
							Sentence s1 = docTxp.getSentences().get(sid1);
							Sentence s2 = docTxp.getSentences().get(sid2);
							
							Integer fidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
									docTxp.getTokenArr().indexOf(s1.getStartTokID());
							Integer fidx2 = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID()) - 
									docTxp.getTokenArr().indexOf(s2.getStartTokID());
							
							eeLblPw.write(fv.getLabel() + "\n");
							
							String sent1 = s1.toString(docTxp);
							String sent2 = s2.toString(docTxp);
							String simpStr = "";
							SentenceSimplifier ss1 = new SentenceSimplifier(lp, sent1);
							SentenceSimplifier ss2 = new SentenceSimplifier(lp, sent2);
							String sim1 = ss1.simplifiedString(fidx1, replaceNouns);
							String sim2 = ss2.simplifiedString(fidx2, replaceNouns);
							if (sim1.startsWith(", ")) sim1 = sim1.substring(2);
							if (sim2.startsWith(", ")) sim2 = sim2.substring(2);
							simpStr += sim1;
							simpStr += " ; ";
							simpStr += sim2;
							while (simpStr.startsWith(", ")) simpStr = simpStr.substring(2);
							
							simpStr = simpStr.replaceAll("[()]", "");
							
							if (replaceNouns) {
								simpStr = simpStr.replaceAll("NNPS", "NNP");
								simpStr = simpStr.replaceAll("NNS", "NN");
								simpStr = simpStr.replaceAll("(NNP )+NNP", "NNP");
								simpStr = simpStr.replaceAll("(NN )+NN", "NN");
								simpStr = simpStr.replaceAll("NN IN NN", "NN");
								simpStr = simpStr.replaceAll("NNP IN NNP", "NNP");
								simpStr = simpStr.replaceAll("NN POS NN", "NN");
								simpStr = simpStr.replaceAll("NNP POS NNP", "NNP");
								simpStr = simpStr.replaceAll("NNP POS NN", "DT NN");
								simpStr = simpStr.replaceAll("NNP NN", "NN");
							}
							
							eePw.write(simpStr + "\n");
						}
							
					} else if (fv.getPairType() == PairType.event_timex) {
						
					} else if (fv.getPairType() == PairType.timex_timex) {
						
					}
					
				}
			}
		}
		System.out.println(eeSame + "-" + eeDiff + "-" + etSame + "-" + etDiff + "-" + etDct + "-" + tt);
		
		eeLblPw.close();
		eePw.close();
	}
}
