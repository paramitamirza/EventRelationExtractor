package simplifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
import model.feature.CausalSignalList;
import model.feature.TemporalSignalList;
import parser.entities.EntityEnum;

public class SentenceSimplifier {
	
	private LexicalizedParser lexParser;
	private String[] sentence;
	private List<TypedDependency> typedDepList;
	private Map<Integer, String> simplified;
	private boolean debug = false;
	
	private TemporalSignalList tsignalList;
	private CausalSignalList csignalList;
	
	private void initSignals() throws Exception {
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
	}
	
	/**
	 * Constructor
	 * @param lp (Stanford) LexicalizedParser
	 * @param sentence string of sentence
	 * @throws Exception 
	 */
	public SentenceSimplifier(LexicalizedParser lp, String sentence) throws Exception {
		setSimplified(new TreeMap<Integer, String>());
		setLexParser(lp);
		setSentence(sentence.split(" "));
		parseSentence();		
	    initSignals();
	}
	
	public SentenceSimplifier(LexicalizedParser lp, String sentence, boolean debug) throws Exception {
		setSimplified(new TreeMap<Integer, String>());
		this.debug = debug;
		setLexParser(lp);
		setSentence(sentence.split(" "));
		parseSentence();		
	    initSignals();
	}
	
	private void parseSentence() {
		List<CoreLabel> rawWords = edu.stanford.nlp.ling.Sentence.toCoreLabelList(sentence);
	    Tree parse = lexParser.apply(rawWords);
	    
	    TreebankLanguagePack tlp = lexParser.treebankLanguagePack(); // PennTreebankLanguagePack for English
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	    GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
	    typedDepList = gs.typedDependenciesCCprocessed(Extras.MAXIMAL);	    
	}
	
	/**
	 * Simplifying sentence based on only one focus word (e.g. event)
	 * @param fidx index of focus word
	 * @return list of tokens in the simplified sentence
	 */
	public Map<Integer, String> simplify(Integer fidx, boolean replaceNouns) {
		ExpandWord expand = new ExpandWord(sentence, typedDepList);
		
		//Get the simplified sentence based on each focus word
		Map<Integer,String> simp = simplifySentencePerFocusWord(fidx, expand, replaceNouns);
		List<Integer> fidxs = new ArrayList<Integer>();
		for (Integer key : simp.keySet()) {
			fidxs.add(key);
	    	if (!simp.get(key).equals("")) simplified.put(key, simp.get(key));
	    }
	    
		HashMap<Integer,Integer> signals = findEventSignals();
	    
	    //Get the connectors in the sentence based on the focus word
	    Map<Integer, String> connector = simplifyConnectorBegin(fidx, fidxs, expand, signals);
	    for (Integer key : connector.keySet()) {
	    	if (!connector.get(key).equals("")) simplified.put(key, "(" + connector.get(key) + ")");
	    }
	    
	    Map<Integer, String> commas = findCommas(0, fidx);
	    simplified.putAll(commas);
	    
	    return simplified;
	}
	
	public List<String> simplifiedList(Integer fidx, boolean replaceNouns) {
		List<String> sim = new ArrayList<String>();
		for (String val : simplify(fidx, replaceNouns).values()) {
			if (!val.equals("")) sim.add(val);
		}		
		return sim;
	}
	
	public String simplifiedString(Integer fidx, boolean replaceNouns) {
		List<String> sim = simplifiedList(fidx, replaceNouns);
		return String.join(" ", sim);
	}
	
	/**
	 * Simplifying sentence based on two focus words (e.g. pair of events)
	 * @param fidx1 index of 1st focus word
	 * @param fidx2 index of 2nd focus word
	 * @return map of <index in the sentence,token> of the simplified sentence
	 */
	public Map<Integer, String> simplify(Integer fidx1, Integer fidx2, boolean replaceNouns) {
		ExpandWord expand = new ExpandWord(sentence, typedDepList);
		
		//Get the simplified sentence based on each focus word
		//(still taking into account the other focus word)
		Map<Integer,String> simplified1 = simplifySentencePerFocusWord(fidx1, fidx2, expand, replaceNouns);
		Map<Integer,String> simplified2 = simplifySentencePerFocusWord(fidx2, fidx1, expand, replaceNouns);
		List<Integer> fidxs1 = new ArrayList<Integer>();
	    List<Integer> fidxs2 = new ArrayList<Integer>();
		for (Integer key : simplified1.keySet()) {
			fidxs1.add(key);
	    	if (!simplified1.get(key).equals("")) simplified.put(key, simplified1.get(key));
	    }
	    for (Integer key : simplified2.keySet()) {
	    	fidxs2.add(key);
	    	if (!simplified2.get(key).equals("")) simplified.put(key, simplified2.get(key));
	    }
	    
	    HashMap<Integer,Integer> signals = findEventSignals();
	    
	    //Get the connectors in the sentence based on the two focus words
	    Map<Integer, String> connector = simplifyConnector(fidx1, fidx2, fidxs1, fidxs2, expand, signals);
	    for (Integer key : connector.keySet()) {
	    	if (!connector.get(key).equals("")) simplified.put(key, "(" + connector.get(key) + ")");
	    }
	    
	    Map<Integer, String> commas = findCommas(fidx1, fidx2);
	    simplified.putAll(commas);
		
		return simplified;
	}
	
	public List<String> simplifiedList(Integer fidx1, Integer fidx2, boolean replaceNouns) {
		List<String> sim = new ArrayList<String>();
		for (String val : simplify(fidx1, fidx2, replaceNouns).values()) {
			if (!val.equals("")) sim.add(val);
		}		
		return sim;
	}
	
	public String simplifiedString(Integer fidx1, Integer fidx2, boolean replaceNouns) {
		List<String> sim = simplifiedList(fidx1, fidx2, replaceNouns);
		return String.join(" ", sim);
	}
	
	public String simplifiedFocus(Integer fidx1, Integer fidx2, boolean replaceNouns) {
		ExpandWord expand = new ExpandWord(sentence, typedDepList);
		
		//Get the simplified sentence based on each focus word
		//(still taking into account the other focus word)
		Map<Integer,String> simplified1 = simplifySentencePerFocusWord(fidx1, fidx2, expand, replaceNouns);
		for (Integer key : simplified1.keySet()) {
			if (!simplified1.get(key).equals("")) simplified.put(key, simplified1.get(key));
	    }
		
		List<String> sim = new ArrayList<String>();
		for (String val : simplified1.values()) {
			if (!val.equals("")) sim.add(val);
		}
		
		return String.join(" ", sim);
	}
	
	/**
	 * Simplifying sentence based on a word and a phrase 
	 * (e.g. pair of event and time expression)
	 * @param fidx1 index of 1st focus word
	 * @param fidx2 array of index of focus phrase
	 * @return list of tokens in the simplified sentence
	 */
	public Map<Integer, String> simplify(Integer fidx1, ArrayList<Integer> fidx2, boolean replaceNouns) {
		ExpandWord expand = new ExpandWord(sentence, typedDepList);
		
		//Get the simplified sentence based on each focus word
		Map<Integer,String> simp = simplifySentencePerFocusWord(fidx1, expand, replaceNouns);
		List<Integer> fidxs1 = new ArrayList<Integer>();
		List<Integer> fidxs2 = new ArrayList<Integer>();
		for (Integer key : simp.keySet()) {
			fidxs1.add(key);
	    	if (!simp.get(key).equals("")) simplified.put(key, simp.get(key));
	    }
		for (Integer key : fidx2) {
			fidxs2.add(key+1);
	    	simplified.put(key+1, sentence[key]);
	    }		
	    
		HashMap<Integer,Integer> signals = findTimexSignals();
		
	    //Get the connectors in the sentence based on the focus word
	    Map<Integer, String> connector = simplifyConnector(fidx1+1, fidxs2, expand, signals);
	    for (Integer key : connector.keySet()) {
	    	if (!connector.get(key).equals("")) simplified.put(key, "(" + connector.get(key) + ")");
	    }
	    
	    if (fidx2.get(0) < fidx1) {
		    Map<Integer, String> commas = findCommas(fidx2.get(0), fidx1);
		    simplified.putAll(commas);
	    }
	    
	    return simplified;
	}
	
	public List<String> simplifiedList(Integer fidx1, ArrayList<Integer> fidx2, boolean replaceNouns) {
		List<String> sim = new ArrayList<String>();
		for (String val : simplify(fidx1, fidx2, replaceNouns).values()) {
			if (!val.equals("")) sim.add(val);
		}		
		return sim;
	}
	
	public String simplifiedString(Integer fidx1, ArrayList<Integer> fidx2, boolean replaceNouns) {
		List<String> sim = simplifiedList(fidx1, fidx2, replaceNouns);
		return String.join(" ", sim);
	}
	
	/**
	 * Simplifying sentence based on a phrase (e.g. time expression)
	 * @param fidx1 index of 1st focus word
	 * @param fidx2 array of index of focus phrase
	 * @return list of tokens in the simplified sentence
	 */
	public Map<Integer, String> simplify(ArrayList<Integer> fidx) {
		ExpandWord expand = new ExpandWord(sentence, typedDepList);
		
		//Get the simplified sentence based on each focus word
		List<Integer> fidxs = new ArrayList<Integer>();
		for (Integer key : fidx) {
			fidxs.add(key+1);
	    	simplified.put(key+1, sentence[key]);
	    }		
	    
		HashMap<Integer,Integer> signals = findTimexSignals();
		
	    //Get the connectors in the sentence based on the focus word
	    Map<Integer, String> connector = simplifyConnector(-1, fidxs, expand, signals);
	    for (Integer key : connector.keySet()) {
	    	if (!connector.get(key).equals("")) simplified.put(key, "(" + connector.get(key) + ")");
	    }
	    
	    return simplified;
	}
	
	public List<String> simplifiedList(ArrayList<Integer> fidx) {
		List<String> sim = new ArrayList<String>();
		for (String val : simplify(fidx).values()) {
			if (!val.equals("")) sim.add(val);
		}		
		return sim;
	}
	
	public String simplifiedString(ArrayList<Integer> fidx) {
		List<String> sim = simplifiedList(fidx);
		return String.join(" ", sim);
	}

	public LexicalizedParser getLexParser() {
		return lexParser;
	}

	public void setLexParser(LexicalizedParser lexParser) {
		this.lexParser = lexParser;
	}

	public String[] getSentence() {
		return sentence;
	}

	public void setSentence(String[] sentence) {
		this.sentence = sentence;
	}

	public Map<Integer, String> getSimplified() {
		return simplified;
	}

	public void setSimplified(Map<Integer, String> simplified) {
		this.simplified = simplified;
	}

	public List<TypedDependency> getTypedDepList() {
		return typedDepList;
	}

	public void setTypedDepList(List<TypedDependency> typedDepList) {
		this.typedDepList = typedDepList;
	}
	
	/**
	 * find signals (temporal and causal) related to events in the sentence
	 * @param s sentence string
	 * @return map of signals (key:index in the sentence, value: word length)
	 */
	private HashMap<Integer,Integer> findEventSignals() {
		HashMap<Integer,Integer> signals = new HashMap<Integer,Integer>();
		
		String s = String.join(" ", sentence);
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
	
	/**
	 * find signals (temporal) related to time expressions in the sentence
	 * @param s sentence string
	 * @return map of signals (key:index in the sentence, value: word length)
	 */
	private HashMap<Integer,Integer> findTimexSignals() {
		HashMap<Integer,Integer> signals = new HashMap<Integer,Integer>();
		
		String s = String.join(" ", sentence);
		Object[] tsigKeys = tsignalList.getTimexList().keySet().toArray();
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
		
		return signals;
	}
	
	/**
	 * Simplifying sentence per focus word
	 * @param fidx
	 * @return Map<Integer, String> simplified sentence
	 */
	private Map<Integer, String> simplifySentencePerFocusWord(Integer fidx, ExpandWord expand, boolean replaceNouns) {
		Map<Integer, String> sim = new TreeMap<Integer, String>();
		String rel;
		boolean subjFound = false;
		boolean objFound = false;
		
	    for (TypedDependency d : typedDepList) {
	    	if (debug) {
	    		System.out.println(d.gov().value().toLowerCase() + "-" + d.gov().index() + "-" + d.gov().tag() + "|"
	    				+ d.dep().value().toLowerCase() + "-" + d.dep().index()  + "-" + d.dep().tag() + "|"
	    				+ d.reln().getShortName());
	    	}
	    	
	    	rel = d.reln().getShortName();
	    	if (d.gov().index() == fidx+1) {
	    		sim.put(d.gov().index(), d.gov().value().toLowerCase());
	    		
	    		if (d.gov().tag().startsWith("V")) {	//verb
	    			
	    			//e.g. acl(observers-NNS, looking-VBG)
	    			if (d.gov().tag().equals("VBG")) {
	    				for (TypedDependency dd : typedDepList) {
		    				if (dd.dep().index() == d.gov().index()
		    						&& dd.reln().getShortName().contains("acl")) {
		    					Map<Integer, String> expandedNoun = expand.expandNoun(dd.gov().index(), false, replaceNouns);
		    					sim.putAll(expandedNoun);
		    				} 
		    			}
	    			} else if (d.gov().tag().equals("VB")) {
	    				if (rel.equals("mark") && d.dep().tag().equals("TO")) {
	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    				}
	    			} else if (d.gov().tag().equals("VBN")
	    				&& rel.equals("nmod")) {
	    				for (TypedDependency dd : typedDepList) {
		    				if (dd.gov().index() == d.dep().index()
		    						&& dd.dep().value().toLowerCase().equals("by")
		    						&& dd.reln().getShortName().contains("case")) {
		    					sim.put(dd.dep().index(), dd.dep().value().toLowerCase());
		    					Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    					sim.putAll(expandedNoun);
		    				} 
		    			}
	    			}
	    			
	    			if (!subjFound
	    				&& (rel.equals("nsubj")
	    					|| rel.equals("nsubjpass"))){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					if (replaceNouns) sim.put(d.dep().index(), d.dep().tag());
	    					else sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    					
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
//	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    					Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    				sim.putAll(expandedNoun);
			    			
			    			for (TypedDependency dd : typedDepList) {
			    				if (dd.gov().index() == d.dep().index()
		    						&& dd.reln().getShortName().contains("ref")) {
			    					sim.put(dd.dep().index(), dd.dep().value().toLowerCase());
			    				} 
			    			}
	    				}
	    				subjFound = true;
		    			
	    			} if (!objFound
	    				&& rel.equals("dobj")){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					if (replaceNouns) sim.put(d.dep().index(), d.dep().tag());
	    					else sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    					
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
//	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
		    				Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    				sim.putAll(expandedNoun);
	    				}
	    				objFound = true;
		    			
	    			} else {
	    				Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
	    				sim.putAll(expandedVerb);
	    			}
	    			
	    		} else if (d.gov().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expand.expandNoun(d.gov().index(), true, replaceNouns);
	    			sim.putAll(expandedNoun);
	    			
	    		} else if (d.gov().tag().startsWith("J")){
	    			if (rel.equals("aux")
	    				|| rel.equals("cop")
		    			) {
	    				sim.put(d.dep().index(), d.dep().value().toLowerCase());
		    		}
	    		}
	    	} else if (d.dep().index() == fidx+1) {
	    		sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    		rel = d.reln().getShortName();
	    		
	    		if (d.dep().tag().startsWith("V")) {	//verb
	    		
		    		if (rel.equals("xcomp")) {
		    			//sim.put(d.gov().index(), d.gov().value().toLowerCase());
		    			
		    			Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
		    			sim.putAll(expandedVerb);
		    			
		    			for (TypedDependency dd : typedDepList) {
		    				if (dd.gov().index() == d.gov().index()
		    						&& ((dd.reln().getShortName().equals("nsubj")
		    							|| dd.reln().getShortName().equals("nsubjpass")))) {
		    					Map<Integer, String> expandedNoun = expand.expandNoun(dd.dep().index(), false, replaceNouns);
				    			sim.putAll(expandedNoun);
		    				} 
		    			}
		    			
		    		} else if (rel.equals("acl")) {
		    			Map<Integer, String> expandedNoun = expand.expandNoun(d.gov().index(), false, replaceNouns);
		    			sim.putAll(expandedNoun);
		    		}
	    		
	    		} else if (d.dep().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), true, replaceNouns);
	    			sim.putAll(expandedNoun);
	    			
//	    			if (rel.equals("nsubj") 
//	    					|| rel.equals("dobj")
//	    				) {
//	    				Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
//	    				sim.putAll(expandedVerb);
//	    			}
	    		}
	    	}
	    }
	    
	    return sim;
	}

	/**
	 * Simplifying sentence per focus word,
	 * but still considering the other focus word in the sentence
	 * @param fidx1
	 * @param fidx2
	 * @return Map<Integer, String> simplified sentence
	 */
	private Map<Integer, String> simplifySentencePerFocusWord(Integer fidx1, Integer fidx2,
			ExpandWord expand, boolean replaceNouns) {
		Map<Integer, String> sim = new TreeMap<Integer, String>();
		String rel;
		boolean subjFound = false;
		boolean objFound = false;
		
	    for (TypedDependency d : typedDepList) {
	    	if (debug) {
	    		System.out.println(d.gov().value().toLowerCase() + "-" + d.gov().index() + "-" + d.gov().tag() + "|"
	    				+ d.dep().value().toLowerCase() + "-" + d.dep().index()  + "-" + d.dep().tag() + "|"
	    				+ d.reln().getShortName());
	    	}
	    	
	    	rel = d.reln().getShortName();
	    	if (d.gov().index() == fidx1+1) {
	    		sim.put(d.gov().index(), d.gov().value().toLowerCase());
	    		
	    		if (d.gov().tag().startsWith("V")) {	//verb
	    			
	    			//e.g. acl(observers-NNS, looking-VBG)
	    			if (d.gov().tag().equals("VBG")) {
	    				for (TypedDependency dd : typedDepList) {
		    				if (dd.dep().index() == d.gov().index()
		    						&& dd.reln().getShortName().contains("acl")) {
		    					Map<Integer, String> expandedNoun = expand.expandNoun(dd.gov().index(), false, replaceNouns);
		    					sim.putAll(expandedNoun);
		    				} 
		    			}
	    			} else if (d.gov().tag().equals("VB")) {
	    				if (rel.equals("mark") && d.dep().tag().equals("TO")) {
	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    				}
	    			} else if (d.gov().tag().equals("VBN")
	    				&& rel.equals("nmod")) {
	    				for (TypedDependency dd : typedDepList) {
		    				if (dd.gov().index() == d.dep().index()
		    						&& d.dep().index() != fidx2+1
		    						&& dd.dep().value().toLowerCase().equals("by")
		    						&& dd.reln().getShortName().contains("case")) {
		    					sim.put(dd.dep().index(), dd.dep().value().toLowerCase());
		    					Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    					sim.putAll(expandedNoun);
		    				} 
		    			}
	    			}
	    			
	    			if (!subjFound
	    				&& d.dep().index() != fidx2+1
	    				&& (rel.equals("nsubj")
	    					|| rel.equals("nsubjpass"))){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					if (replaceNouns) sim.put(d.dep().index(), d.dep().tag());
	    					else sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    					
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
		    				Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    				sim.putAll(expandedNoun);
			    			
			    			for (TypedDependency dd : typedDepList) {
			    				if (dd.gov().index() == d.dep().index()
		    						&& dd.reln().getShortName().contains("ref")) {
			    					sim.put(dd.dep().index(), dd.dep().value().toLowerCase());
			    				} 
			    			}
	    				}
	    				subjFound = true;
		    			
	    			} if (!objFound
	    				&& d.dep().index() != fidx2+1
	    				&& rel.equals("dobj")){
	    				
	    				if (d.dep().tag().startsWith("PR")
	    					|| d.dep().tag().startsWith("DT")) {
	    					if (replaceNouns) sim.put(d.dep().index(), d.dep().tag());
	    					else sim.put(d.dep().index(), d.dep().value().toLowerCase());
                            
	    				} else if (d.dep().tag().startsWith("N")
	    					|| d.dep().tag().startsWith("J")
	    					|| d.dep().tag().startsWith("C")) {
	    					sim.put(d.dep().index(), d.dep().value().toLowerCase());
		    				Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), false, replaceNouns);
		    				sim.putAll(expandedNoun);
	    				}
	    				objFound = true;
		    			
	    			} else {
	    				Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
	    				sim.putAll(expandedVerb);
	    			}
	    			
	    		} else if (d.gov().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expand.expandNoun(d.gov().index(), true, replaceNouns);
	    			sim.putAll(expandedNoun);
	    			
	    		} else if (d.gov().tag().startsWith("J")){
	    			if (rel.equals("aux")
	    				|| rel.equals("cop")
		    			) {
	    				sim.put(d.dep().index(), d.dep().value().toLowerCase());
		    		}
	    		}
	    	} else if (d.dep().index() == fidx1+1) {
	    		sim.put(d.dep().index(), d.dep().value().toLowerCase());
	    		rel = d.reln().getShortName();
	    		
	    		if (d.dep().tag().startsWith("V")) {	//verb
	    		
		    		if (rel.equals("xcomp")) {
		    			//sim.put(d.gov().index(), d.gov().value().toLowerCase());
		    			Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
		    			sim.putAll(expandedVerb);
		    			
		    			for (TypedDependency dd : typedDepList) {
		    				if (dd.gov().index() == d.gov().index()
		    						&& ((dd.reln().getShortName().equals("nsubj")
		    							|| dd.reln().getShortName().equals("nsubjpass")))) {
		    					Map<Integer, String> expandedNoun = expand.expandNoun(dd.dep().index(), false, replaceNouns);
				    			sim.putAll(expandedNoun);
		    				} 
		    			}
		    			
		    		} else if (rel.equals("acl")) {
		    			Map<Integer, String> expandedNoun = expand.expandNoun(d.gov().index(), false, replaceNouns);
		    			sim.putAll(expandedNoun);
		    		}
	    		
	    		} else if (d.dep().tag().startsWith("N")) {
	    			Map<Integer, String> expandedNoun = expand.expandNoun(d.dep().index(), true, replaceNouns);
	    			sim.putAll(expandedNoun);
	    			
	    			if (rel.equals("nsubj") 
//	    					|| rel.equals("dobj")
	    				) {
	    				if (d.gov().value().toLowerCase().equals("is")
    						|| d.gov().value().toLowerCase().equals("are")
    						|| d.gov().value().toLowerCase().equals("was")
    						|| d.gov().value().toLowerCase().equals("were")
    						|| d.gov().value().toLowerCase().equals("be")
    						|| d.gov().value().toLowerCase().equals("been")
    					) {
	    					sim.put(d.gov().index(), "");
//	    				} else {
//	    					Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
//	    					sim.putAll(expandedVerb);
	    				}
	    			}
	    		}
	    	}
	    }
	    
	    return sim;
	}
	
	private Map<Integer, String> simplifyConnectorBegin(Integer fidx, 
			List<Integer> fidxs,
			ExpandWord expand,
			HashMap<Integer,Integer> signals) {
		Map<Integer, String> mark = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
	    	rel = d.reln().getShortName();
	    	
	    	if ((d.reln().getShortName().equals("case")
	    			|| 	d.reln().getShortName().equals("mark")
	    			||	d.reln().getShortName().equals("advmod")
	    		)
	    		&& fidxs.contains(d.gov().index())
	    		) {
	    		
	    		for (Integer key : signals.keySet()) {
	    			ArrayList<Integer> sigs = new ArrayList<Integer>();
	    			Integer start = new Integer(key);
	    			for (int i=0; i<signals.get(key); i++) {
	    				sigs.add(start);
	    				start ++;
	    			}
	    			if (sigs.contains(d.dep().index()-1)
	    					&& sigs.contains(0)) {
	    				for (int i=0; i<sigs.size(); i++) {
	    					mark.put(sigs.get(i)+1, sentence[sigs.get(i)]);
	    				}
	    				break;
	    			}
	    		}
	    	}
	    }
		
	    return mark;
	}
	
	private Map<Integer, String> simplifyConnector(Integer fidx, 
			List<Integer> fidxs,
			ExpandWord expand,
			HashMap<Integer,Integer> signals) {
		Map<Integer, String> mark = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
	    	rel = d.reln().getShortName();
	    	
	    	if ((d.reln().getShortName().equals("case")
	    			|| 	d.reln().getShortName().equals("mark")
	    			||	d.reln().getShortName().equals("advmod")
	    		)
	    		&& (fidxs.contains(d.gov().index())
	    			|| fidx == d.gov().index())
	    		) {
	    		
	    		if (d.dep().value().toLowerCase().equals("of")
						|| d.dep().value().toLowerCase().equals("'s")) {
					mark.put(d.dep().index(), d.dep().value().toLowerCase());
				}
	    		
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
		
	    return mark;
	}
	
	private Map<Integer, String> simplifyConnector(Integer fidx1, Integer fidx2, 
			List<Integer> fidxs1, List<Integer> fidxs2,
			ExpandWord expand,
			HashMap<Integer,Integer> signals) {
		Map<Integer, String> mark = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
	    	rel = d.reln().getShortName();
	    	
	    	if (d.dep().index() == fidx1+1
	    			&& d.dep().tag().startsWith("N")
	    			&& (rel.equals("nsubj")
	    					|| rel.equals("nsubjpass"))) {
	    		for (TypedDependency dd : typedDepList) {
    				if (dd.dep().index() == fidx2+1
    						&& dd.gov().index() == d.gov().index()) {
    					
    					if (dd.dep().tag().startsWith("N")) {
	    					Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
			    			mark.putAll(expandedVerb);
			    			Map<Integer, String> verbNounMark = getVerbNounConnector(d.gov().index(), dd.dep().index());
			    			mark.putAll(verbNounMark);
    					} else if (dd.dep().tag().startsWith("V")) {
    						Map<Integer, String> expandedVerb = expand.expandVerb(d.gov().index());
			    			mark.putAll(expandedVerb);
    					}
    				}
	    		}
	    	} else if ((fidxs1.contains(d.gov().index()) && d.gov().tag().startsWith("V")
	    					&& fidxs2.contains(d.dep().index()) && d.dep().tag().startsWith("N"))
	    			|| (fidxs2.contains(d.gov().index()) && d.gov().tag().startsWith("V")
	    					&& fidxs1.contains(d.dep().index()) && d.dep().tag().startsWith("N"))) {
	    		Map<Integer, String> verbNounMark = getVerbNounConnector(d.gov().index(), d.dep().index());
    			mark.putAll(verbNounMark);
    			
	    	} else if ((fidxs1.contains(d.gov().index()) && d.gov().tag().startsWith("N")
	    	    			&& fidxs2.contains(d.dep().index()) && d.dep().tag().startsWith("V"))
	    			|| (fidxs2.contains(d.gov().index()) && d.gov().tag().startsWith("N")
	    					&& fidxs1.contains(d.dep().index()) && d.dep().tag().startsWith("V"))) {
	    		Map<Integer, String> verbNounMark = getVerbNounConnector(d.dep().index(), d.gov().index());
    			mark.putAll(verbNounMark);
    			
	    	} else if (d.reln().getShortName().equals("conj")) {
	    		if (fidxs1.contains(d.gov().index()) 
	    				&& fidxs2.contains(d.dep().index())) {
	    			for (TypedDependency dd : typedDepList) {
	    				if (fidxs1.contains(dd.gov().index()) 
	    						&& dd.reln().getShortName().equals("cc")) {
	    					if (!mark.containsValue(dd.dep().value().toLowerCase())) {
	    						mark.put(dd.dep().index(), dd.dep().value().toLowerCase());
	    					}
	    				} else if (fidxs2.contains(dd.gov().index())
	    						&& dd.dep().value().toLowerCase().equals("then")
	    						&& dd.reln().getShortName().equals("advmod")) {
	    					mark.put(dd.dep().index(), dd.dep().value().toLowerCase());
	    				}
	    			}
	    		} 
	    	} else if ((d.reln().getShortName().equals("case")
	    			|| 	d.reln().getShortName().equals("mark")
	    			||	d.reln().getShortName().equals("advmod")
	    		)
	    		&& (fidxs1.contains(d.gov().index())
	    			|| fidxs2.contains(d.gov().index()))
	    		) {
	    		
	    		if (d.dep().value().toLowerCase().equals("in")
						|| d.dep().value().toLowerCase().equals("at")
						|| d.dep().value().toLowerCase().equals("on")
						|| d.dep().value().toLowerCase().equals("from")) {
					mark.put(d.dep().index(), d.dep().value().toLowerCase());
				}
	    		
	    		for (Integer key : signals.keySet()) {
	    			ArrayList<Integer> sigs = new ArrayList<Integer>();
	    			Integer start = new Integer(key);
	    			for (int i=0; i<signals.get(key); i++) {
	    				sigs.add(start);
	    				start ++;
	    			}
	    			if (sigs.contains(d.dep().index()-1)) {
	    				if (sigs.get(0) < fidx2
	    						|| sigs.get(0) == fidxs2.get(fidxs2.size()-1)+1) {
		    				for (int i=0; i<sigs.size(); i++) {
		    					mark.put(sigs.get(i)+1, sentence[sigs.get(i)].toLowerCase());
		    				}
	    				}
	    				break;
	    			}
	    		}
	    	}
	    }
		
	    return mark;
	}

	private Map<Integer, String> getVerbNounConnector(Integer verbidx, Integer nounidx) {
		Map<Integer, String> mark = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
	    	rel = d.reln().getShortName();
	    	
	    	if (d.gov().index() == verbidx
	    			&& d.dep().index() == nounidx) {
	    		if (rel.equals("nmod")) {
		    		for (TypedDependency dd : typedDepList) {
	    				if (dd.gov().index() == nounidx
	    						&& (dd.dep().value().toLowerCase().equals("to")
	    								|| dd.dep().value().toLowerCase().equals("by")
	    								|| dd.dep().value().toLowerCase().equals("about")
	    								|| dd.dep().value().toLowerCase().equals("with"))) {
	    					mark.put(dd.dep().index(), dd.dep().value().toLowerCase());
	    				}
	    			}
	    		} 
	    		
	    	} else if (d.gov().index() == nounidx
	    			&& d.dep().index() == verbidx) {
	    		if (rel.equals("acl:relcl")) {
	    			for (TypedDependency dd : typedDepList) {
	    				if (dd.gov().index() == verbidx
	    						&& dd.reln().getShortName().equals("advmod")
	    						&& dd.dep().tag().startsWith("W")) {
	    					mark.put(dd.dep().index(), dd.dep().value().toLowerCase());
	    				}
	    			}
	    		}
	    		
			} else if (d.gov().index() == nounidx
					&& rel.equals("case")
					&& (d.dep().value().toLowerCase().equals("in")
							|| d.dep().value().toLowerCase().equals("at")
							|| d.dep().value().toLowerCase().equals("on")
							|| d.dep().value().toLowerCase().equals("from"))) {
	    		mark.put(d.dep().index(), d.dep().value().toLowerCase());
	    	} 
		}
		
		return mark;
	}
	
	private Map<Integer, String> findCommas(Integer fidx1, int fidx2) {
		Map<Integer, String> commas = new TreeMap<Integer, String>();
		
		int start = fidx1+1;
		int end = fidx2;
		boolean found = false;
		for (int i=start; i<end; i++) {
			if (sentence[i].equals(",")) {
				if (!found) {
					commas.put(i+1, ",");
					found = !found;
				}
			}
		}
		
		return commas;
	}
}
