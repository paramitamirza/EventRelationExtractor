package debugger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.cmu.ark.AnalysisUtilities;
import edu.cmu.ark.GlobalProperties;
import edu.cmu.ark.Question;
import edu.cmu.ark.SentenceSimplifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.Dependency;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.SimpleTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.FeatureName;
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
import parser.entities.Token;

public class PrintPairs {
	
	private String txpDirpath = "./data/TempEval3-train_TXP2/";
	private String tmlDirpath = "./data/TempEval3-train_TML/";
	
	private static TemporalSignalList tsignalList;
	private static CausalSignalList csignalList;
	
	public PrintPairs(TXPParser txpParser, TimeMLParser tmlParser, LexicalizedParser lp) throws Exception {
		initSignal();
		printPairs(txpParser, tmlParser, txpDirpath, tmlDirpath, lp);
	}
	
	private static void initSignal() throws Exception {
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
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
		initSignal();
		
		String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	    LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);
	    
		String s = "Just the fact that we are doing the job that we are doing makes us role models .";
		int eidx1 = 6;
		int eidx2 = 13;
		HashMap<Integer,Integer> signals = findSignals(s);
		System.out.println(simplifySentence(s, eidx1, eidx2, lp, signals, true));
		
		
//		PrintPairs pp = new PrintPairs(txpParser, tmlParser, lp);
	}
	
	private void printPairs(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath, LexicalizedParser lp) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;		
	    
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			printPairsPerFile(txpParser, tmlParser, txpFile, tmlFile, lp);
		}	
	}
	
	private static Map<Integer, String> expandNoun(List<TypedDependency> tdl, 
			int nounGovIdx) {
		Map<Integer, String> expanded = new HashMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : tdl) {
			rel = d.reln().getShortName();
			
			if (d.gov().index() == nounGovIdx) {				
				expanded.put(d.gov().index(), d.gov().value());
				
				if (rel.equals("det")
					|| rel.equals("compound")
					|| rel.equals("amod")
					|| rel.equals("neg")) {
					expanded.put(d.dep().index(), d.dep().value());
				} else if (rel.equals("nmod:poss")
					|| rel.equals("nmod")) {
					for (TypedDependency dd : tdl) {
						if (dd.gov().index() == d.dep().index()
							&& dd.reln().getShortName().equals("case")
							&& (dd.dep().value().equals("'s")
								|| dd.dep().value().equals("of"))) {
							expanded.put(d.dep().index(), d.dep().value());
							expanded.put(dd.dep().index(), dd.dep().value());
						}
					}
					break;
				} else if (rel.equals("expl")) {	//there will be (no) [confrontation]
					expanded.put(d.dep().index(), d.dep().value());
					for (TypedDependency dd : tdl) {
						if (dd.gov().index() == d.gov().index()) {
							if (dd.reln().getShortName().equals("aux")
								|| dd.reln().getShortName().equals("cop")) {
								expanded.put(dd.dep().index(), dd.dep().value());
							}
						}
					}
				}
			} else if (d.dep().index() == nounGovIdx) {
				if (rel.equals("det")
					|| rel.equals("compound")
					|| rel.equals("amod")) {
					expanded.put(d.gov().index(), d.gov().value());
				} else if (rel.equals("nsubj")) {
					if (d.gov().value().equals("is")
						|| d.gov().value().equals("are")
						|| d.gov().value().equals("was")
						|| d.gov().value().equals("were")) {
						for (TypedDependency dd : tdl) {
							if (dd.gov().index() == d.gov().index()) {
								if (dd.reln().getShortName().equals("expl")) {
									expanded.put(dd.gov().index(), dd.gov().value());
									expanded.put(dd.dep().index(), dd.dep().value());
								}
							}
						}
					} 
				} 
			} 
		}
		
		return expanded;
	}
	
	private static Map<Integer, String> expandVerb(List<TypedDependency> tdl, 
			int verbGovIdx) {
		Map<Integer, String> expanded = new HashMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : tdl) {
			rel = d.reln().getShortName();
			
			if (d.gov().index() == verbGovIdx) {				
				expanded.put(d.gov().index(), d.gov().value());
				
				if (rel.equals("aux")
    				|| rel.equals("neg")
    				|| rel.equals("compound:prt")
    				|| rel.equals("auxpass")
	    				) {
	    			if (d.reln().getSpecific() != null 
	    					&& d.reln().getSpecific() != "prt") {
	    				expanded.put(d.dep().index(), d.reln().getSpecific().replace("_", " ") + " " + d.dep().value());
	    			} else {
	    				expanded.put(d.dep().index(), d.dep().value());
	    			}
				} 
				
			} else if (d.dep().index() == verbGovIdx) {
				if (rel.equals("xcomp")) {
					for (TypedDependency dd : tdl) {
	    				if (dd.gov().index() == verbGovIdx
	    					&& dd.dep().value().equals("to")) {
	    					expanded.put(d.gov().index(), d.gov().value());
	    					expanded.put(dd.dep().index(), dd.dep().value());
	    				}
		    		}
				} else if (rel.equals("conj")
					&& d.gov().tag().startsWith("V")) {
					expanded.put(d.gov().index(), "");
				}
			} 
		}
		
		return expanded;
	}
	
	private static Map<Integer, String> getVerbNounConnector(List<TypedDependency> tdl, 
			int verbidx, int nounidx,
			boolean debug) {
		Map<Integer, String> mark = new HashMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : tdl) {
	    	rel = d.reln().getShortName();
	    	
	    	if (d.gov().index() == verbidx
	    		&& d.dep().index() == nounidx) {
	    		if (rel.equals("nmod")) {
		    		for (TypedDependency dd : tdl) {
	    				if (dd.gov().index() == nounidx
	    					&& (dd.dep().value().toLowerCase().equals("to")
	    						|| dd.dep().value().toLowerCase().equals("by")
	    						|| dd.dep().value().toLowerCase().equals("about")
	    						|| dd.dep().value().toLowerCase().equals("with"))) {
	    					mark.put(dd.dep().index(), dd.dep().value());
	    				}
	    			}
	    		} 
	    		
	    	} else if (d.gov().index() == nounidx
	    		&& d.dep().index() == verbidx) {
	    		if (rel.equals("acl:relcl")) {
	    			for (TypedDependency dd : tdl) {
	    				if (dd.gov().index() == verbidx
	    					&& dd.reln().getShortName().equals("advmod")
	    					&& dd.dep().tag().startsWith("W")) {
	    					mark.put(dd.dep().index(), dd.dep().value());
	    				}
	    			}
	    		}
	    		
			} else if (d.gov().index() == nounidx
	    		&& rel.equals("case")
	    		&& (d.dep().value().toLowerCase().equals("in")
	    			|| d.dep().value().toLowerCase().equals("at")
	    			|| d.dep().value().toLowerCase().equals("on")
	    			|| d.dep().value().toLowerCase().equals("from"))) {
	    		mark.put(d.dep().index(), d.dep().value());
	    	} 
		}
		
		Map<Integer, String> treeMap = new TreeMap<Integer, String>(mark);
	    return treeMap;
	}
	
	private static Map<Integer, String> simplifySentenceConnector(List<TypedDependency> tdl, 
			int eidx1, int eidx2, 
			List<Integer> eidxs1, List<Integer> eidxs2,
			String[] sentence, HashMap<Integer,Integer> signals,
			boolean debug) {
		Map<Integer, String> mark = new HashMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : tdl) {
	    	rel = d.reln().getShortName();
	    	if (d.dep().index() == eidx1+1
	    		&& d.dep().tag().startsWith("N")
	    		&& (rel.equals("nsubj")
	    			|| rel.equals("nsubjpass"))) {
	    		for (TypedDependency dd : tdl) {
    				if (dd.dep().index() == eidx2+1
    					&& dd.gov().index() == d.gov().index()) { 
    					if (dd.dep().tag().startsWith("N")) {
	    					Map<Integer, String> expandedVerb = expandVerb(tdl, d.gov().index());
			    			mark.putAll(expandedVerb);
			    			Map<Integer, String> verbNounMark = getVerbNounConnector(tdl, d.gov().index(), dd.dep().index(), false);
			    			mark.putAll(verbNounMark);
    					} else if (dd.dep().tag().startsWith("V")) {
    						Map<Integer, String> expandedVerb = expandVerb(tdl, d.gov().index());
			    			mark.putAll(expandedVerb);
    					}
    				}
	    		}
	    	} else if ((eidxs1.contains(d.gov().index()) && d.gov().tag().startsWith("V")
	    				&& eidxs2.contains(d.dep().index()) && d.dep().tag().startsWith("N"))
	    			|| (eidxs2.contains(d.gov().index()) && d.gov().tag().startsWith("V")
	    				&& eidxs1.contains(d.dep().index()) && d.dep().tag().startsWith("N"))) {
	    		Map<Integer, String> verbNounMark = getVerbNounConnector(tdl, d.gov().index(), d.dep().index(), false);
    			mark.putAll(verbNounMark);
    			
	    	} else if ((eidxs1.contains(d.gov().index()) && d.gov().tag().startsWith("N")
	    	    		&& eidxs2.contains(d.dep().index()) && d.dep().tag().startsWith("V"))
	    			|| (eidxs2.contains(d.gov().index()) && d.gov().tag().startsWith("N")
	    					&& eidxs1.contains(d.dep().index()) && d.dep().tag().startsWith("V"))) {
	    		Map<Integer, String> verbNounMark = getVerbNounConnector(tdl, d.dep().index(), d.gov().index(), false);
    			mark.putAll(verbNounMark);
    			
	    	} else if (d.reln().getShortName().equals("conj")) {
	    		if (eidxs1.contains(d.gov().index()) 
	    			&& eidxs2.contains(d.dep().index())) {
	    			for (TypedDependency dd : tdl) {
	    				if (eidxs1.contains(dd.gov().index()) 
	    						&& dd.reln().getShortName().equals("cc")) {
	    					if (!mark.containsValue(dd.dep().value())) {
	    						mark.put(dd.dep().index(), dd.dep().value());
	    					}
	    				} else if (eidxs2.contains(dd.gov().index())
	    						&& dd.dep().value().equals("then")
	    						&& dd.reln().getShortName().equals("advmod")) {
	    					mark.put(dd.dep().index(), dd.dep().value());
	    				}
	    			}
	    		} 
	    	} else if ((d.reln().getShortName().equals("case")
	    		|| 	d.reln().getShortName().equals("mark")
	    		||	d.reln().getShortName().equals("advmod")
	    		)
	    		&& (eidxs1.contains(d.gov().index())
	    			|| eidxs2.contains(d.gov().index()))
	    		) {
	    		for (Integer key : signals.keySet()) {
	    			ArrayList<Integer> sigs = new ArrayList<Integer>();
	    			Integer start = new Integer(key);
	    			for (int i=0; i<signals.get(key); i++) {
	    				sigs.add(start);
	    				start ++;
	    			}
	    			if (sigs.contains(d.dep().index()-1)) {
	    				for (int i=0; i<sigs.size(); i++) {
	    					mark.put(sigs.get(i)+1, sentence[sigs.get(i)]);
	    				}
	    				break;
	    			}
	    		}
	    	}
	    }
		
	    Map<Integer, String> treeMap = new TreeMap<Integer, String>(mark);
	    return treeMap;
	}
	
	private static Map<Integer, String> simplifySentencePerEntity(List<TypedDependency> tdl, 
			int eidx1, int eidx2, 
			String[] sentence, HashMap<Integer,Integer> signals,
			boolean debug) {
		Map<Integer, String> simplified = new HashMap<Integer, String>();
		
		String rel;
		boolean subjFound = false;
		boolean objFound = false;
	    for (TypedDependency d : tdl) {
	    	if (debug) {
	    		System.out.println(d.gov().value() + "-" + d.gov().index() + "-" + d.gov().tag() + "|"
	    				+ d.dep().value() + "-" + d.dep().index()  + "-" + d.dep().tag() + "|"
	    				+ d.reln().getShortName());
	    	}
	    	
	    	rel = d.reln().getShortName();
	    	if (d.gov().index() == eidx1+1) {
	    		simplified.put(d.gov().index(), d.gov().value());
	    		
	    		if (d.gov().tag().startsWith("V")) {	//verb
	    			
	    			//e.g. acl(observers-NNS, looking-VBG)
	    			if (d.gov().tag().equals("VBG")) {
	    				for (TypedDependency dd : tdl) {
		    				if (dd.dep().index() == d.gov().index()
		    						&& dd.reln().getShortName().contains("acl")) {
		    					Map<Integer, String> expandedNoun = expandNoun(tdl, dd.gov().index());
				    			simplified.putAll(expandedNoun);
		    				} 
		    			}
	    			} else if (d.gov().tag().equals("VB")) {
	    				if (rel.equals("mark") && d.dep().tag().equals("TO")) {
	    					simplified.put(d.dep().index(), d.dep().value());
	    				}
	    			} else if (d.gov().tag().equals("VBN")
	    				&& rel.equals("nmod")) {
	    				for (TypedDependency dd : tdl) {
		    				if (dd.gov().index() == d.dep().index()
		    						&& d.dep().index() != eidx2+1
		    						&& dd.dep().value().equals("by")
		    						&& dd.reln().getShortName().contains("case")) {
		    					simplified.put(dd.dep().index(), dd.dep().value());
		    					simplified.put(d.dep().index(), d.dep().value());
		    				} 
		    			}
	    			}
	    			
	    			if (!subjFound
	    				&& d.dep().index() != eidx2+1
	    				&& (rel.equals("nsubj")
	    					|| rel.equals("nsubjpass"))){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					simplified.put(d.dep().index(), d.dep().value());
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
	    					simplified.put(d.dep().index(), d.dep().value());
		    				Map<Integer, String> expandedNoun = expandNoun(tdl, d.dep().index());
			    			simplified.putAll(expandedNoun);
			    			
			    			for (TypedDependency dd : tdl) {
			    				if (dd.gov().index() == d.dep().index()
		    						&& dd.reln().getShortName().contains("ref")) {
			    					simplified.put(dd.dep().index(), dd.dep().value());
			    				} 
			    			}
	    				}
	    				subjFound = true;
		    			
	    			} if (!objFound
	    				&& d.dep().index() != eidx2+1
	    				&& rel.equals("dobj")){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					simplified.put(d.dep().index(), d.dep().value());
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
	    					simplified.put(d.dep().index(), d.dep().value());
		    				Map<Integer, String> expandedNoun = expandNoun(tdl, d.dep().index());
			    			simplified.putAll(expandedNoun);
	    				}
	    				objFound = true;
		    			
	    			} else {
	    				Map<Integer, String> expandedVerb = expandVerb(tdl, d.gov().index());
		    			simplified.putAll(expandedVerb);
	    			}
	    			
	    		} else if (d.gov().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expandNoun(tdl, d.gov().index());
	    			simplified.putAll(expandedNoun);
	    			
	    		} else if (d.gov().tag().startsWith("J")){
	    			if (rel.equals("aux")
	    				|| rel.equals("cop")
		    			) {
		    			simplified.put(d.dep().index(), d.dep().value());
		    		}
	    		}
	    	} else if (d.dep().index() == eidx1+1) {
	    		simplified.put(d.dep().index(), d.dep().value());
	    		rel = d.reln().getShortName();
	    		
	    		if (d.dep().tag().startsWith("V")) {	//verb
	    		
		    		if (rel.equals("xcomp")) {
		    			simplified.put(d.gov().index(), d.gov().value());
		    		} else if (rel.equals("acl")) {
		    			Map<Integer, String> expandedNoun = expandNoun(tdl, d.gov().index());
		    			simplified.putAll(expandedNoun);
		    		}
	    		
	    		} else if (d.dep().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expandNoun(tdl, d.dep().index());
	    			simplified.putAll(expandedNoun);
	    			
	    			if (rel.equals("nsubj") || rel.equals("dobj")) {
	    				if (d.gov().value().equals("is")
    						|| d.gov().value().equals("are")
    						|| d.gov().value().equals("was")
    						|| d.gov().value().equals("were")
    						|| d.gov().value().equals("be")
    						|| d.gov().value().equals("been")
    					) {
	    					simplified.put(d.gov().index(), "");
	    				} else {
	    					Map<Integer, String> expandedVerb = expandVerb(tdl, d.gov().index());
	    					simplified.putAll(expandedVerb);
	    				}
	    			}
	    		}
	    	}
	    }
	    
	    Map<Integer, String> treeMap = new TreeMap<Integer, String>(simplified);
	    return treeMap;
	    
//	    String simplifiedStr = "";
//	    int start = ((int)treeMap.keySet().toArray()[0]);
//	    if (start > 1 && sentence[start-2].equals(",")) simplifiedStr += ", ";
//	    for (String str : treeMap.values()) {
//	    	simplifiedStr += str + " ";
//		}
//	    
//	    return simplifiedStr.substring(0, simplifiedStr.length()-1);
	}
	
	private static Map<Integer, String> findCommas(int eidx1, int eidx2, 
			String[] sentence) {
		Map<Integer, String> commas = new HashMap<Integer, String>();
		
		int start = eidx1+1;
		int end = eidx2;
		for (int i=start; i<end; i++) {
			if (sentence[i].equals(",")) {
				commas.put(i+1, ",");
			}
		}
		
		Map<Integer, String> treeMap = new TreeMap<Integer, String>(commas);
	    return treeMap;
	}
	
	private static Map<Integer, String> combineSimplified(Map<Integer, String> entity1, Map<Integer, String> entity2) {
		Map<Integer, String> combined = new TreeMap<Integer, String>();
		
		return combined;
	}
	
	private static String simplifySentence(String s, 
			int eidx1, int eidx2, LexicalizedParser lp,
			HashMap<Integer,Integer> signals, boolean debug) {
		List<CoreLabel> rawWords = edu.stanford.nlp.ling.Sentence.toCoreLabelList(s.split(" "));
	    Tree parse = lp.apply(rawWords);
	    
	    TreebankLanguagePack tlp = lp.treebankLanguagePack(); // PennTreebankLanguagePack for English
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	    GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
	    List<TypedDependency> tdl = gs.typedDependenciesCCprocessed(Extras.MAXIMAL);
	    
	    String[] sentence = s.split(" ");
	    
	    Map<Integer, String> combined = new TreeMap<Integer, String>();
	    Map<Integer, String> simplified1 = simplifySentencePerEntity(tdl, eidx1, eidx2, sentence, signals, debug);
	    Map<Integer, String> simplified2 = simplifySentencePerEntity(tdl, eidx2, eidx1, sentence, signals, debug);
	    
	    List<Integer> eidxs1 = new ArrayList<Integer>();
	    List<Integer> eidxs2 = new ArrayList<Integer>();
	    String simplifiedStr = "";
	    for (Integer key : simplified1.keySet()) {
	    	eidxs1.add(key);
	    	simplifiedStr += simplified1.get(key) + " ";
	    }
	    simplifiedStr += " | ";
    	for (Integer key : simplified2.keySet()) {
    		eidxs2.add(key);
	    	simplifiedStr += simplified2.get(key) + " ";
	    }
	    
	    //combined.putAll(simplified1); combined.putAll(simplified2);	  
	    for (Integer key : simplified1.keySet()) {
	    	if (!simplified1.get(key).equals("")) combined.put(key, simplified1.get(key));
	    }
	    for (Integer key : simplified2.keySet()) {
	    	if (!simplified2.get(key).equals("")) combined.put(key, simplified2.get(key));
	    }
	    Map<Integer, String> connector = simplifySentenceConnector(tdl, 
	    		eidx1, eidx2, eidxs1, eidxs2, sentence, signals, debug);
	    for (Integer key : connector.keySet()) {
	    	combined.put(key, "(" + connector.get(key) + ")");
	    }
	    Map<Integer, String> commas = findCommas(eidx1, eidx2, sentence);
	    combined.putAll(commas);
	    
    	simplifiedStr += " | ";
    	for (Integer key : combined.keySet()) {
    		simplifiedStr += combined.get(key) + " ";
	    }
    	simplifiedStr = simplifiedStr.replace(", , ", "");
    	simplifiedStr = simplifiedStr.replace(") (", " ");
	    return simplifiedStr.trim();
	}
	
	private static HashMap<Integer,Integer> findSignals(String s) {
		HashMap<Integer,Integer> signals = new HashMap<Integer,Integer>();
		
		Object[] tsigKeys = tsignalList.getEventList().keySet().toArray();
		Arrays.sort(tsigKeys, Collections.reverseOrder());
		for (Object tsig : tsigKeys) {
			String tsignal = ((String)tsig) + " ";
			if (s.toLowerCase().startsWith(tsignal)) {
				int sigStartIdx = 0;
				int sigNumTok = tsignal.trim().split(" ").length;
				if (!signals.containsKey(sigStartIdx)) {
					signals.put(sigStartIdx, sigNumTok);
				}
				//System.out.println(tsignal.trim() + "-" + sigStartIdx + "-" + sigNumTok);
			} else if (s.contains(" " + tsignal)) {
				Pattern p = Pattern.compile(" " + tsignal);
				Matcher m = p.matcher(s.toLowerCase());
				while (m.find()) {
					int sigStart = m.start();
					String sub = s.substring(0, sigStart+1);
					int sigStartIdx = sub.length() - sub.replace(" ", "").length();
					int sigNumTok = ((String)tsig).split(" ").length;				
					if (!signals.containsKey(sigStartIdx)) {
						signals.put(sigStartIdx, sigNumTok);
					}
					//System.out.println(tsignal.trim() + "-" + sigStartIdx + "-" + sigNumTok);
				}
			}
		}
		Object[] csigKeys = csignalList.getList().keySet().toArray();
		Arrays.sort(csigKeys, Collections.reverseOrder());
		for (Object csig : csigKeys) {
			String csignal = ((String)csig) + " ";
			if (s.toLowerCase().startsWith(csignal)) {
				int sigStartIdx = 0;
				int sigNumTok = csignal.trim().split(" ").length;
				if (!signals.containsKey(sigStartIdx)) {
					signals.put(sigStartIdx, sigNumTok);
				}
				//System.out.println(csignal.trim() + "-" + sigStartIdx + "-" + sigNumTok);
			} else if (s.contains(" " + csignal)) {
				Pattern p = Pattern.compile(" " + csignal);
				Matcher m = p.matcher(s.toLowerCase());
				while (m.find()) {
					int sigStart = m.start();
					String sub = s.substring(0, sigStart+1);
					int sigStartIdx = sub.length() - sub.replace(" ", "").length();
					int sigNumTok = ((String)csig).split(" ").length;				
					if (!signals.containsKey(sigStartIdx)) {
						signals.put(sigStartIdx, sigNumTok);
					}
					//System.out.println(csignal.trim() + "-" + sigStartIdx + "-" + sigNumTok);
				}				
			}
		}
		
		return signals;
	}
	
	private void printPairsPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile,
			LexicalizedParser lp) throws Exception {
		
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
					
					String sid1 = e1.getSentID();
					String sid2 = e2.getSentID();
					
					if (sid1.equals(sid2)) {	//the same sentence
						Sentence s = docTxp.getSentences().get(sid1);
						
						int eidx1 = docTxp.getTokenArr().indexOf(fv.getE1().getStartTokID()) - 
								docTxp.getTokenArr().indexOf(s.getStartTokID());
						int eidx2 = docTxp.getTokenArr().indexOf(fv.getE2().getStartTokID()) - 
								docTxp.getTokenArr().indexOf(s.getStartTokID());
						
						
						System.out.println(s.toString(docTxp) + "|" + eidx1 + "|" + eidx2 + "|" + fv.getLabel());
						
						HashMap<Integer,Integer> signals = findSignals(s.toString(docTxp));
						System.out.println(simplifySentence(s.toString(docTxp), eidx1, eidx2, lp, signals, false));
					}
						
				} else if (fv.getPairType() == PairType.event_timex) {
					
					
				} else if (fv.getPairType() == PairType.timex_timex) {
					
				}
				
			}
		}
	}
	
	
}
