package model.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.feature.FeatureEnum.*;
import model.feature.temporal.TemporalSignalList;
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
	
	protected String getString(String startTokID, String endTokID) {
		ArrayList<String> tokIDs = getTokenIDArr(startTokID, endTokID);
		ArrayList<String> context = new ArrayList<String>();
		for (String tokID : tokIDs) {
			context.add(doc.getTokens().get(tokID).getTokenAttribute(Feature.token).toLowerCase());
		}
		return String.join(" ", context);
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
	
	private ArrayList<String> getSignalTidArr(String signal, String context, String tidStartContext, String position) {
		ArrayList<String> signalTidArr = new ArrayList<String>();
		
		String resContext = null;
		if (position.equals("BEFORE")) {
			resContext = context.trim().substring(0, context.lastIndexOf(signal));
		} else {
			resContext = context.trim().substring(0, context.indexOf(signal));
		}
		int start = resContext.length() - resContext.replace(" ", "").length(); //count the number of spaces
		
		int tidxStartContext = doc.getTokenArr().indexOf(tidStartContext);
		int tidxStartSignal = tidxStartContext + start;
		int signalLength = signal.trim().split(" ").length;
		for (int i = tidxStartSignal; i < tidxStartSignal + signalLength; i++) {
			signalTidArr.add(doc.getTokenArr().get(i));
		}
		
		return signalTidArr;
	}
	
	private Integer getSignalEntityDistance(String signal, String context, String position) {
		List<String> wordList = Arrays.asList(context.split(" "));
		Collections.reverse(wordList);
		String reversedContext = String.join(" ", wordList);
		
		wordList = Arrays.asList(signal.split(" "));
		Collections.reverse(wordList);
		String reversedSignal = String.join(" ", wordList);
		
		if (position.equals("BEFORE")) {
			String resContext = reversedContext.trim().substring(0, reversedContext.indexOf(reversedSignal));
			return resContext.length() - resContext.replace(" ", "").length(); //count the number of spaces
		} else {
			String resContext = context.trim().substring(0, context.indexOf(signal));
			return resContext.length() - resContext.replace(" ", "").length(); //count the number of spaces
		}
	}
	
	public Marker getTemporalSignal(Entity e) throws IOException {
		Map<String, String> tsignalList = null;
		if (e instanceof Event) {
			tsignalList = ((TemporalSignalList) this.getSignalList()).getEventList();
		} else if (e instanceof Timex) {
			tsignalList = ((TemporalSignalList) this.getSignalList()).getTimexList();
		}
		
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
			
			String tidStart = "", tidEnd = "", tidBegin = "";
			if (e.getStartTokID().equals(s.getStartTokID())) {
				tidStart = s.getStartTokID();
			} else {
				tidStart = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getStartTokID()) - 1);
			}
			if (e.getEndTokID().equals(s.getEndTokID())) {
				tidEnd = s.getEndTokID();
			} else {
				tidEnd = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getEndTokID()) + 1);
			}
			tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
			
			String contextBefore = getString(tidBefore, tidStart);
			String contextAfter = getString(tidEnd, tidAfter);
			String contextBegin = getString(s.getStartTokID(), tidBegin);
			String contextEntity = getString(e.getStartTokID(), e.getEndTokID());
			
			Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
			
			for (String key : tsignalList.keySet()) {
				if (contextBefore.contains(" " + key + " ")) {
					Marker m = new Marker();
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("BEFORE");
					m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), 
						getSignalTidArr(key, contextBefore, tidBefore, "BEFORE")));
					candidates.put(getSignalEntityDistance(key, contextBefore, "BEFORE"), m);
				} else if (contextAfter.contains(" " + key + " ")) {
					Marker m = new Marker();
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("AFTER");
					m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), 
						getSignalTidArr(key, contextAfter, tidEnd, "AFTER")));
					candidates.put(getSignalEntityDistance(key, contextAfter, "AFTER") + 100, m);
				} else if (contextEntity.contains(" " + key + " ")) {
					Marker m = new Marker();
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("INSIDE");
					m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), 
						getSignalTidArr(key, contextEntity, e.getStartTokID(), "INSIDE")));
					candidates.put(getSignalEntityDistance(key, contextEntity, "INSIDE") + 200, m);
				} else if (contextBegin.contains(" " + key + " ")) {
					Marker m = new Marker();
					m.setText(tsignalList.get(key).replace(" ", "_"));
					m.setPosition("BEGIN");
					m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), 
						getSignalTidArr(key, contextBegin, s.getStartTokID(), "BEGIN")));
					candidates.put(getSignalEntityDistance(key, contextBegin, "BEGIN") + 300, m);
				} 
			}
			
			if (!candidates.isEmpty()) {
				Object[] keys = candidates.keySet().toArray();
				Arrays.sort(keys);
				return candidates.get(keys[0]);
			} else {
				return new Marker("O", "O", "O");
			}
		}
		
		return null;
	}
	
	public ArrayList<String> getConnectiveTidArr(String startTidContext, String endTidContext, String position) {
		ArrayList<String> connTidArr = new ArrayList<String>();
		ArrayList<String> tidArr = getTokenIDArr(startTidContext,endTidContext);
		if (position.equals("BEFORE")) {
			Collections.reverse(tidArr);
		}
		Boolean start = false;
		for (String tid : tidArr) {
			if (doc.getTokens().get(tid).getDiscourseConn().equals("Temporal")) {
				connTidArr.add(tid);
				start = true;
			} else {
				if (start) {
					start = false;
					break;
				}
			}
		}
		if (position.equals("BEFORE")) {
			Collections.reverse(connTidArr);
		}
		return connTidArr;
	}
	
	private Integer getConnectiveEntityDistance(Entity e, ArrayList<String> tidConn, String position) {
		if (position.equals("BEFORE")) {
			return Math.abs(doc.getTokenArr().indexOf(e.getStartTokID()) 
				- doc.getTokenArr().indexOf(tidConn.get(tidConn.size()-1)));
		} else {
			return Math.abs(doc.getTokenArr().indexOf(e.getEndTokID()) 
				- doc.getTokenArr().indexOf(tidConn.get(0)));
		}
	}
	
	public Marker getTemporalConnective(Entity e) {
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
		
		String tidStart = "", tidEnd = "", tidBegin = "";
		if (e.getStartTokID().equals(s.getStartTokID())) {
			tidStart = s.getStartTokID();
		} else {
			tidStart = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getStartTokID()) - 1);
		}
		if (e.getEndTokID().equals(s.getEndTokID())) {
			tidEnd = s.getEndTokID();
		} else {
			tidEnd = doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getEndTokID()) + 1);
		}
		tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
		
		ArrayList<String> tidConnBefore = getConnectiveTidArr(tidBefore, tidStart, "BEFORE");
		ArrayList<String> tidConnAfter = getConnectiveTidArr(tidEnd, tidAfter, "AFTER");
		ArrayList<String> tidConnBegin = getConnectiveTidArr(s.getStartTokID(), tidBegin, "BEGIN");
		ArrayList<String> tidConnEntity = getConnectiveTidArr(e.getStartTokID(), e.getEndTokID(), "INSIDE");
		
		Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
		
		//if (key.equals("before")) System.out.println(e.getID() + "\t" + key + "\t-" + contextBefore + "-");
		if (!tidConnBefore.isEmpty()) {
			Marker m = new Marker();
			m.setText(getString(tidConnBefore.get(0), tidConnBefore.get(tidConnBefore.size()-1)));
			m.setPosition("BEFORE");
			m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), tidConnBefore));
			candidates.put(getConnectiveEntityDistance(e, tidConnBefore, "BEFORE"), m);
		} else if (!tidConnAfter.isEmpty()) {			
			Marker m = new Marker();
			m.setText(getString(tidConnAfter.get(0), tidConnAfter.get(tidConnAfter.size()-1)));
			m.setPosition("AFTER");
			m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), tidConnAfter));
			candidates.put(getConnectiveEntityDistance(e, tidConnAfter, "AFTER") + 100, m);
		} else if (!tidConnEntity.isEmpty()) {
			Marker m = new Marker();
			m.setText(getString(tidConnEntity.get(0), tidConnEntity.get(tidConnEntity.size()-1)));
			m.setPosition("INSIDE");
			m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), tidConnEntity));
			candidates.put(getConnectiveEntityDistance(e, tidConnEntity, "INSIDE") + 200, m);
		} else if (!tidConnBegin.isEmpty()) {
			Marker m = new Marker();
			m.setText(getString(tidConnBegin.get(0), tidConnBegin.get(tidConnBegin.size()-1)));
			m.setPosition("BEGIN");
			m.setDepRel(getSignalMateDependencyPath(e, getTokenIDArr(e.getStartTokID(), e.getEndTokID()), tidConnBegin));
			candidates.put(getConnectiveEntityDistance(e, tidConnBegin, "BEGIN") + 300, m);
		}
		
		if (!candidates.isEmpty()) {
			Object[] keys = candidates.keySet().toArray();
			Arrays.sort(keys);
			return candidates.get(keys[0]);
		} else {
			return new Marker("O", "O", "O");
		}
	}
}
