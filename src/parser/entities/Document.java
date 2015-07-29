package parser.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Document {
	
	private String filename;
	private EntityEnum.Language lang;
	private Map<String, Token> tokens;
	private Map<String, Sentence> sentences;
	private ArrayList<Entity> entityArr;
	private Map<String, Entity> entities;
	private Map<String, TemporalSignal> temporalSignals;
	private Map<String, CausalSignal> causalSignals;
	private ArrayList<TemporalRelation> tlinks;
	private ArrayList<CausalRelation> clinks;
	
	public Document() {
		this.setLang(EntityEnum.Language.EN);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entityArr = new ArrayList<Entity>();
		entities = new HashMap<String, Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new ArrayList<TemporalRelation>();
		clinks = new ArrayList<CausalRelation>();
	}
	
	public Document(EntityEnum.Language lang) {
		this.setLang(lang);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entityArr = new ArrayList<Entity>();
		entities = new HashMap<String, Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new ArrayList<TemporalRelation>();
		clinks = new ArrayList<CausalRelation>();
	}
	
	public Document(EntityEnum.Language lang, String filename) {
		this.setLang(lang);
		this.setFilename(filename);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entityArr = new ArrayList<Entity>();
		entities = new HashMap<String, Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new ArrayList<TemporalRelation>();
		clinks = new ArrayList<CausalRelation>();
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public EntityEnum.Language getLang() {
		return lang;
	}

	public void setLang(EntityEnum.Language lang) {
		this.lang = lang;
	}

	public Map<String, Token> getTokens() {
		return tokens;
	}

	public void setTokens(Map<String, Token> tokens) {
		this.tokens = tokens;
	}

	public Map<String, Sentence> getSentences() {
		return sentences;
	}

	public void setSentences(Map<String, Sentence> sentences) {
		this.sentences = sentences;
	}

	public Map<String, Entity> getEntities() {
		return entities;
	}

	public void setEntities(Map<String, Entity> entities) {
		this.entities = entities;
	}

	public Map<String, TemporalSignal> getTemporalSignals() {
		return temporalSignals;
	}

	public void setTemporalSignals(Map<String, TemporalSignal> temporalSignals) {
		this.temporalSignals = temporalSignals;
	}

	public Map<String, CausalSignal> getCausalSignals() {
		return causalSignals;
	}

	public void setCausalSignals(Map<String, CausalSignal> causalSignals) {
		this.causalSignals = causalSignals;
	}

	public ArrayList<TemporalRelation> getTlinks() {
		return tlinks;
	}

	public void setTlinks(ArrayList<TemporalRelation> tlinks) {
		this.tlinks = tlinks;
	}

	public ArrayList<CausalRelation> getClinks() {
		return clinks;
	}

	public void setClinks(ArrayList<CausalRelation> clinks) {
		this.clinks = clinks;
	}

	public ArrayList<Entity> getEntityArr() {
		return entityArr;
	}

	public void setEntityArr(ArrayList<Entity> entity_arr) {
		this.entityArr = entity_arr;
	}
}
