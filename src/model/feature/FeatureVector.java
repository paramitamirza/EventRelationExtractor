package model.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.feature.FeatureEnum.*;
import parser.entities.*;

public class FeatureVector {
	
	protected Document doc;
	private ArrayList<String> vectors;
	protected Entity e1;
	protected Entity e2;
	protected String label;
	private PairType pairType;
	private SignalList signalList;
	
	public FeatureVector(Document doc, Entity e1, Entity e2, String label, SignalList signalList) {
		this.setDoc(doc);
		this.setE1(e1);
		this.setE2(e2);
		this.setLabel(label);
		this.setVectors(new ArrayList<String>());
		if (e1 instanceof Event && e2 instanceof Event) {
			this.setPairType(PairType.event_event);
		} else if ((e1 instanceof Timex && e2 instanceof Event) || 
				(e1 instanceof Event && e2 instanceof Timex)) {
			this.setPairType(PairType.event_timex);
		} else if (e1 instanceof Timex && e2 instanceof Timex) {
			this.setPairType(PairType.timex_timex);
		}
		this.setSignalList(signalList);
	}
	
	public FeatureVector(Document doc, Entity e1, Entity e2, ArrayList<String> vectors, String label, SignalList signalList) {
		this.setDoc(doc);
		this.setE1(e1);
		this.setE2(e2);
		this.setVectors(vectors);
		this.setLabel(label);
		if (e1 instanceof Event && e2 instanceof Event) {
			this.setPairType(PairType.event_event);
		} else if ((e1 instanceof Timex && e2 instanceof Event) || 
				(e1 instanceof Event && e2 instanceof Timex)) {
			this.setPairType(PairType.event_timex);
		} else if (e1 instanceof Timex && e2 instanceof Timex) {
			this.setPairType(PairType.timex_timex);
		}
		this.setSignalList(signalList);
	}

	public Document getDoc() {
		return doc;
	}

	public void setDoc(Document doc) {
		this.doc = doc;
	}

	public ArrayList<String> getVectors() {
		return vectors;
	}

	public void setVectors(ArrayList<String> vectors) {
		this.vectors = vectors;
	}

	public Entity getE1() {
		return e1;
	}

	public void setE1(Entity e1) {
		this.e1 = e1;
	}

	public Entity getE2() {
		return e2;
	}

	public void setE2(Entity e2) {
		this.e2 = e2;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
	
	public String printVectors() {
		return String.join("\t", this.vectors);
	}

	public PairType getPairType() {
		return pairType;
	}

	public void setPairType(PairType pairType) {
		this.pairType = pairType;
	}

	public SignalList getSignalList() {
		return signalList;
	}

	public void setSignalList(SignalList signalList) {
		this.signalList = signalList;
	}
	
	protected ArrayList<String> getTokenIDArr(String startTokID, String endTokID) {
		ArrayList<String> tokIDs = new ArrayList<String>();
		int startTokIdx = doc.getTokens().get(startTokID).getIndex();
		int endTokIdx = doc.getTokens().get(endTokID).getIndex();
		for (int i=startTokIdx; i<endTokIdx+1; i++) {
			tokIDs.add(doc.getTokenArr().get(i));
		}
		return tokIDs;
	}
	
	protected String getTokenAttribute(Entity e, Feature feature) {
		ArrayList<String> attrList = new ArrayList<String>();
		String currAttr;
		if (e instanceof Timex && (((Timex)e).isDct() || ((Timex)e).isEmptyTag())) {
			return "O";
		} else {	
			for (String tokID : getTokenIDArr(e.getStartTokID(), e.getEndTokID())) {
				currAttr = doc.getTokens().get(tokID).getTokenAttribute(feature);
				if (attrList.isEmpty()){
					attrList.add(currAttr);
				} else {
					if (!currAttr.equals(attrList.get(attrList.size()-1))) {
						attrList.add(currAttr);
					}
				}
			}
			return String.join("_", attrList);
		}
	}
	
	public ArrayList<String> getTokenAttribute(Feature feature) {
		ArrayList<String> texts = new ArrayList<String>();
		texts.add(getTokenAttribute(e1, feature));
		texts.add(getTokenAttribute(e2, feature));
		return texts;
	}
	
	public Boolean isSameTokenAttribute(Feature feature) {
		String eAttr1 = getTokenAttribute(e1, feature);
		String eAttr2 = getTokenAttribute(e2, feature);
		return (eAttr1.equals(eAttr2));
	}
	
	public Integer getEntityDistance() {
		if ((e1 instanceof Timex && ((Timex)e1).isDct()) || (e2 instanceof Timex && ((Timex)e2).isDct()) ||
			(e1 instanceof Timex && ((Timex)e1).isEmptyTag()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag())) {
			return -1;
		} else {
			int eidx1 = e1.getIndex();
			int eidx2 = e2.getIndex();
			return Math.abs(eidx1 - eidx2); 
		}
	}
	
	public Integer getSentenceDistance() {
		if ((e1 instanceof Timex && ((Timex)e1).isDct()) || (e2 instanceof Timex && ((Timex)e2).isDct()) ||
			(e1 instanceof Timex && ((Timex)e1).isEmptyTag()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag())) {
			return -1;
		} else {
			int sidx1 = doc.getSentences().get(e1.getSentID()).getIndex();
			int sidx2 = doc.getSentences().get(e2.getSentID()).getIndex();
			return Math.abs(sidx1 - sidx2);
		}
	}
	
	public Boolean isSameSentence() {
		if ((e1 instanceof Timex && ((Timex)e1).isDct()) || (e2 instanceof Timex && ((Timex)e2).isDct()) ||
			(e1 instanceof Timex && ((Timex)e1).isEmptyTag()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag())) {
			return false;
		} else {
			int sidx1 = doc.getSentences().get(e1.getSentID()).getIndex();
			int sidx2 = doc.getSentences().get(e2.getSentID()).getIndex();
			return (sidx1 == sidx2);
		}
	}
	
	public String getOrder() {
		if ((e1 instanceof Timex && ((Timex)e1).isDct()) || (e2 instanceof Timex && ((Timex)e2).isDct()) ||
			(e1 instanceof Timex && ((Timex)e1).isEmptyTag()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag())) {
			return "O";
		} else {
			int eidx1 = e1.getIndex();
			int eidx2 = e2.getIndex();
			if (eidx1 - eidx2 < 0) {
				return "BEFORE";
			} else if (eidx1 - eidx2 > 0) {
				return "AFTER";
			}
		}
		return null;
	}
	
	protected String getHeadVerb(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		for (String tok : tokenArr) {
			if (!tokID.equals(tok) && doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(tokID) &&
					doc.getTokens().get(tok).getDependencyRel().get(tokID).equals("VC")) {
					return getHeadVerb(tok);
				} 
			}
		}
		return tokID;
	}
	
	protected String getVerbFromAdj(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		for (String tok : tokenArr) {
			if (!tokID.equals(tok) && doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(tokID) &&
					doc.getTokens().get(tok).getDependencyRel().get(tokID).equals("PRD")) {
					return tok;
				}
			}
		}
		return null;
	}
	
	protected String getCoordVerb(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		String headID = getHeadVerb(tokID);
		for (String tok : tokenArr) {
			if (!headID.equals(tok) && 
				doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(headID) &&
					doc.getTokens().get(tok).getDependencyRel().get(headID).equals("COORD")) {
					return tok;
				}
			}
		}
		return null;
	}
	
	protected void generateDependencyPath(String govID, ArrayList<String> depArr, List<String> paths, String pathSoFar, List<String> visited) {
		if (doc.getTokens().get(govID).getDependencyRel() != null && !visited.contains(govID)) {
			for (String key : doc.getTokens().get(govID).getDependencyRel().keySet()) {
				if (depArr.contains(key)) {
					paths.add(pathSoFar + "-" + doc.getTokens().get(govID).getDependencyRel().get(key));
				} else {
					generateDependencyPath(key, depArr, paths, pathSoFar + "-" + doc.getTokens().get(govID).getDependencyRel().get(key), visited);
				}
			}
		}
	}
	
	public String getMateMainVerb(Entity e) {
		if (getTokenAttribute(e, Feature.mainpos).equals("v")) {
			return (doc.getTokens().get(getHeadVerb(e.getStartTokID())).isMainVerb() ? "MAIN" : "O");
		}
		return "O";
	}
	
	protected String getContext(String startTokID, String endTokID) {
		ArrayList<String> tokIDs = getTokenIDArr(startTokID, endTokID);
		ArrayList<String> context = new ArrayList<String>();
		for (String tokID : tokIDs) {
			context.add(doc.getTokens().get(tokID).getTokenAttribute(Feature.token).toLowerCase());
		}
		return " " + String.join(" ", context) + " ";
	}
	
	public String getSignalMateDependencyPath(Entity e, ArrayList<String> entArr, ArrayList<String> signalArr) {
		for (String govID : entArr) {
			List<String> paths = new ArrayList<String>();
			List<String> visited = new ArrayList<String>();
			if (getTokenAttribute(e, Feature.mainpos).equals("v")) {
				govID = getHeadVerb(govID);
			} else if (getTokenAttribute(e, Feature.mainpos).equals("adj") &&
				getVerbFromAdj(govID) != null) {
				govID = getVerbFromAdj(govID);
			}
			generateDependencyPath(govID, signalArr, paths, "", visited);
			if (!paths.isEmpty()) {
				return paths.get(0).substring(1);
			}
			if (getCoordVerb(govID) != null) {
				generateDependencyPath(getCoordVerb(govID), signalArr, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
			}
		}
		for (String govID : signalArr) {
			List<String> paths = new ArrayList<String>();
			List<String> visited = new ArrayList<String>();
			generateDependencyPath(govID, entArr, paths, "", visited);
			if (!paths.isEmpty()) {
				return paths.get(0).substring(1);
			}
		}
		return "O";
	}
	
	public Marker getTemporalSignal(Entity e) throws IOException {
		Map<String, String> tsignalList = null;
		if (e instanceof Event) {
			tsignalList = ((TemporalSignalList) this.getSignalList()).getEventList();
		} else if (e instanceof Timex) {
			tsignalList = ((TemporalSignalList) this.getSignalList()).getTimexList();
		}
		
		Marker m = new Marker("O", "O", "O");
		
		if (tsignalList != null) {		
			Sentence s = doc.getSentences().get(e.getSentID());
			ArrayList<String> entArr = s.getEntityArr();
			int eidx = entArr.indexOf(e.getID());
			
			String tidBefore = "", tidAfter = "";
			if (eidx == 0) { //first entity
				tidBefore = s.getStartTokID();
			} else {
				Entity eBefore = doc.getEntities().get(entArr.get(eidx - 1)); 
				tidBefore = doc.getTokenArr().get(doc.getTokenArr().indexOf(eBefore.getEndTokID()) + 1);
			}
			if (eidx == entArr.size()-1) { //last entity
				tidAfter = s.getEndTokID();
			} else { 
				Entity eAfter = doc.getEntities().get(entArr.get(eidx + 1));
				tidAfter = doc.getTokenArr().get(doc.getTokenArr().indexOf(eAfter.getStartTokID()) - 1);
			}
			
			String tidStart = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getStartTokID()) - 1);
			String tidEnd = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getEndTokID()) + 1);
			String tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
			
			String contextBefore = getContext(tidBefore, tidStart);
			String contextAfter = getContext(tidEnd, tidAfter);
			String contextBegin = getContext(s.getStartTokID(), tidBegin);
			
			for (String key : tsignalList.keySet()) {
				//if (key.equals("before")) System.out.println(e.getID() + "\t" + key + "\t-" + contextBefore + "-");
				if (contextBefore.contains(" " + key + " ")) {
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("BEFORE");
					//m.setDepRel(getSignalMateDependencyPath(e, ));
				} else if (contextAfter.contains(" " + key + " ")) {
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("AFTER");
				} else if (contextBegin.contains(" " + key + " ")) {
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("BEGIN");
				}
			}
		}
		
		return m;
	}
}
