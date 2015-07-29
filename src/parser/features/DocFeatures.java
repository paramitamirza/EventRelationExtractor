package parser.features;

import java.util.ArrayList;

import parser.features.entities.*;

public class DocFeatures {
	
	private String filename;
	private enum Language {EN, IT};
	private Language lang;
	private ArrayList<Token> tokens;
	private ArrayList<Entity> entities;
	private ArrayList<TemporalSignal> temporalSignals;
	private ArrayList<CausalSignal> causalSignals;
	private ArrayList<TemporalRelation> tlinks;
	private ArrayList<CausalRelation> clinks;
	
	public DocFeatures() {
		this.setLang(Language.EN);
	}
	
	public DocFeatures(Language lang) {
		this.setLang(lang);
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public Language getLang() {
		return lang;
	}

	public void setLang(Language lang) {
		this.lang = lang;
	}

	public ArrayList<Token> getTokens() {
		return tokens;
	}

	public void setTokens(ArrayList<Token> tokens) {
		this.tokens = tokens;
	}

	public ArrayList<Entity> getEntities() {
		return entities;
	}

	public void setEntities(ArrayList<Entity> entities) {
		this.entities = entities;
	}

	public ArrayList<TemporalSignal> getTemporalSignals() {
		return temporalSignals;
	}

	public void setTemporalSignals(ArrayList<TemporalSignal> temporalSignals) {
		this.temporalSignals = temporalSignals;
	}

	public ArrayList<CausalSignal> getCausalSignals() {
		return causalSignals;
	}

	public void setCausalSignals(ArrayList<CausalSignal> causalSignals) {
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

}
