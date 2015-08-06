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

	public EventEventFeatureVector(Document doc, Entity e1, Entity e2, String label, TemporalSignalList tempSignalList, CausalSignalList causalSignalList) {
		super(doc, e1, e2, label, tempSignalList, causalSignalList);
		orderPair();
	}
	
	public EventEventFeatureVector(FeatureVector fv) {
		super(fv.getDoc(), fv.getE1(), fv.getE2(), fv.getVectors(), fv.getLabel(), fv.getTempSignalList(), fv.getCausalSignalList());
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
		if ((feature == Feature.tense || feature == Feature.aspect || feature == Feature.polarity) && 
				((Event)e).getAttribute(feature).equals("O")) {
			String relatedTid = null;
			if (doc.getTokens().get(e.getStartTokID()).getMainPos().equals("n")) {
				relatedTid = getMateVerbFromSbjNoun(e.getStartTokID());
				if (relatedTid == null) relatedTid = getMateVerbFromObjNoun(e.getStartTokID());
			} else if (doc.getTokens().get(e.getStartTokID()).getMainPos().equals("adj")) {
				relatedTid = getMateVerbFromAdj(e.getStartTokID());
			}
			if (relatedTid != null) {
				if (feature == Feature.tense) return doc.getTokens().get(relatedTid).getTense();
				else if (feature == Feature.aspect) return doc.getTokens().get(relatedTid).getAspect();
				else if (feature == Feature.polarity) return doc.getTokens().get(relatedTid).getPolarity();
			}
		}
		if (((Event)e).getAttribute(feature).equals("O")) return "NONE";
		else return ((Event)e).getAttribute(feature);
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
		entityAttrs.add(getEntityAttribute(e1, Feature.tense) + "-" + getEntityAttribute(e1, Feature.aspect) + "|" + 
				getEntityAttribute(e2, Feature.tense) + "-" + getEntityAttribute(e2, Feature.aspect));
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
				if (getMateCoordVerb(govID) != null) {
					generateDependencyPath(getMateCoordVerb(govID), tokenArr2, paths, "", visited);
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
				if (getMateCoordVerb(govID) != null) {
					generateDependencyPath(getMateCoordVerb(govID), tokenArr1, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
			
			tokenArr1.clear();
			tokenArr2.clear();			
			
			if (getTokenAttribute(e1, Feature.mainpos).equals("v")) {
				tokenArr1.add(getMateHeadVerb(tokenID1));
			} else if (getTokenAttribute(e1, Feature.mainpos).equals("adj") &&
				getMateVerbFromAdj(tokenID1) != null) {
				tokenArr1.add(getMateVerbFromAdj(tokenID1));
			} else {
				tokenArr1.add(tokenID1);
			}
			if (getTokenAttribute(e2, Feature.mainpos).equals("v")) {
				tokenArr2.add(getMateHeadVerb(tokenID2));
			} else if (getTokenAttribute(e2, Feature.mainpos).equals("adj") &&
				getMateVerbFromAdj(tokenID2) != null) {
				tokenArr2.add(getMateVerbFromAdj(tokenID2));
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
				if (getMateCoordVerb(govID) != null) {
					generateDependencyPath(getMateCoordVerb(govID), tokenArr2, paths, "", visited);
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
				if (getMateCoordVerb(govID) != null) {
					generateDependencyPath(getMateCoordVerb(govID), tokenArr1, paths, "", visited);
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
	
	public ArrayList<String> getTemporalMarker() throws IOException {
		ArrayList<String> tMarkers = new ArrayList<String>();
		Marker m = super.getTemporalConnective();
		if (m.getText().equals("O")) m = super.getTemporalSignal();
		tMarkers.add(m.getCluster().replace(" ", "_") + "|" + m.getPosition());
		tMarkers.add(m.getDepRelE1() + "|" + m.getDepRelE2());
		return tMarkers;
	}
	
	public ArrayList<String> getCausalMarker() throws IOException {
		ArrayList<String> tMarkers = new ArrayList<String>();
		Marker m = super.getCausalConnective();
		if (m.getText().equals("O")) m = super.getCausalSignal();
		if (m.getText().equals("O")) m = super.getCausalVerb();
		tMarkers.add(m.getCluster().replace(" ", "_") + "|" + m.getPosition());
		tMarkers.add(m.getDepRelE1() + "|" + m.getDepRelE2());
		return tMarkers;
	}

}
