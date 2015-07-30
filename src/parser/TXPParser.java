package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import parser.entities.*;

public class TXPParser {
	
	public static enum Field {
		token, token_id, sent_id, pos, lemma, 
		deps, tmx_id, tmx_type, tmx_value, ner, ev_class, 
		ev_id, role1, role2, role3, is_arg_pred, has_semrole, 
		chunk, main_verb, connective, morpho, supersense,
		tense_aspect_pol, tense, aspect, pol, coref_event, 
		tlink, clink, tsignal, csignal;
	}
	
	private EntityEnum.Language language;
	private Field[] fields;
	
	private Entity currEntity = null;
	private Sentence currSentence = null;
	
	public TXPParser(EntityEnum.Language lang, Field[] fields) {
		this.language = lang;
		this.fields = fields;
	}
	
	public Document parseDocument(String filepath) throws IOException {
		File f = new File(filepath);
		Document doc = new Document(this.language, f.getName());
		
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
		doc.getSentenceArr().add(currSentence.getID());
		doc.getSentences().put(currSentence.getID(), currSentence);
		currSentence = null;
		
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
	
	public void parseLine(String s, Document doc) {
		ArrayList<String> cols = new ArrayList<String>(Arrays.asList(s.split("\t")));
		//System.out.println(cols.get(0));
		
		if(cols.get(0).contains("DCT_")) {
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			Timex dct = new Timex(tmx_id, "O", "O");
			dct.setAttributes(cols.get(getIndex(Field.tmx_type)), 
					cols.get(getIndex(Field.tmx_value)));
			doc.getEntityArr().add(tmx_id);
			doc.getEntities().put(tmx_id, dct);
		} else if(!cols.get(0).isEmpty()) {
			//token basic info
			String tok_id = cols.get(getIndex(Field.token_id));
			Token tok = new Token(tok_id, 
					cols.get(getIndex(Field.sent_id)), 
					cols.get(getIndex(Field.token)));
			tok.setLemmaPosChunk(cols.get(getIndex(Field.lemma)), 
					cols.get(getIndex(Field.pos)), 
					cols.get(getIndex(Field.chunk)));
			tok.setMainPos(getMainPosFromMorpho(cols.get(getIndex(Field.morpho))));
			
			//named entitiy type
			tok.setNamedEntity(cols.get(getIndex(Field.ner)));
			
			//discourse connective
			tok.setDiscourseConn(cols.get(getIndex(Field.connective)));
			
			//dependency info
			tok.setDependencyInfo(isMainVerb(cols.get(getIndex(Field.main_verb))), 
					parseDependency(cols.get(getIndex(Field.deps))));
			
			//entity info
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			String ev_id = cols.get(getIndex(Field.ev_id));
			String tsig_id = null, csig_id = null;
			if (getIndex(Field.csignal) != -1) {
				tsig_id = cols.get(getIndex(Field.tsignal));
			} else if (getIndex(Field.csignal) != -1) {
				csig_id = cols.get(getIndex(Field.csignal));
			}
			
			doc.getTokenArr().add(tok_id);
			doc.getTokens().put(tok_id, tok);
			
			//sentence info
			String sent_id = cols.get(getIndex(Field.sent_id));
			if (currSentence == null) {
				currSentence = new Sentence(sent_id, tok_id, tok_id);
			} else if (currSentence != null && sent_id.equals(currSentence.getID())) {
				currSentence.setEndTokID(tok_id);
			} else if (currSentence != null && !sent_id.equals(currSentence.getID())) {
				doc.getSentenceArr().add(currSentence.getID());
				doc.getSentences().put(currSentence.getID(), currSentence);
				currSentence = new Sentence(sent_id, tok_id, tok_id);
			}
			
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
			
			//Timex
			if (currEntity == null && !tmx_id.equals("O")) {				
				tok.setTimexID(tmx_id);
				currEntity = new Timex(tmx_id, tok_id, tok_id);
				((Timex)currEntity).setAttributes(cols.get(getIndex(Field.tmx_type)), 
						cols.get(getIndex(Field.tmx_value)));					
			} else if (currEntity != null && tmx_id.equals(currEntity.getID())) {
				tok.setTimexID(tmx_id);
				currEntity.setEndTokID(tok_id);
			} else if (currEntity != null && currEntity instanceof Timex && 
					!tmx_id.equals(currEntity.getID()) &&
					tmx_id.equals("O")) {
				doc.getEntityArr().add(currEntity.getID());
				doc.getEntities().put(currEntity.getID(), currEntity);
				currEntity = null;
			} else if (currEntity != null && currEntity instanceof Timex && 
					!tmx_id.equals(currEntity.getID()) &&
					!tmx_id.equals("O")) {
				tok.setTimexID(tmx_id);
				currEntity = new Timex(tmx_id, tok_id, tok_id);
				((Timex)currEntity).setAttributes(cols.get(getIndex(Field.tmx_type)), 
						cols.get(getIndex(Field.tmx_value)));
			}
			
			//coreference info
			String[] coref = null;
			if (getIndex(Field.coref_event) != -1) {
				coref = parseCoreference(cols.get(getIndex(Field.coref_event)));
			}
			
			//Event
			if (currEntity == null && !ev_id.equals("O") && tmx_id.equals("O")) {
				tok.setEventID(ev_id);
				currEntity = new Event(ev_id, tok_id, tok_id);
				((Event)currEntity).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
				if (coref != null) {
					for (String c : coref) {
						((Event)currEntity).getCorefList().add(c);
					}
				}
			} else if (currEntity != null && ev_id.equals(currEntity.getID())) {
				tok.setEventID(ev_id);
				currEntity.setEndTokID(tok_id);
			} else if (currEntity != null && currEntity instanceof Event && 
					!ev_id.equals(currEntity.getID()) &&
					ev_id.equals("O")) {
				doc.getEntityArr().add(currEntity.getID());
				doc.getEntities().put(currEntity.getID(), currEntity);
				currEntity = null;
			} else if (currEntity != null && currEntity instanceof Event && 
					!ev_id.equals(currEntity.getID()) &&
					!ev_id.equals("O")) {
				tok.setEventID(ev_id);
				currEntity = new Event(ev_id, tok_id, tok_id);
				((Event)currEntity).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
				if (coref != null) {
					for (String c : coref) {
						((Event)currEntity).getCorefList().add(c);
					}
				}
			}
			
			//Temporal signals
			if (tsig_id != null) {
				if (currEntity == null && !tsig_id.equals("O")) {
					tok.settSignalID(tsig_id);
					currEntity = new TemporalSignal(tsig_id, tok_id, tok_id);
				} else if (currEntity != null && tsig_id.equals(currEntity.getID())) {
					tok.settSignalID(tsig_id);
					currEntity.setEndTokID(tok_id);
				} else if (currEntity != null && currEntity instanceof TemporalSignal && 
						!tsig_id.equals(currEntity.getID()) &&
						tsig_id.equals("O")) {
					doc.getTemporalSignals().put(tsig_id, ((TemporalSignal)currEntity));
					currEntity = null;
				} else if (currEntity != null && currEntity instanceof TemporalSignal && 
						!tsig_id.equals(currEntity.getID()) &&
						!tsig_id.equals("O")) {
					tok.settSignalID(tsig_id);
					currEntity = new TemporalSignal(tsig_id, tok_id, tok_id);
				}
			}
			
			//Causal signals
			if (csig_id != null) {
				if (currEntity == null && !csig_id.equals("O")) {
					tok.setcSignalID(csig_id);
					currEntity = new CausalSignal(csig_id, tok_id, tok_id);
				} else if (currEntity != null && csig_id.equals(currEntity.getID())) {
					tok.settSignalID(csig_id);
					currEntity.setEndTokID(tok_id);
				} else if (currEntity != null && currEntity instanceof CausalSignal && 
						!csig_id.equals(currEntity.getID()) &&
						csig_id.equals("O")) {
					doc.getCausalSignals().put(csig_id, ((CausalSignal)currEntity));
					currEntity = null;
				} else if (currEntity != null && currEntity instanceof CausalSignal && 
						!csig_id.equals(currEntity.getID()) &&
						!csig_id.equals("O")) {
					tok.setcSignalID(tsig_id);
					currEntity = new CausalSignal(csig_id, tok_id, tok_id);
				}
			}
			
			if (!tmx_id.equals("O") || !ev_id.equals("O")) {
				
				String tlinks = null, clinks = null;
				if (getIndex(Field.tlink) != -1) {
					tlinks = cols.get(getIndex(Field.tlink));
				}
				if (getIndex(Field.clink) != -1) {
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
