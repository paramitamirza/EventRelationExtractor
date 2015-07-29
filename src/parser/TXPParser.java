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
		tense_aspect_pol, tense, aspect, pol, coevent, 
		tlink, clink, tsignal, csignal;
	}
	
	private EntityEnum.Language language;
	private Field[] fields;
	
	private Entity currEntity = null;
	
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
		
		for (Entity ent : doc.getEntities()) {
			if (ent instanceof Timex) {
				System.out.println(ent.getID() + "\tTimex");
			} else if (ent instanceof Event) {
				System.out.println(ent.getID() + "\tEvent");
			}
		}
		System.out.println();
		
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
        if (tense_aspect_pol.equals("O") && tense_aspect_pol.equals("_")) {
        	arr = tense_aspect_pol.split("\\+");
        }
        return arr;
	}
	
	public void parseLine(String s, Document doc) {
		ArrayList<String> cols = new ArrayList<String>(Arrays.asList(s.split("\t")));
		//System.out.println(cols.get(0));
		
		if(cols.get(0).contains("DCT_")) {
			String tmx_id = cols.get(getIndex(Field.tmx_id));
			Timex dct = new Timex(tmx_id, "O", "O");
			dct.setAttributes(cols.get(getIndex(Field.tmx_type)), 
					cols.get(getIndex(Field.tmx_value)));
			doc.getEntities().add(dct);
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
			if (getIndex(Field.csignal) != -1) {
				String tsig_id = cols.get(getIndex(Field.tsignal));
			} else if (getIndex(Field.csignal) != -1) {
				String csig_id = cols.get(getIndex(Field.csignal));
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
			
			if (currEntity == null && !tmx_id.equals("O")) {				
				tok.setTimexID(tmx_id);
				currEntity = new Timex(tmx_id, tok_id, tok_id);
				((Timex)currEntity).setAttributes(cols.get(getIndex(Field.tmx_type)), 
						cols.get(getIndex(Field.tmx_value)));					
			} else if (currEntity != null && tmx_id.equals(currEntity.getID())) {
				tok.setTimexID(tmx_id);
				currEntity.setEndTokID(tok_id);
			} else if (currEntity != null && !tmx_id.equals(currEntity.getID()) &&
					tmx_id.equals("O")) {
				doc.getEntities().add(currEntity);
				currEntity = null;
			} else if (currEntity != null && !tmx_id.equals(currEntity.getID()) &&
					!tmx_id.equals("O")) {
				tok.setTimexID(tmx_id);
				currEntity = new Timex(tmx_id, tok_id, tok_id);
				((Timex)currEntity).setAttributes(cols.get(getIndex(Field.tmx_type)), 
						cols.get(getIndex(Field.tmx_value)));
			}
			
			if (currEntity == null && !ev_id.equals("O") && tmx_id.equals("O")) {
				tok.setEventID(ev_id);
				currEntity = new Event(ev_id, tok_id, tok_id);
				((Event)currEntity).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
			} else if (currEntity != null && ev_id.equals(currEntity.getID())) {
				tok.setEventID(ev_id);
				currEntity.setEndTokID(tok_id);
			} else if (currEntity != null && !ev_id.equals(currEntity.getID()) &&
					ev_id.equals("O")) {
				doc.getEntities().add(currEntity);
				currEntity = null;
			} else if (currEntity != null && !ev_id.equals(currEntity.getID()) &&
					!ev_id.equals("O")) {
				tok.setEventID(ev_id);
				currEntity = new Event(ev_id, tok_id, tok_id);
				((Event)currEntity).setAttributes(cols.get(getIndex(Field.ev_class)), 
						tense, aspect, pol);
			}
			
			/**
			} else if (!cols.get(getIndex(Field.ev_id)).equals("O")) {
				tok.setEventID(cols.get(getIndex(Field.ev_id)));
			} else if (getIndex(Field.tsignal) != -1 && 
					!cols.get(getIndex(Field.tsignal)).equals("O")) {
				tok.settSignalID(cols.get(getIndex(Field.tsignal)));
			} else if (getIndex(Field.csignal) != -1 && 
					!cols.get(getIndex(Field.csignal)).equals("O")) {
				tok.settSignalID(cols.get(getIndex(Field.csignal)));
			}
			**/
			
		}			

	}

}
