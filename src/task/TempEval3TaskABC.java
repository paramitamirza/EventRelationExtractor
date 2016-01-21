package task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import evaluator.PairEvaluator;
import evaluator.TempEval3;
import model.classifier.EventEventRelationClassifier;
import model.classifier.EventTimexRelationClassifier;
import model.classifier.PairClassifier;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.FeatureName;
import model.feature.FeatureEnum.PairType;
import model.rule.EventEventRelationRule;
import model.rule.EventTimexRelationRule;
import model.rule.TimexTimexRelationRule;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.TimeMLParser;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.TimeMLDoc;
import parser.entities.Timex;
import server.RemoteServer;

class TempEval3TaskABC {
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	private List<String> labelList = Arrays.asList(label);
	
	public TempEval3TaskABC() {
	}
	
	public Map<String,String> getTimexTimexRuleRelation(Doc doc) {
		Object[] entArr = doc.getEntities().keySet().toArray();
		Map<String,String> ttlinks = new HashMap<String,String>();
		String pair = null;
		for (int i = 0; i < entArr.length; i++) {
			for (int j = i; j < entArr.length; j++) {
				if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
						doc.getEntities().get(entArr[j]) instanceof Timex) {
					TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
							((Timex)doc.getEntities().get(entArr[j])), doc.getDct(), false);
					if (!timextimex.getRelType().equals("O")) {
						pair = ((String) entArr[i]) + "-" + ((String) entArr[j]);
						ttlinks.put(pair, timextimex.getRelType());
					}
				}
			}
		}
		return ttlinks;
	}
	
	public List<String> getTimexTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		List<String> tt = new ArrayList<String>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<String,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")	//classifying the relation task
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					String st = tlink.getSourceID() + "-" + tlink.getTargetID();
					String ts = tlink.getTargetID() + "-" + tlink.getSourceID();
					if (ttlinks.containsKey(st)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else if (ttlinks.containsKey(ts)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
					} else {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\tNONE");
					}
				}
			}
		}
		return tt;
	}
	
	public List<PairFeatureVector> getEventTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier etRelCls,
			boolean train,
			StringBuilder etRuleRel) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {	
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_timex)) {
					EventTimexFeatureVector etfv = new EventTimexFeatureVector(fv);
					
					//Add features to feature vector
					if (etRelCls.classifier.equals(VectorClassifier.yamcha)) {
						etfv.addToVector(FeatureName.id);
					}
					
					for (FeatureName f : etRelCls.featureList) {
						if (etRelCls.classifier.equals(VectorClassifier.libsvm) ||
								etRelCls.classifier.equals(VectorClassifier.liblinear) ||
								etRelCls.classifier.equals(VectorClassifier.weka)) {
							etfv.addBinaryFeatureToVector(f);
						} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
								etRelCls.classifier.equals(VectorClassifier.none)) {
							etfv.addToVector(f);
						}
					}
					
					if (etRelCls.classifier.equals(VectorClassifier.libsvm) || 
							etRelCls.classifier.equals(VectorClassifier.liblinear)) {
						if (train) etfv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else etfv.addBinaryFeatureToVector(FeatureName.label);
					} else if (etRelCls.classifier.equals(VectorClassifier.yamcha) ||
							etRelCls.classifier.equals(VectorClassifier.weka) ||
							etRelCls.classifier.equals(VectorClassifier.none)){
						if (train) etfv.addToVector(FeatureName.labelCollapsed);
						else etfv.addToVector(FeatureName.label);
					}
					
					//Rules
					EventTimexRelationRule etRule = new EventTimexRelationRule((Event) etfv.getE1(), (Timex) etfv.getE2(), 
							docTxp, etfv.getMateDependencyPath());
					
					if (train && !etfv.getLabel().equals("0")
							&& !etfv.getLabel().equals("NONE")) {
						fvList.add(etfv);
					} else if (!train){ //test
						fvList.add(etfv);	//add all
						if (etRule.equals("O")) etRuleRel.append("0" + "\n");
						else etRuleRel.append((labelList.indexOf(etRule.getRelType())+1) + "\n");
						
//						//add only if with DCT/EmptyTag or in the same sentence
//						if (((Timex) etfv.getE2()).isDct() || ((Timex) etfv.getE2()).isEmptyTag()) {
//							fvList.add(etfv);
//							etRuleRel.append("0" + "\n");
//						} else {
//							if (((Event) etfv.getE1()).getSentID().equals(((Timex) etfv.getE2()).getSentID())) {
//								fvList.add(etfv);
//								if (etRule.equals("O")) etRuleRel.append("0" + "\n");
//								else etRuleRel.append((labelList.indexOf(etRule.getRelType())+1) + "\n");
//							}
//						}
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier etRelCls,
			boolean train,
			StringBuilder etRuleRel) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventTimexTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, etRelCls, train, etRuleRel));
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, PairClassifier eeRelCls,
			boolean train,
			StringBuilder eeRuleRel) throws Exception {
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		TemporalSignalList tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		CausalSignalList csignalList = new CausalSignalList(EntityEnum.Language.EN);
	    
		for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		//for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					) {
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					EventEventFeatureVector eefv = new EventEventFeatureVector(fv);
					
					//Add features to feature vector
					if (eeRelCls.classifier.equals(VectorClassifier.yamcha)) {
						eefv.addToVector(FeatureName.id);
					}
					
					for (FeatureName f : eeRelCls.featureList) {
						if (eeRelCls.classifier.equals(VectorClassifier.libsvm) ||
								eeRelCls.classifier.equals(VectorClassifier.liblinear) ||
								eeRelCls.classifier.equals(VectorClassifier.weka)) {
							eefv.addBinaryFeatureToVector(f);
						} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
								eeRelCls.classifier.equals(VectorClassifier.none)) {
							eefv.addToVector(f);
						}
					}
					
					if (eeRelCls.classifier.equals(VectorClassifier.libsvm) || 
							eeRelCls.classifier.equals(VectorClassifier.liblinear)) {
						if (train) eefv.addBinaryFeatureToVector(FeatureName.labelCollapsed);
						else eefv.addBinaryFeatureToVector(FeatureName.label);
					} else if (eeRelCls.classifier.equals(VectorClassifier.yamcha) ||
							eeRelCls.classifier.equals(VectorClassifier.weka) ||
							eeRelCls.classifier.equals(VectorClassifier.none)){
						if (train) eefv.addToVector(FeatureName.labelCollapsed);
						else eefv.addToVector(FeatureName.label);
					}
					
					//Rules
					EventEventRelationRule eeRule = new EventEventRelationRule((Event) eefv.getE1(), (Event) eefv.getE2(), 
							docTxp, eefv.getMateDependencyPath());
					
					if (train && !eefv.getLabel().equals("0")
							&& !eefv.getLabel().equals("NONE")) {
						fvList.add(eefv);
					} else if (!train){ //test
						fvList.add(eefv);	//add all
						
//						if (eefv.isCoreference()) {	//check if co-refer events
//							eeRuleRel.append((labelList.indexOf("IDENTITY")+1) + "\n");
//						} else {	
							//check if event-event rules applied
							if (eeRule.getRelType().equals("O")) eeRuleRel.append("0" + "\n");
							else eeRuleRel.append((labelList.indexOf(eeRule.getRelType())+1) + "\n");
//						}
					}
				}
			}
		}
		return fvList;
	}
	
	public List<PairFeatureVector> getEventEventTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String dirTxpPath, String dirTmlPath, PairClassifier eeRelCls,
			boolean train,
			StringBuilder eeRuleRel) throws Exception {
		File[] txpFiles = new File(dirTxpPath).listFiles();		
		if (dirTxpPath == null) return null;
		
		List<PairFeatureVector> fvList = new ArrayList<PairFeatureVector>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(dirTmlPath, txpFile.getName().replace(".txp", ""));
			fvList.addAll(getEventEventTlinksPerFile(txpParser, tmlParser, 
					txpFile, tmlFile, eeRelCls, train, eeRuleRel));
		}
		return fvList;
	}
	
	public void writeTimeMLFile(TXPParser txpParser, File txpFile,
			TimeMLParser tmlParser, File tmlFile,
			List<String> ttResult, List<String> etResult, List<String> eeResult,
			String systemTMLPath) 
					throws Exception {
		Doc dTml = tmlParser.parseDocument(tmlFile.getPath());
		Doc dTxp = txpParser.parseDocument(txpFile.getPath());
		
		TimeMLDoc tml = new TimeMLDoc(tmlFile.getPath());
		List<String> tmlText = tml.splitText();		
		tml.removeText();
		tml.removeInstances();
		tml.removeLinks();
		
		/***TEXT***/
		String textStr = "";
		String tid = "", evid = "", tmxid = "";
		String tmlTok = "", txpTok = "";
		int w = 0, t = 0;
		boolean inTml = false;
		Event ev; Timex tmx;
		while (w < tmlText.size()) {
			if (t >= dTxp.getTokenArr().size()) {
				if (tmlText.get(w).isEmpty()) textStr += " ";
				else textStr += tmlText.get(w);
				w ++;
			} else {
				tid = dTxp.getTokenArr().get(t);
				evid = dTxp.getTokens().get(tid).getEventID();
				tmxid = dTxp.getTokens().get(tid).getTimexID();
				
				txpTok = dTxp.getTokens().get(tid).getText();
				txpTok = txpTok.replace("``", "\"");
				txpTok = txpTok.replace("''", "\"");
				txpTok = txpTok.replace("`", "'");
				txpTok = txpTok.replace("-LCB-", "{");
				txpTok = txpTok.replace("-RCB-", "}");
				txpTok = txpTok.replace("-LRB-", "(");
				txpTok = txpTok.replace("-RRB-", ")");
				txpTok = txpTok.replace("-LSB-", "[");
				txpTok = txpTok.replace("-RSB-", "]");
				if (!inTml) {
					tmlTok = tmlText.get(w);
				}
				if (txpTok.equals("DCT")) t ++;
				else if (tmlTok.isEmpty() && w == 0) w ++;
				else {
//					System.out.println("#" + tmlTok + "#" + txpTok);
					if (tmlTok.equals("\n")) {
						textStr += "\n";
						w ++;
					} else if (tmlTok.startsWith("\n")) {
						inTml = true;
						tmlTok = tmlTok.substring(1);
						textStr += "\n";
					} else if (!tmlTok.startsWith(txpTok) 
							&& txpTok.equals(".")) {
						t ++;
					} else if (tmlTok.isEmpty()) {
						textStr += " ";
						w ++;
					} else {
						if (tmlTok.equals(txpTok)) {
							if (!inTml) textStr += " ";
							inTml = false;
							w ++; t ++;
						} else if (tmlTok.startsWith(txpTok)) {
							if (!inTml) textStr += " ";
							inTml = true;
							tmlTok = tmlTok.substring(txpTok.length());
							t ++;
						} 
						if (evid != null) {
							ev = (Event) dTxp.getEntities().get(evid);
							if (tid.equals(ev.getStartTokID()))
								textStr += "<EVENT eid=\""+ev.getID()+"\" class=\""+ev.getEventClass()+"\">";
							textStr += txpTok;
							if (tid.equals(ev.getEndTokID())) 
								textStr += "</EVENT>";
						} else if (tmxid != null) {
							tmx = (Timex) dTxp.getEntities().get(tmxid);
							if (tid.equals(tmx.getStartTokID()))
								textStr += "<TIMEX3 tid=\""+tmx.getID().replace("tmx", "t")+"\" type=\""+tmx.getType()+"\" value=\""+tmx.getValue()+"\">";
							textStr += txpTok;
							if (tid.equals(tmx.getEndTokID()))
								textStr += "</TIMEX3>";
						} else {
							textStr += txpTok;
						}
						
					}
				}
			}
		}
			
		Element text = tml.getDoc().createElement("TEXT");
		text.setTextContent(textStr);
		tml.addText(text);
		
		/***MAKEINSTANCE***/
		for (String ent : dTxp.getEntities().keySet()) {
			Entity e = dTxp.getEntities().get(ent);
			if (e instanceof Event) {
				Element instance = tml.getDoc().createElement("MAKEINSTANCE");
				Attr eid = tml.getDoc().createAttribute("eventID"); eid.setValue(e.getID());
				Attr eiid = tml.getDoc().createAttribute("eiid"); eiid.setValue(e.getID().replace("e", "ei"));
				Attr tense = tml.getDoc().createAttribute("tense"); tense.setValue(((Event) e).getTense());
				Attr aspect = tml.getDoc().createAttribute("aspect"); aspect.setValue(((Event) e).getAspect());
				Attr pol = tml.getDoc().createAttribute("polarity"); pol.setValue(((Event) e).getPolarity().toUpperCase());
				instance.setAttributeNode(eid);
				instance.setAttributeNode(eiid);
				instance.setAttributeNode(tense);
				instance.setAttributeNode(aspect);
				instance.setAttributeNode(pol);
				tml.addInstance(instance);
			}
		}
		
		/***TLINKs***/
		int linkId = 1;
		TemporalRelation tlink = new TemporalRelation();
		for (String ttStr : ttResult) {
			if (!ttStr.isEmpty()) {
				String[] cols = ttStr.split("\t");
				tlink.setSourceID(cols[0].replace("tmx", "t"));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Timex");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String etStr : etResult) {
			if (!etStr.isEmpty()) {
				String[] cols = etStr.split("\t");
				tlink.setSourceID(cols[0].replace("e", "ei"));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String eeStr : eeResult) {
			if (!eeStr.isEmpty()) {
				String[] cols = eeStr.split("\t");
				tlink.setSourceID(cols[0].replace("e", "ei"));
				tlink.setTargetID(cols[1].replace("e", "ei"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Event");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		
		File sysTmlPath = new File(systemTMLPath + "/" + tmlFile.getName());
		PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
		String tmlStr = tml.toString();
		tmlStr = tmlStr.replaceAll("TITLE>\\s+<TEXT", "TITLE>\n\n<TEXT");
		sysTML.write(StringEscapeUtils.unescapeXml(tmlStr));
		sysTML.close();
	}
	
	public static void main(String [] args) throws Exception {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, Field.coref_event, Field.tlink};
		
		TempEval3TaskABC task = new TempEval3TaskABC();
		
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		String evalTxpDirpath = "./data/tempeval3_TXP_TimeML_cl/";
		String evalTmlDirpath = "./data/TempEval3-eval_TML/";
		
		//TimeML directory for system result files
		String systemTMLPath = "data/TempEval3-system_TML";
		File sysDir = new File(systemTMLPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		//Init classifiers
		
		PairClassifier etCls = new EventTimexRelationClassifier("te3", "liblinear");
		PairClassifier eeCls = new EventEventRelationClassifier("te3", "liblinear");
//		PairClassifier eeCls = new EventEventRelationClassifier("te3", "liblinear"
//				,"convprob", "4", "data/probs/indexes-ep100-event-eval-glove-100d.gr4.exp5.csv"
//				);
		
//		RemoteServer rs = new RemoteServer();
		
		//(Delete if exist and) create gold/ and system/ directories in remote server
//		rs.executeCommand("rm -rf ~/data/gold/ && mkdir ~/data/gold/");
//		rs.executeCommand("rm -rf ~/data/system/ && mkdir ~/data/system/");
		
		
		Map<String, StringBuilder> eeTestPerFile = new HashMap<String, StringBuilder>();
		if (eeCls.getFeatureTypeString().equals("convprob")) {
			
			StringBuilder eeRuleTest = new StringBuilder();		
			List<PairFeatureVector> eeFvList = task.getEventEventTlinks(txpParser, tmlParser, 
					evalTxpDirpath, evalTmlDirpath, eeCls, false, eeRuleTest);
			String eeClsTest = eeCls.test(eeFvList);
			List<String> eePairIDs = new ArrayList<String>();
			for (PairFeatureVector pair: eeFvList) {
				eePairIDs.add(pair.getE1().getID() + "\t" + pair.getE2().getID());
			}
			
			String[] eeRuleTestList = eeRuleTest.toString().trim().split("\\r?\\n");
			String[] eeClsTestList = eeClsTest.trim().split("\\r?\\n");
			List<String> eeTestList = new ArrayList<String>();
			
			String eeGold, eePredicted;
			for (int i=0; i<eeClsTestList.length; i++) {
				if (!eeRuleTestList[i].equals("0")) {
					String[] cols = eeClsTestList[i].split("\t");
					eeGold = task.labelList.get(Integer.valueOf(cols[0])-1);
					eePredicted = task.labelList.get(Integer.valueOf(eeRuleTestList[i])-1);
				} else {
					String[] cols = eeClsTestList[i].split("\t");
					eeGold = task.labelList.get(Integer.valueOf(cols[0])-1);
					eePredicted = task.labelList.get(Integer.valueOf(cols[1])-1);
				}
				eeTestList.add(eePairIDs.get(i) + "\t" + eeGold + "\t" + eePredicted);
			}
			
			BufferedReader br = new BufferedReader(new FileReader(new File("data/probs/te3-ee-eval-filenames.csv")));
			String filename;
			int i=0;
		    while ((filename = br.readLine()) != null) {
		    	if (!eeTestPerFile.containsKey(filename)) {
		    		eeTestPerFile.put(filename, new StringBuilder());
		    	} 
		    	eeTestPerFile.put(filename, 
		    			eeTestPerFile.get(filename).append(eeTestList.get(i) + "\n"));
		    	i++;
		    }
		}
		
		File[] txpFiles = new File(evalTxpDirpath).listFiles();
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				File tmlFile = new File(evalTmlDirpath, txpFile.getName().replace(".txp", ""));
				System.out.println(tmlFile.getName());
				
				//timex-timex pairs
				List<String> ttPerFile = task.getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile);
				
				//event-timex pairs
				StringBuilder etRuleTest = new StringBuilder();
				List<PairFeatureVector> etFvList = task.getEventTimexTlinksPerFile(txpParser, tmlParser, 
							txpFile, tmlFile, 
							etCls, false, etRuleTest);
				String etClsTest = etCls.test(etFvList);
				List<String> etPairIDs = new ArrayList<String>();
				for (PairFeatureVector pair: etFvList) {
					etPairIDs.add(pair.getE1().getID() + "\t" + pair.getE2().getID());
				}
				String[] etRuleTestList = etRuleTest.toString().trim().split("\\r?\\n");
				String[] etClsTestList = etClsTest.trim().split("\\r?\\n");
				List<String> etTestList = new ArrayList<String>();
				
				String etGold, etPredicted;
				for (int i=0; i<etClsTestList.length; i++) {
					String[] cols = etClsTestList[i].split("\t");
					if (cols[0].equals("0")) {
						etGold = "NONE";
					} else {
						etGold = task.labelList.get(Integer.valueOf(cols[0])-1);
					}
//					if (!etRuleTestList[i].equals("0")) {
//						etPredicted = task.labelList.get(Integer.valueOf(etRuleTestList[i])-1);
//					} else {
						etPredicted = task.labelList.get(Integer.valueOf(cols[1])-1);
//					}
					etTestList.add(etPairIDs.get(i) + "\t" + etGold + "\t" + etPredicted);
				}
				
				//event-event pairs
				List<String> eeTestList = new ArrayList<String>();
				
				if (eeCls.getFeatureTypeString().equals("convprob")) {
					
					String[] eeTestArray = eeTestPerFile.get(tmlFile.getName()).toString().trim().split("\\r?\\n");
					eeTestList = Arrays.asList(eeTestArray);
				
				} else {
				
					StringBuilder eeRuleTest = new StringBuilder();
					List<PairFeatureVector> eeFvList = task.getEventEventTlinksPerFile(txpParser, tmlParser, 
								txpFile, tmlFile, eeCls, false, eeRuleTest);
					String eeClsTest = eeCls.test(eeFvList);
					List<String> eePairIDs = new ArrayList<String>();
					for (PairFeatureVector pair: eeFvList) {
						eePairIDs.add(pair.getE1().getID() + "\t" + pair.getE2().getID());
					}
					String[] eeRuleTestList = eeRuleTest.toString().trim().split("\\r?\\n");
					String[] eeClsTestList = eeClsTest.trim().split("\\r?\\n");
					
					
					String eeGold, eePredicted;
					for (int i=0; i<eeClsTestList.length; i++) {
						String[] cols = eeClsTestList[i].split("\t");
						if (cols[0].equals("0")) {
							eeGold = "NONE";
						} else {
							eeGold = task.labelList.get(Integer.valueOf(cols[0])-1);
						}
//						if (!eeRuleTestList[i].equals("0")) {
//							eePredicted = task.labelList.get(Integer.valueOf(eeRuleTestList[i])-1);
//						} else {
							eePredicted = task.labelList.get(Integer.valueOf(cols[1])-1);
//						}
						eeTestList.add(eePairIDs.get(i) + "\t" + eeGold + "\t" + eePredicted);
					}
				}
				
				//Write the TimeML document with new TLINKs
				task.writeTimeMLFile(txpParser, txpFile, tmlParser, tmlFile, 
						ttPerFile, etTestList, eeTestList,
						systemTMLPath);
			}
		}
//		rs.disconnect();
		
		TempEval3 te3 = new TempEval3(evalTmlDirpath, systemTMLPath);
		te3.evaluate();	
	}

}
