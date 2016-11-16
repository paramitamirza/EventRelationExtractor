package simplifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;

public class ExpandWord {
	
	private String[] sentence;
	private List<TypedDependency> typedDepList;
	private Map<Integer, String> expanded;
	
	public ExpandWord() {
		setExpanded(new TreeMap<Integer, String>());
	}
	
	/**
	 * Constructor
	 * @param s sentence string
	 * @param typedDepList list of (Stanford) TypedDependency
	 * @param eidx event index (in the sentence)
	 */
	public ExpandWord(String[] sent, List<TypedDependency> typedDepList) {
		setSentence(sent);
		setTypedDepList(typedDepList);
	}
	
	public String[] getSentence() {
		return sentence;
	}
	
	public void setSentence(String[] sentence) {
		this.sentence = sentence;
	}

	public Map<Integer, String> getExpanded() {
		return expanded;
	}

	public void setExpanded(Map<Integer, String> expanded) {
		this.expanded = expanded;
	}

	public List<TypedDependency> getTypedDepList() {
		return typedDepList;
	}

	public void setTypedDepList(List<TypedDependency> typedDepList) {
		this.typedDepList = typedDepList;
	}
		
	/**
	 * Expanding a noun event
	 * @param nounIdx
	 * @return Map<Integer, String> of index and value (token)
	 * e.g.
	 * embargo --> the(det) political(amod) embargo
	 * stop --> bus stop (compound noun)
	 * comparison --> there will be no comparison
	 * visit --> Pope's visit
	 * attack --> attack of Iraq 
	 */
	public Map<Integer, String> expandNoun(Integer nounIdx, boolean focus, boolean replaceNouns) {
		Map<Integer, String> expanded = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
			rel = d.reln().getShortName();
			
			if (d.gov().index() == nounIdx) {			
				if (focus) expanded.put(d.gov().index(), d.gov().value().toLowerCase());
				else {
					if (replaceNouns 
							&& (d.gov().tag().startsWith("NNP") 
									|| d.gov().tag().startsWith("DT")
									|| d.gov().tag().startsWith("POS")
									|| d.gov().tag().startsWith("IN"))) 
						expanded.put(d.gov().index(), d.gov().tag());
					else expanded.put(d.gov().index(), d.gov().value().toLowerCase());
				}
				
				if (rel.equals("det")
						|| rel.equals("compound")
//						|| rel.equals("amod")
						|| rel.equals("neg")) {
					if (replaceNouns 
							&& (d.dep().tag().startsWith("NNP") 
									|| d.dep().tag().startsWith("DT")
									|| d.dep().tag().startsWith("POS")
									|| d.dep().tag().startsWith("IN"))) 
						expanded.put(d.dep().index(), d.dep().tag());
					else expanded.put(d.dep().index(), d.dep().value().toLowerCase());
					
				} else if (rel.equals("nmod:poss")
						|| rel.equals("nmod")) {
					for (TypedDependency dd : typedDepList) {
						if (dd.gov().index() == d.dep().index()
								&& dd.reln().getShortName().equals("case")
								&& (dd.dep().value().toLowerCase().equals("'s")
									|| dd.dep().value().toLowerCase().equals("of"))) {							
							if (replaceNouns
									&& (d.dep().tag().startsWith("NNP") 
											|| d.dep().tag().startsWith("DT")
											|| d.dep().tag().startsWith("POS")
											|| d.dep().tag().startsWith("IN"))) {
								expanded.put(d.dep().index(), d.dep().tag());
								expanded.put(dd.dep().index(), dd.dep().tag());
							} else {
								expanded.put(d.dep().index(), d.dep().value().toLowerCase());
								expanded.put(dd.dep().index(), dd.dep().value().toLowerCase());
							}
						}
					}
					break;
					
				} else if (rel.equals("expl")) {	//there will be (no) [confrontation]
					expanded.put(d.dep().index(), d.dep().value().toLowerCase());
					for (TypedDependency dd : typedDepList) {
						if (dd.gov().index() == d.gov().index()
								&& (dd.reln().getShortName().equals("aux")
									|| dd.reln().getShortName().equals("cop"))) {
							expanded.put(dd.dep().index(), dd.dep().value().toLowerCase());
						}
					}
				}
				
			} else if (d.dep().index() == nounIdx) {
				if (rel.equals("det")
						|| rel.equals("compound")
						|| rel.equals("amod")) {
					if (replaceNouns 
							&& (d.gov().tag().startsWith("NNP") 
									|| d.gov().tag().startsWith("DT")
									|| d.gov().tag().startsWith("POS")
									|| d.gov().tag().startsWith("IN"))) 
						expanded.put(d.gov().index(), d.gov().tag());
					else expanded.put(d.gov().index(), d.gov().value().toLowerCase());
					
				} else if (rel.equals("nsubj")) {
					if (d.gov().value().toLowerCase().equals("is")
							|| d.gov().value().toLowerCase().equals("are")
							|| d.gov().value().toLowerCase().equals("was")
							|| d.gov().value().toLowerCase().equals("were")) {
						for (TypedDependency dd : typedDepList) {
							if (dd.gov().index() == d.gov().index()
									&& dd.reln().getShortName().equals("expl")) {
								expanded.put(dd.gov().index(), dd.gov().value().toLowerCase());
								expanded.put(dd.dep().index(), dd.dep().value().toLowerCase());
							}
						}
					} 
				} 
			} 
		}
		
		return expanded;
	}	
	
	/**
	 * Expanding a verb event
	 * @param verbIdx
	 * @return Map<Integer, String> of index and value (token)
	 * e.g.
	 * turns --> turns up (compound verb)
	 * retrieved --> has(aux) not(neg) retrieved 
	 */
	public Map<Integer, String> expandVerb(Integer verbIdx) {
		Map<Integer, String> expanded = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
			rel = d.reln().getShortName();
			
			if (d.gov().index() == verbIdx) {				
				expanded.put(d.gov().index(), d.gov().value().toLowerCase());
				
				if (rel.equals("aux")
						|| rel.equals("neg")
						|| rel.equals("compound:prt")
						|| rel.equals("auxpass")
	    			) {
	    			if (d.reln().getSpecific() != null 
	    					&& d.reln().getSpecific() != "prt") {
	    				expanded.put(d.dep().index(), d.reln().getSpecific().replace("_", " ") + " " + d.dep().value().toLowerCase());
	    			} else {
	    				expanded.put(d.dep().index(), d.dep().value().toLowerCase());
	    			}
				} 
				
			} else if (d.dep().index() == verbIdx) {
				if (rel.equals("xcomp")) {
					for (TypedDependency dd : typedDepList) {
	    				if (dd.gov().index() == verbIdx
	    						&& dd.dep().value().toLowerCase().equals("to")) {
	    					expanded.put(d.gov().index(), d.gov().value().toLowerCase());
	    					expanded.put(dd.dep().index(), dd.dep().value().toLowerCase());
	    				}
		    		}
				} else if (rel.equals("conj")
						&& d.gov().tag().startsWith("V")) {
					//(called and) said --> called is put as an empty token, 
					//but the index is needed for recovering connectors, e.g. when, with 'said'
					//because the connectors are usually related only with the first verb (i.e. 'called')
					expanded.put(d.gov().index(), "");
				}
			} 
		}
		
		return expanded;
	}

	/**
	 * Expanding an adjective event
	 * @param adjIdx
	 * @return Map<Integer, String> of index and value (token)
	 * e.g.
	 * afraid --> is(cop) afraid
	 * afraid --> has(aux) been(cop) afraid
	 */
	public Map<Integer, String> expandAdjective(Integer adjIdx) {
		Map<Integer, String> expanded = new TreeMap<Integer, String>();
		
		String rel;
		for (TypedDependency d : typedDepList) {
			rel = d.reln().getShortName();
			
			if (rel.equals("aux")
    				|| rel.equals("cop")
	    		) {
    			expanded.put(d.dep().index(), d.dep().value().toLowerCase());
    		} 
		}
		
		return expanded;
	}
}
