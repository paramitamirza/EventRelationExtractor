package parser.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Document {
	
	private String filename;
	private EntityEnum.Language lang;
	private Map<String, Token> tokens;
	private Map<String, Sentence> sentences;
	private ArrayList<Entity> entities;
	private Map<String, TemporalSignal> temporalSignals;
	private Map<String, CausalSignal> causalSignals;
	private Map<String, TemporalRelation> tlinks;
	private Map<String, CausalRelation> clinks;
	
	public Document() {
		this.setLang(EntityEnum.Language.EN);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entities = new ArrayList<Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new HashMap<String, TemporalRelation>();
		clinks = new HashMap<String, CausalRelation>();
	}
	
	public Document(EntityEnum.Language lang) {
		this.setLang(lang);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entities = new ArrayList<Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new HashMap<String, TemporalRelation>();
		clinks = new HashMap<String, CausalRelation>();
	}
	
	public Document(EntityEnum.Language lang, String filename) {
		this.setLang(lang);
		this.setFilename(filename);
		tokens = new HashMap<String, Token>();
		sentences = new HashMap<String, Sentence>();
		entities = new ArrayList<Entity>();
		temporalSignals = new HashMap<String, TemporalSignal>();
		causalSignals = new HashMap<String, CausalSignal>();
		tlinks = new HashMap<String, TemporalRelation>();
		clinks = new HashMap<String, CausalRelation>();
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

	public ArrayList<Entity> getEntities() {
		return entities;
	}

	public void setEntities(ArrayList<Entity> entities) {
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

	public Map<String, TemporalRelation> getTlinks() {
		return tlinks;
	}

	public void setTlinks(Map<String, TemporalRelation> tlinks) {
		this.tlinks = tlinks;
	}

	public Map<String, CausalRelation> getClinks() {
		return clinks;
	}

	public void setClinks(Map<String, CausalRelation> clinks) {
		this.clinks = clinks;
	}
}
