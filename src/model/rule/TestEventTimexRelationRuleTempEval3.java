package model.rule;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import evaluator.PairEvaluator;
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
import parser.entities.Event;
import parser.entities.Sentence;
import parser.entities.TemporalRelation;
import parser.entities.Timex;

public class TestEventTimexRelationRuleTempEval3 {
	
	public TestEventTimexRelationRuleTempEval3() {
		
	}
	
	public List<String> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		List<String> et = new ArrayList<String>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
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
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
							docTxp, etfv.getMateDependencyPath());
					if (!etRule.getRelType().equals("O")) {
						et.add(etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + 
								etfv.getLabel() + "\t" + etRule.getRelType());
					} else {
						et.add(etfv.getE1().getID() + "\t" + etfv.getE2().getID() + "\t" + 
								etfv.getLabel() + "\tNONE");
					}
				}
			}
		}
		return et;
	}
	
	public List<String> getEventTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath) throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();
		List<String> et = new ArrayList<String>();
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			List<String> etPerFile = getEventTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile);
			et.addAll(etPerFile);
			PairEvaluator pe = new PairEvaluator(etPerFile);
			pe.printIncorrectAndSentence(txpParser, txpFile);
		}		
		return et;
	}

	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
//		String txpDirpath = "./data/TempEval3-train_TXP2/";
//		String tmlDirpath = "./data/TempEval3-train_TML/";
		
		String txpDirpath = "./data/TempEval3-eval_TXP/";
		String tmlDirpath = "./data/TempEval3-eval_TML/";
		
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;	
		
		TestEventTimexRelationRuleTempEval3 test = new TestEventTimexRelationRuleTempEval3();
		List<String> etResult = test.getEventTimexTlinks(txpParser, tmlParser, txpDirpath, tmlDirpath);

		PairEvaluator pe = new PairEvaluator(etResult);
		pe.evaluatePerLabel();   
		
	}
}
