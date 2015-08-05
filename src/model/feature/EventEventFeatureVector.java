package model.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import parser.entities.Document;
import parser.entities.Entity;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.RelatednessCalculator;
import edu.cmu.lti.ws4j.impl.Lin;
import model.feature.FeatureEnum.Feature;

public class EventEventFeatureVector extends FeatureVector{

	public EventEventFeatureVector(Document doc, Entity e1, Entity e2, String label, SignalList signalList) {
		super(doc, e1, e2, label, signalList);
		orderPair();
	}
	
	public EventEventFeatureVector(FeatureVector fv) {
		super(fv.getDoc(), fv.getE1(), fv.getE2(), fv.getVectors(), fv.getLabel(), fv.getSignalList());
		orderPair();
	}
	
	public void orderPair() {
		//if in timex-event order, switch!
		if (getOrder().equals("AFTER")) {
			Entity temp = e1;
			this.setE1(e2);
			this.setE2(temp);
			this.setLabel(TemporalRelation.getInverseRelation(label));
		}
	}
	
	public Double getWordSimilarity() {
		ILexicalDatabase db = new NictWordNet();
		RelatednessCalculator rc = new Lin(db);
		return rc.calcRelatednessOfWords(getTokenAttribute(e1, Feature.lemma), getTokenAttribute(e2, Feature.lemma));
	}
	
	protected String getEntityAttribute(Entity e, Feature feature) {
		return ((Event)e).getAttribute(feature);
	}
	
	public ArrayList<String> getEntityAttributes() {
		ArrayList<String> entityAttrs = new ArrayList<String>();
		entityAttrs.add(getEntityAttribute(e1, Feature.eventClass));
		entityAttrs.add(getEntityAttribute(e2, Feature.eventClass));
		entityAttrs.add(getEntityAttribute(e1, Feature.tense));
		entityAttrs.add(getEntityAttribute(e2, Feature.tense));
		entityAttrs.add(getEntityAttribute(e1, Feature.aspect));
		entityAttrs.add(getEntityAttribute(e2, Feature.aspect));
		entityAttrs.add(getEntityAttribute(e1, Feature.polarity));
		entityAttrs.add(getEntityAttribute(e2, Feature.polarity));
		return entityAttrs;
	}
	
	public ArrayList<String> getCombinedEntityAttributes() {
		ArrayList<String> entityAttrs = new ArrayList<String>();
		entityAttrs.add(getEntityAttribute(e1, Feature.eventClass) + "|" + getEntityAttribute(e2, Feature.eventClass));
		entityAttrs.add(getEntityAttribute(e1, Feature.tense) + "|" + getEntityAttribute(e2, Feature.tense));
		entityAttrs.add(getEntityAttribute(e1, Feature.aspect) + "|" + getEntityAttribute(e2, Feature.aspect));
		entityAttrs.add(getEntityAttribute(e1, Feature.polarity) + "|" + getEntityAttribute(e2, Feature.polarity));
		return entityAttrs;
	}
	
	public ArrayList<String> getSameEntityAttributes() {
		ArrayList<String> entityAttrs = new ArrayList<String>();
		entityAttrs.add(getEntityAttribute(e1, Feature.eventClass).equals(getEntityAttribute(e2, Feature.eventClass)) ? "TRUE" : "FALSE");
		entityAttrs.add(getEntityAttribute(e1, Feature.tense).equals(getEntityAttribute(e2, Feature.tense)) ? "TRUE" : "FALSE");
		entityAttrs.add(getEntityAttribute(e1, Feature.aspect).equals(getEntityAttribute(e2, Feature.aspect)) ? "TRUE" : "FALSE");
		entityAttrs.add(getEntityAttribute(e1, Feature.polarity).equals(getEntityAttribute(e2, Feature.polarity)) ? "TRUE" : "FALSE");
		return entityAttrs;
	}
	
	public Boolean isCoreference() {
		return ((Event)e1).getCorefList().contains(e2.getID());
	}
	
	public String getMateDependencyPath() {
		//Assuming that event is always single-token
		if (isSameSentence()) {
			ArrayList<String> tokenArr1 = new ArrayList<String>();
			ArrayList<String> tokenArr2 = new ArrayList<String>();
			String tokenID1 = e1.getStartTokID();
			String tokenID2 = e2.getStartTokID();
			
			tokenArr1.add(tokenID1);
			tokenArr2.add(tokenID2);
			
			for (String govID : tokenArr1) {
				List<String> paths = new ArrayList<String>();
				List<String> visited = new ArrayList<String>();
				generateDependencyPath(govID, tokenArr2, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
				if (getCoordVerb(govID) != null) {
					generateDependencyPath(getCoordVerb(govID), tokenArr2, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
			for (String govID : tokenArr2) {
				List<String> paths = new ArrayList<String>();
				List<String> visited = new ArrayList<String>();
				generateDependencyPath(govID, tokenArr1, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
				if (getCoordVerb(govID) != null) {
					generateDependencyPath(getCoordVerb(govID), tokenArr1, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
			
			tokenArr1.clear();
			tokenArr2.clear();			
			
			if (getTokenAttribute(e1, Feature.mainpos).equals("v")) {
				tokenArr1.add(getHeadVerb(tokenID1));
			} else if (getTokenAttribute(e1, Feature.mainpos).equals("adj") &&
				getVerbFromAdj(tokenID1) != null) {
				tokenArr1.add(getVerbFromAdj(tokenID1));
			} else {
				tokenArr1.add(tokenID1);
			}
			if (getTokenAttribute(e2, Feature.mainpos).equals("v")) {
				tokenArr2.add(getHeadVerb(tokenID2));
			} else if (getTokenAttribute(e2, Feature.mainpos).equals("adj") &&
				getVerbFromAdj(tokenID2) != null) {
				tokenArr2.add(getVerbFromAdj(tokenID2));
			} else {
				tokenArr2.add(tokenID2);
			}
			
			for (String govID : tokenArr1) {
				List<String> paths = new ArrayList<String>();
				List<String> visited = new ArrayList<String>();
				generateDependencyPath(govID, tokenArr2, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
				if (getCoordVerb(govID) != null) {
					generateDependencyPath(getCoordVerb(govID), tokenArr2, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
			for (String govID : tokenArr2) {
				List<String> paths = new ArrayList<String>();
				List<String> visited = new ArrayList<String>();
				generateDependencyPath(govID, tokenArr1, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
				if (getCoordVerb(govID) != null) {
					generateDependencyPath(getCoordVerb(govID), tokenArr1, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
		} 
		return "O";
	}
	
	public ArrayList<String> getMateMainVerb() {
		ArrayList<String> mainVerbs = new ArrayList<String>();
		mainVerbs.add(super.getMateMainVerb(e1));
		mainVerbs.add(super.getMateMainVerb(e2));
		return mainVerbs;
	}
	
	public ArrayList<String> getTemporalSignal() throws IOException {
		ArrayList<String> tSignals = new ArrayList<String>();
		Marker me1 = super.getTemporalSignal(e1);
		Marker me2 = super.getTemporalSignal(e2);
		tSignals.add(me1.getText());
		tSignals.add(me1.getPosition());
		tSignals.add(me1.getDepRel());
		tSignals.add(me2.getText());
		tSignals.add(me2.getPosition());
		tSignals.add(me2.getDepRel());
		return tSignals;
	}
	
	public ArrayList<String> getTemporalConnective() {
		ArrayList<String> tSignals = new ArrayList<String>();
		Marker me1 = super.getTemporalConnective(e1);
		Marker me2 = super.getTemporalConnective(e2);
		tSignals.add(me1.getText());
		tSignals.add(me1.getPosition());
		tSignals.add(me1.getDepRel());
		tSignals.add(me2.getText());
		tSignals.add(me2.getPosition());
		tSignals.add(me2.getDepRel());
		return tSignals;
	}

}
