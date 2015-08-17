package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import parser.entities.*;

public class TXPParser {
	
	public static enum Field {
		token, token_id, sent_id, pos, lemma, 
		deps, tmx_id, tmx_type, tmx_value, ner, ev_class, 
		ev_id, role1, role2, role3, is_arg_pred, has_semrole, 
		chunk, main_verb, connective, morpho, supersense, ss_ner,
		tense_aspect_pol, tense, aspect, pol, coref_event, 
		tlink, clink, tsignal, csignal;
	}
	
	private EntityEnum.Language language;
	private Field[] fields;
	
	private Entity currTimex = null;
	private Entity currEvent = null;
	private Entity currTSignal = null;
	private Entity currCSignal = null;
	private Sentence currSentence = null;
	
	public TXPParser(EntityEnum.Language lang, Field[] fields) {
		this.language = lang;
		this.fields = fields;
	}
	
	public Doc parseDocument(String filepath) throws IOException {
		File f = new File(filepath);
		Doc doc = new Doc(this.language, f.getName());
		
		BufferedReader reader = new BufferedReader(new FileReader(filepath));	
		
		//Read the first 4 lines (comments)
		reader.readLine();
		reader.readLine();
		reader.readLine();
		reader.readLine();
		
		String line;
		while ((line = reader.readLine()) != null) { 
			parseLine(line, doc);
		}
		
		//Add the last sentence
		currSentence.setIndex(doc.getSentIdx()); doc.setSentIdx(doc.getSentIdx() + 1);
		doc.getSentenceArr().add(currSentence.getID());
		doc.getSentences().put(currSentence.getID(), currSentence);
		currSentence = null;
		
		reader.close();
		
		return doc;
	}
	
	private Integer getIndex(Field field) {
		Integer idx = -1;
		ArrayList<Field> arr_fields = new ArrayList<Field>(Arrays.asList(this.fields));
		if (arr_fields.contains(field)) {
			idx = arr_fields.indexOf(field);
		}
		return idx;
	}
	
	private String getMainPosFromMorpho(String morpho) {
        if (!morpho.isEmpty()) {
            String[] morphs = morpho.split("\\+");
            if (morphs.length == 1) {
            	return morphs[0];
            } else {
            	return morphs[1];
            }
        } else {
            return "O";
        }
	}
	
	private String getMainPosFromPos(String pos) {
        if (pos.charAt(0) == 'V') return "v";
        else if (pos.charAt(0) == 'N') return "n";
        else if (pos.charAt(0) == 'A' && pos.charAt(1) == 'T') return "art";
        else if (pos.charAt(0) == 'D') return "det";
        else if (pos.charAt(0) == 'A' && pos.charAt(1) == 'J') return "adj";
        else if (pos.charAt(0) == 'A' && pos.charAt(1) == 'V') return "adv";
        else if (pos.charAt(0) == 'C' && pos.charAt(1) == 'J') return "conj";
        else if (pos.charAt(0) == 'C' && pos.charAt(1) == 'R' && pos.charAt(2) == 'D') return "crd";
        else if (pos.charAt(0) == 'O' && pos.charAt(1) == 'R' && pos.charAt(2) == 'D') return "ord";
        else if (pos.charAt(0) == 'P' && pos.charAt(1) == 'N') return "pron";
        else if (pos.charAt(0) == 'P' && pos.charAt(1) == 'R') return "prep";
        else if (pos.charAt(0) == 'T' && pos.charAt(1) == 'O') return "to";
        else if (pos.charAt(0) == 'P' && pos.charAt(1) == 'O' && pos.charAt(2) == 'S') return "pos";
        else if (pos.charAt(0) == 'P' && pos.charAt(1) == 'U') return "punc";
        else if (pos.charAt(0) == 'X') return "neg";
        return "O";
	}
	
	private Boolean isMainVerb(String mainVerb) {
		if (!mainVerb.isEmpty()) {
			if (mainVerb.equals("mainVb")) return true;
			else return false;
		} else {
			return false;
		}
	}
	
	private Map<String, String> parseDependency(String dep) {
		Map<String, String> dependencies = null; 
        if (!dep.equals("O")) {
        	dependencies = new HashMap<String, String>();
            for (String d : dep.split("\\|\\|")) {
            	String[] dependent = d.split(":");
                String tokendep = dependent[0];
                String deprel = dependent[1];
                dependencies.put(tokendep, deprel);
            }
        }
        return dependencies;
	}
	
	private String[] parseTenseAspectPol(String tense_aspect_pol) {
		String[] arr = {"O", "O", "O"};
        if (!tense_aspect_pol.equals("O") && !tense_aspect_pol.equals("_")) {
        	arr = tense_aspect_pol.split("\\+");
        }
        return arr;
	}
	
	private String[] parseCoreference(String coref) {
		if (!coref.equals("O") && !coref.equals("_")) {
        	return coref.split(":");
        }
        return null;
	}
	
	public void parseLine(String s, Doc doc) {
		ArrayList<String> cols = new ArrayList<String>(Arrays.asList(s.split("\t")));
		//System.out.println(cols.get(0));
		
		if(cols.get(0).contains("DCT_")) {
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			Timex dct = new Timex(tmx_id, "O", "O");
			String type = cols.get(getIndex(Field.tmx_type));
			if (type.contains("B-") || type.contains("I-")) type = type.substring(2);
			dct.setAttributes(type, 
					cols.get(getIndex(Field.tmx_value)));
			dct.setDct(true);
			dct.setEmptyTag(false);
			dct.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
			doc.getEntities().put(tmx_id, dct);
			doc.setDct(dct);
		} else if(cols.get(0).contains("ETX_")) {
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			Timex etx = new Timex(tmx_id, "O", "O");
			String type = cols.get(getIndex(Field.tmx_type));
			if (type.contains("B-") || type.contains("I-")) type = type.substring(2);
			etx.setAttributes(type, 
					cols.get(getIndex(Field.tmx_value)));
			etx.setDct(false);
			etx.setEmptyTag(true);
			etx.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
			doc.getEntities().put(tmx_id, etx);
		} else if(!cols.get(0).isEmpty()) {
			//token basic info
			String tok_id = cols.get(getIndex(Field.token_id));
			Token tok = new Token(tok_id, 
					cols.get(getIndex(Field.sent_id)), 
					cols.get(getIndex(Field.token)));
			tok.setLemmaPosChunk(cols.get(getIndex(Field.lemma)), 
					cols.get(getIndex(Field.pos)), 
					cols.get(getIndex(Field.chunk)));
			tok.setMainPos(getMainPosFromPos(cols.get(getIndex(Field.pos))));
			
			//named entitiy type
			tok.setNamedEntity(cols.get(getIndex(Field.ner)));
			
			//discourse connective
			tok.setDiscourseConn(cols.get(getIndex(Field.connective)));
			
			//dependency info
			tok.setDependencyInfo(isMainVerb(cols.get(getIndex(Field.main_verb))), 
					parseDependency(cols.get(getIndex(Field.deps))));
			
			String tense = "O", aspect = "O", pol = "O";
			//tense, aspect, polarity
			if (getIndex(Field.tense_aspect_pol) != -1) {
				String[] arr = parseTenseAspectPol(cols.get(getIndex(Field.tense_aspect_pol)));
				tense = arr[0];
				aspect = arr[1];
				pol = arr[2];
			} else {
				if (getIndex(Field.tense) != -1) {
					tense = cols.get(getIndex(Field.tense));
				}
				if (getIndex(Field.aspect) != -1) {
					aspect = cols.get(getIndex(Field.aspect));
				}
				if (getIndex(Field.pol) != -1) {
					pol = cols.get(getIndex(Field.pol));
				}
			}
			tok.setTense(tense); tok.setAspect(aspect); tok.setPolarity(pol);
			
			tok.setIndex(doc.getTokIdx()); doc.setTokIdx(doc.getTokIdx() + 1);
			doc.getTokenArr().add(tok_id);
			doc.getTokens().put(tok_id, tok);
			
			//sentence info
			String sent_id = cols.get(getIndex(Field.sent_id));
			if (currSentence == null) {
				currSentence = new Sentence(sent_id, tok_id, tok_id);
			} else if (currSentence != null && sent_id.equals(currSentence.getID())) {
				currSentence.setEndTokID(tok_id);
			} else if (currSentence != null && !sent_id.equals(currSentence.getID())) {
				currSentence.setIndex(doc.getSentIdx()); doc.setSentIdx(doc.getSentIdx() + 1);
				doc.getSentenceArr().add(currSentence.getID());
				doc.getSentences().put(currSentence.getID(), currSentence);
				currSentence = new Sentence(sent_id, tok_id, tok_id);
			}
			
			//entity info
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			String ev_id = cols.get(getIndex(Field.ev_id));
			String tsig_id = null, csig_id = null;
			if (getIndex(Field.tsignal) != -1) {
				tsig_id = cols.get(getIndex(Field.tsignal));
			} else if (getIndex(Field.csignal) != -1) {
				csig_id = cols.get(getIndex(Field.csignal));
			}
			
			//Timex
			if (currTimex == null && !tmx_id.equals("O")) {
				tok.setTimexID(tmx_id);
				currTimex = new Timex(tmx_id, tok_id, tok_id);
				String type = cols.get(getIndex(Field.tmx_type));
				if (type.contains("B-") || type.contains("I-")) type = type.substring(2);
				((Timex)currTimex).setAttributes(type, 
						cols.get(getIndex(Field.tmx_value)));
				((Timex)currTimex).setDct(false);
				((Timex)currTimex).setEmptyTag(false);
			} else if (currTimex != null && tmx_id.equals(currTimex.getID())) {
				tok.setTimexID(tmx_id);
				currTimex.setEndTokID(tok_id);
			} else if (currTimex != null && currTimex instanceof Timex && 
					!tmx_id.equals(currTimex.getID()) &&
					tmx_id.equals("O")) {
				currTimex.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
				currTimex.setSentID(currSentence.getID());
				doc.getEntities().put(currTimex.getID(), currTimex);	
				currSentence.getEntityArr().add(currTimex.getID());
				currTimex = null;
			} else if (currTimex != null && currTimex instanceof Timex && 
					!tmx_id.equals(currTimex.getID()) &&
					!tmx_id.equals("O")) {
				currTimex.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
				currTimex.setSentID(currSentence.getID());
				doc.getEntities().put(currTimex.getID(), currTimex);	
				currSentence.getEntityArr().add(currTimex.getID());				
				
				tok.setTimexID(tmx_id);
				currTimex = new Timex(tmx_id, tok_id, tok_id);
				String type = cols.get(getIndex(Field.tmx_type));
				if (type.contains("B-") || type.contains("I-")) type = type.substring(2);
				((Timex)currTimex).setAttributes(type, 
						cols.get(getIndex(Field.tmx_value)));
				((Timex)currTimex).setDct(false);
				((Timex)currTimex).setEmptyTag(false);
			}
			
			//coreference info
			String[] coref = null;
			if (getIndex(Field.coref_event) != -1) {
				coref = parseCoreference(cols.get(getIndex(Field.coref_event)));
			}
			
			//Event
			if (currEvent == null && !ev_id.equals("O") && tmx_id.equals("O")) {
				tok.setEventID(ev_id);
				currEvent = new Event(ev_id, tok_id, tok_id);
				((Event)currEvent).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
				if (coref != null) {
					for (String c : coref) {
						((Event)currEvent).getCorefList().add(c);
					}
				}
			} else if (currEvent != null && ev_id.equals(currEvent.getID())) {
				tok.setEventID(ev_id);
				currEvent.setEndTokID(tok_id);
			} else if (currEvent != null && currEvent instanceof Event && 
					!ev_id.equals(currEvent.getID()) &&
					ev_id.equals("O")) {
				currEvent.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
				currEvent.setSentID(currSentence.getID());
				doc.getEntities().put(currEvent.getID(), currEvent);
				currSentence.getEntityArr().add(currEvent.getID());
				currEvent = null;
			} else if (currEvent != null && currEvent instanceof Event && 
					!ev_id.equals(currEvent.getID()) &&
					!ev_id.equals("O")) {
				currEvent.setIndex(doc.getEntIdx()); doc.setEntIdx(doc.getEntIdx() + 1);
				currEvent.setSentID(currSentence.getID());
				doc.getEntities().put(currEvent.getID(), currEvent);
				currSentence.getEntityArr().add(currEvent.getID());
				
				tok.setEventID(ev_id);
				currEvent = new Event(ev_id, tok_id, tok_id);
				((Event)currEvent).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
				if (coref != null) {
					for (String c : coref) {
						((Event)currEvent).getCorefList().add(c);
					}
				}
			}
			
			//Temporal signals
			if (tsig_id != null) {
				if (currTSignal == null && !tsig_id.equals("O")) {
					tok.settSignalID(tsig_id);
					currTSignal = new TemporalSignal(tsig_id, tok_id, tok_id);
				} else if (currTSignal != null && tsig_id.equals(currTSignal.getID())) {
					tok.settSignalID(tsig_id);
					currTSignal.setEndTokID(tok_id);
				} else if (currTSignal != null && currTSignal instanceof TemporalSignal && 
						!tsig_id.equals(currTSignal.getID()) &&
						tsig_id.equals("O")) {
					doc.getTemporalSignals().put(tsig_id, ((TemporalSignal)currTSignal));
					currTSignal = null;
				} else if (currTSignal != null && currTSignal instanceof TemporalSignal && 
						!tsig_id.equals(currTSignal.getID()) &&
						!tsig_id.equals("O")) {
					tok.settSignalID(tsig_id);
					currTSignal = new TemporalSignal(tsig_id, tok_id, tok_id);
				}
			}
			
			//Causal signals
			if (csig_id != null) {
				if (currCSignal == null && !csig_id.equals("O")) {
					tok.setcSignalID(csig_id);
					currCSignal = new CausalSignal(csig_id, tok_id, tok_id);
				} else if (currCSignal != null && csig_id.equals(currCSignal.getID())) {
					tok.settSignalID(csig_id);
					currCSignal.setEndTokID(tok_id);
				} else if (currCSignal != null && currCSignal instanceof CausalSignal && 
						!csig_id.equals(currCSignal.getID()) &&
						csig_id.equals("O")) {
					doc.getCausalSignals().put(csig_id, ((CausalSignal)currCSignal));
					currCSignal = null;
				} else if (currCSignal != null && currCSignal instanceof CausalSignal && 
						!csig_id.equals(currCSignal.getID()) &&
						!csig_id.equals("O")) {
					tok.setcSignalID(tsig_id);
					currCSignal = new CausalSignal(csig_id, tok_id, tok_id);
				}
			}
			
			if (!tmx_id.equals("O") || !ev_id.equals("O")) {
				
				String tlinks = null, clinks = null;
				if (getIndex(Field.tlink) != -1 && getIndex(Field.tlink) < cols.size()) {
					tlinks = cols.get(getIndex(Field.tlink));
				}
				if (getIndex(Field.clink) != -1 && getIndex(Field.clink) < cols.size()) {
					clinks = cols.get(getIndex(Field.clink));
				}
				
				//Temporal links
				if (tlinks != null) {
					if (!tlinks.equals("O") && !tlinks.equals("_NULL_")) {
						for (String t : tlinks.split("\\|\\|")) {
			            	String[] tlink_str = t.split(":");
			            	if (tlink_str.length == 3) {
			            		TemporalRelation tlink = new TemporalRelation(tlink_str[0], tlink_str[1]);
			            		tlink.setRelType(tlink_str[2]);
			            		if (!doc.getTlinks().contains(tlink)) {
			            			doc.getTlinks().add(tlink);
			            		}
			            	}
			            }
					}
				}
				
				//Causal links
				if (clinks != null) {
					if (!clinks.equals("O") && !clinks.equals("_NULL_")) {
						for (String c : clinks.split("\\|\\|")) {
							String[] clink_str = c.split(":");
							if (clink_str.length >= 2) {
								CausalRelation clink = new CausalRelation(clink_str[0], clink_str[1]);
								if (!doc.getClinks().contains(clink)) {
									doc.getClinks().add(clink);
								}
							}
						}
					}
				}
			}
			
		}			

	}

}
