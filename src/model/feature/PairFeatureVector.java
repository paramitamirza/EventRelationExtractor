package model.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.feature.FeatureEnum.*;
import parser.entities.*;

public class PairFeatureVector {
	
	protected Document doc;
	private ArrayList<String> vectors;
	protected Entity e1;
	protected Entity e2;
	protected String label;
	private PairType pairType;
	private TemporalSignalList tempSignalList;
	private CausalSignalList causalSignalList;
	
	public PairFeatureVector(Document doc, Entity e1, Entity e2, String label, TemporalSignalList tempSignalList, CausalSignalList causalSignalList) {
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
		this.setTempSignalList(tempSignalList);
		this.setCausalSignalList(causalSignalList);
	}
	
	public PairFeatureVector(Document doc, Entity e1, Entity e2, ArrayList<String> vectors, String label, TemporalSignalList tempSignalList, CausalSignalList causalSignalList) {
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
		this.setTempSignalList(tempSignalList);
		this.setCausalSignalList(causalSignalList);
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
	
	public String printCSVVectors() {
		String csv = "";
		for (String col : vectors) {
			String s = col.replace(",", "COMMA");
			s = s.replace("'", "QUOT");
			csv += s + ",";
		}
		return csv.substring(0, csv.length()-1);
	}

	public PairType getPairType() {
		return pairType;
	}

	public void setPairType(PairType pairType) {
		this.pairType = pairType;
	}

	public TemporalSignalList getTempSignalList() {
		return tempSignalList;
	}

	public void setTempSignalList(TemporalSignalList tempSignalList) {
		this.tempSignalList = tempSignalList;
	}

	public CausalSignalList getCausalSignalList() {
		return causalSignalList;
	}

	public void setCausalSignalList(CausalSignalList causalSignalList) {
		this.causalSignalList = causalSignalList;
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
			if (feature.equals(Feature.token) || feature.equals(Feature.lemma))
				return String.join(",", attrList);
			else
				return String.join("_", attrList);
		}
	}
	
	public ArrayList<String> getTokenAttribute(Feature feature) {
		ArrayList<String> texts = new ArrayList<String>();
		texts.add(getTokenAttribute(e1, feature));
		texts.add(getTokenAttribute(e2, feature));
		return texts;
	}
	
	public String getCombinedTokenAttribute(Feature feature) {
		return getTokenAttribute(e1, feature) + "|" + getTokenAttribute(e2, feature);
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
	
	protected String getEntityAttribute(Entity e, Feature feature) {
		if (e instanceof Event) {
			if (((Event)e).getAttribute(feature).equals("O")) {
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
				return "NONE";
			} else {
				return ((Event)e).getAttribute(feature);
			}
		} else if (e instanceof Timex){
			return ((Timex)e).getAttribute(feature);
		}
		return null;
	}
	
	protected String getMateHeadVerb(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		for (String tok : tokenArr) {
			if (!tokID.equals(tok) && doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(tokID) &&
					doc.getTokens().get(tok).getDependencyRel().get(tokID).equals("VC")) {
					return getMateHeadVerb(tok);
				} 
			}
		}
		return tokID;
	}
	
	protected String getMateVerbFromSbjNoun(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		for (String tok : tokenArr) {
			if (!tokID.equals(tok) && doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(tokID) &&
					doc.getTokens().get(tok).getDependencyRel().get(tokID).equals("SBJ")) {
					return tok;
				}
			}
		}
		return null;
	}
	
	protected String getMateVerbFromObjNoun(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		for (String tok : tokenArr) {
			if (!tokID.equals(tok) && doc.getTokens().get(tok).getDependencyRel() != null) {
				if (doc.getTokens().get(tok).getDependencyRel().keySet().contains(tokID) &&
					doc.getTokens().get(tok).getDependencyRel().get(tokID).equals("OBJ")) {
					return tok;
				}
			}
		}
		return null;
	}
	
	protected String getMateVerbFromAdj(String tokID) {
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
	
	protected String getMateCoordVerb(String tokID) {
		Sentence s = doc.getSentences().get(doc.getTokens().get(tokID).getSentID());
		ArrayList<String> tokenArr = getTokenIDArr(s.getStartTokID(), s.getEndTokID());
		String headID = getMateHeadVerb(tokID);
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
			return (doc.getTokens().get(getMateHeadVerb(e.getStartTokID())).isMainVerb() ? "MAIN" : "O");
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
				govID = getMateHeadVerb(govID);
			} else if (getTokenAttribute(e, Feature.mainpos).equals("adj") &&
				getMateVerbFromAdj(govID) != null) {
				govID = getMateVerbFromAdj(govID);
			}
			generateDependencyPath(govID, signalArr, paths, "", visited);
			if (!paths.isEmpty()) {
				return paths.get(0).substring(1);
			}
			if (getMateCoordVerb(govID) != null) {
				generateDependencyPath(getMateCoordVerb(govID), signalArr, paths, "", visited);
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
		if (position.equals("BEFORE") || position.equals("BETWEEN")) {
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
		
		if (position.equals("BEFORE") || position.equals("BETWEEN")) {
			String resContext = reversedContext.trim().substring(0, reversedContext.indexOf(reversedSignal));
			return resContext.length() - resContext.replace(" ", "").length(); //count the number of spaces
		} else {
			String resContext = context.trim().substring(0, context.indexOf(signal));
			return resContext.length() - resContext.replace(" ", "").length(); //count the number of spaces
		}
	}
	
	protected ArrayList<String> getTidEntityBeforeAfter(Entity e) {
		ArrayList<String> tids = new ArrayList<String>();
		Sentence s = doc.getSentences().get(e.getSentID());
		ArrayList<String> entArr = s.getEntityArr();
		int eidx = entArr.indexOf(e.getID());
		
		if (eidx == 0) { //first entity
			tids.add(s.getStartTokID());
		} else {
			Entity eBefore = doc.getEntities().get(entArr.get(eidx - 1)); 
			tids.add(doc.getTokenArr().get(doc.getTokenArr().indexOf(eBefore.getEndTokID()) + 1));
		}
		if (eidx == entArr.size()-1) { //last entity
			tids.add(s.getEndTokID());
		} else { 
			Entity eAfter = doc.getEntities().get(entArr.get(eidx + 1));
			tids.add(doc.getTokenArr().get(doc.getTokenArr().indexOf(eAfter.getStartTokID()) - 1));
		}
		
		return tids;
	}
	
	protected ArrayList<String> getTidBeforeAfter(Entity e) {
		ArrayList<String> tids = new ArrayList<String>();
		Sentence s = doc.getSentences().get(e.getSentID());
		
		if (e.getStartTokID().equals(s.getStartTokID())) { //first entity
			tids.add(s.getStartTokID());
		} else {
			tids.add(doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getStartTokID()) - 1));
		}
		if (e.getEndTokID().equals(s.getEndTokID())) { //last entity
			tids.add(s.getEndTokID());
		} else {
			tids.add(doc.getTokenArr().get(doc.getTokenArr().indexOf(e.getEndTokID()) + 1));
		}
		
		return tids;
	}
	
	public ArrayList<String> getConnectiveTidArr(String conn, String startTidContext, String endTidContext, String position) {
		ArrayList<String> connTidArr = new ArrayList<String>();
		ArrayList<String> tidArr = getTokenIDArr(startTidContext,endTidContext);
		if (position.equals("BEFORE") || position.equals("BETWEEN")) {
			Collections.reverse(tidArr);
		}
		Boolean start = false;
		for (String tid : tidArr) {
			if (doc.getTokens().get(tid).getDiscourseConn().equals(conn)) {
				connTidArr.add(tid);
				start = true;
			} else {
				if (start) {
					start = false;
					break;
				}
			}
		}
		if (position.equals("BEFORE") || position.equals("BETWEEN")) {
			Collections.reverse(connTidArr);
		}
		return connTidArr;
	}
	
	private Integer getConnectiveEntityDistance(Entity e, ArrayList<String> tidConn, String position) {
		if (position.equals("BEFORE") || position.equals("BETWEEN")) {
			return Math.abs(doc.getTokenArr().indexOf(e.getStartTokID()) 
				- doc.getTokenArr().indexOf(tidConn.get(tidConn.size()-1)));
		} else {
			return Math.abs(doc.getTokenArr().indexOf(e.getEndTokID()) 
				- doc.getTokenArr().indexOf(tidConn.get(0)));
		}
	}

	public Marker getSignalMarker(Map<String, String> signalList, String text, String position, String context, String contextStartTid) {
		Marker m = new Marker();
		m.setText(text);
		m.setCluster(signalList.get(text));
		m.setPosition(position);
		String dep1 = getSignalMateDependencyPath(e1, getTokenIDArr(e1.getStartTokID(), e1.getEndTokID()), 
				getSignalTidArr(text, context, contextStartTid, position));
		String dep2 = getSignalMateDependencyPath(e2, getTokenIDArr(e2.getStartTokID(), e2.getEndTokID()), 
				getSignalTidArr(text, context, contextStartTid, position));
		m.setDepRelE1(dep1);
		m.setDepRelE2(dep2);
		return m;
	}
	
	public Marker getConnectiveMarker(String text, String position, ArrayList<String> conn) {
		Marker m = new Marker();
		m.setText(text);
		m.setCluster(text);
		m.setPosition(position);
		String dep1 = getSignalMateDependencyPath(e1, getTokenIDArr(e1.getStartTokID(), e1.getEndTokID()), conn);
		String dep2 = getSignalMateDependencyPath(e2, getTokenIDArr(e2.getStartTokID(), e2.getEndTokID()), conn);
		m.setDepRelE1(dep1);
		m.setDepRelE2(dep2);
		return m;
	}
	
	public Marker getVerbMarker(Map<String, String> verbList, String text, String position, String verbTid) {
		Marker m = new Marker();
		m.setText(text);
		m.setCluster(verbList.get(text));
		m.setPosition(position);
		ArrayList<String> verbTidArr = new ArrayList<String>(); verbTidArr.add(verbTid);
		String dep1 = getSignalMateDependencyPath(e1, getTokenIDArr(e1.getStartTokID(), e1.getEndTokID()), verbTidArr);
		String dep2 = getSignalMateDependencyPath(e2, getTokenIDArr(e2.getStartTokID(), e2.getEndTokID()), verbTidArr);
		m.setDepRelE1(dep1);
		m.setDepRelE2(dep2);
		return m;
	}
	
	public Marker getTemporalSignal() throws IOException {
		Map<String, String> tsignalListEvent = this.getTempSignalList().getEventList();
		Map<String, String> tsignalListTimex = this.getTempSignalList().getTimexList();
		
		if (tsignalListEvent != null && tsignalListTimex != null) {		
			Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
			
			if (isSameSentence()) {
				Sentence s = doc.getSentences().get(e1.getSentID());
				
				String tidBefore1 = getTidEntityBeforeAfter(e1).get(0);
				//String tidAfter1 = getTidEntityBeforeAfter(e1).get(1);
				String tidStart1 = getTidBeforeAfter(e1).get(0);
				//String tidEnd1 = getTidBeforeAfter(e1).get(1);
				
				String tidBefore2 = getTidEntityBeforeAfter(e1).get(0);
				String tidAfter2 = getTidEntityBeforeAfter(e2).get(1);
				String tidStart2 = getTidBeforeAfter(e2).get(0);
				String tidEnd2 = getTidBeforeAfter(e2).get(1);
				
				String tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
				
				String contextBefore = getString(tidBefore1, tidStart1);
				String contextBetween = getString(tidBefore2, tidStart2);	//#only consider the context before e2 (after other entity)
				String contextAfter = getString(tidEnd2, tidAfter2);
				String contextBegin = getString(s.getStartTokID(), tidBegin);
				String contextEntity = getString(e2.getStartTokID(), e2.getEndTokID());	//in event-timex pair, sometimes signal is within timex
				
				Map<String, String> signalList;
				if (e2 instanceof Timex) signalList = tsignalListTimex;
				else signalList = tsignalListEvent;
				for (String key : signalList.keySet()) {
					if (contextBetween.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BETWEEN", contextBetween, tidBefore2);
						candidates.put(getSignalEntityDistance(key, contextBetween, "BETWEEN"), m);
					} else if (contextEntity.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "INSIDE", contextEntity, e2.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextEntity, "INSIDE") + 100, m);
					} else if (contextAfter.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "AFTER", contextAfter, tidEnd2);
						candidates.put(getSignalEntityDistance(key, contextAfter, "AFTER") + 300, m);
					}
				}
				for (String key : tsignalListEvent.keySet()) {
					if (contextBefore.contains(" " + key + " ")) {
						Marker m = getSignalMarker(tsignalListEvent, key, "BEFORE", contextBefore, tidBefore1);
						candidates.put(getSignalEntityDistance(key, contextBefore, "BEFORE") + 200, m);
					} else if (contextBegin.contains(" " + key + " ")) {
						Marker m = getSignalMarker(tsignalListEvent, key, "BEGIN", contextBegin, s.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin, "BEGIN") + 400, m);
					} 
				}
			} else { //different sentences
				Sentence s1 = doc.getSentences().get(e1.getSentID());
				Sentence s2 = doc.getSentences().get(e2.getSentID());
				String tidBegin1 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s1.getStartTokID()) + 4);
				String tidBegin2 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s2.getStartTokID()) + 4);
				String contextBegin1 = getString(s1.getStartTokID(), tidBegin1);
				String contextBegin2 = getString(s2.getStartTokID(), tidBegin2);
				
				for (String key : tsignalListEvent.keySet()) {
					if (contextBegin2.contains(" " + key + " ")) {
						Marker m = getSignalMarker(tsignalListEvent, key, "BEGIN-BETWEEN", contextBegin2, s2.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin2, "BEGIN-BETWEEN"), m);
					} else if (contextBegin1.contains(" " + key + " ")) {
						Marker m = getSignalMarker(tsignalListEvent, key, "BEGIN-BEFORE", contextBegin1, s1.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin1, "BEGIN-BEFORE") + 100, m);
					}
				}
			}
			
			if (!candidates.isEmpty()) {
				Object[] keys = candidates.keySet().toArray();
				Arrays.sort(keys);
				return candidates.get(keys[0]);
			} else {
				return new Marker("O", "O", "O", "O", "O");
			}
		}
		return null;
	}
			
	public Marker getTemporalConnective() {
		Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
		
		if (isSameSentence()) {
			Sentence s = doc.getSentences().get(e1.getSentID());
			
			String tidBefore1 = getTidEntityBeforeAfter(e1).get(0);
			//String tidAfter1 = getTidEntityBeforeAfter(e1).get(1);
			String tidStart1 = getTidBeforeAfter(e1).get(0);
			//String tidEnd1 = getTidBeforeAfter(e1).get(1);
			
			String tidBefore2 = getTidEntityBeforeAfter(e1).get(0);
			String tidAfter2 = getTidEntityBeforeAfter(e2).get(1);
			String tidStart2 = getTidBeforeAfter(e2).get(0);
			String tidEnd2 = getTidBeforeAfter(e2).get(1);
			
			String tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
			
			ArrayList<String> tidConnBefore = getConnectiveTidArr("Temporal", tidBefore1, tidStart1, "BEFORE");
			ArrayList<String> tidConnBetween = getConnectiveTidArr("Temporal", tidBefore2, tidStart2, "BETWEEN");
			ArrayList<String> tidConnAfter = getConnectiveTidArr("Temporal", tidEnd2, tidAfter2, "AFTER");
			ArrayList<String> tidConnBegin = getConnectiveTidArr("Temporal", s.getStartTokID(), tidBegin, "BEGIN");
			ArrayList<String> tidConnEntity = getConnectiveTidArr("Temporal", e2.getStartTokID(), e2.getEndTokID(), "INSIDE");
			
			if (!tidConnBetween.isEmpty()) {
				String text = getString(tidConnBetween.get(0), tidConnBetween.get(tidConnBetween.size()-1));
				Marker m = getConnectiveMarker(text, "BETWEEN", tidConnBetween);
				candidates.put(getConnectiveEntityDistance(e2, tidConnBetween, "BETWEEN"), m);
			} else if (!tidConnBefore.isEmpty()) {
				String text = getString(tidConnBefore.get(0), tidConnBefore.get(tidConnBefore.size()-1));
				Marker m = getConnectiveMarker(text, "BEFORE", tidConnBefore);
				candidates.put(getConnectiveEntityDistance(e1, tidConnBefore, "BEFORE") + 100, m);
			} else if (!tidConnAfter.isEmpty()) {
				String text = getString(tidConnAfter.get(0), tidConnAfter.get(tidConnAfter.size()-1));
				Marker m = getConnectiveMarker(text, "AFTER", tidConnAfter);
				candidates.put(getConnectiveEntityDistance(e2, tidConnAfter, "AFTER") + 200, m);
			} else if (!tidConnEntity.isEmpty()) {
				String text = getString(tidConnEntity.get(0), tidConnEntity.get(tidConnEntity.size()-1));
				Marker m = getConnectiveMarker(text, "INSIDE", tidConnBegin);
				int distance = Math.abs(doc.getTokenArr().indexOf(e2.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnEntity.get(0)));
				candidates.put(distance + 300, m);
			} else if (!tidConnBegin.isEmpty()) {
				String text = getString(tidConnBegin.get(0), tidConnBegin.get(tidConnBegin.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN", tidConnBegin);
				int distance = Math.abs(doc.getTokenArr().indexOf(s.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin.get(0)));
				candidates.put(distance + 400, m);
			}
		} else { //different sentences
			Sentence s1 = doc.getSentences().get(e1.getSentID());
			Sentence s2 = doc.getSentences().get(e2.getSentID());
			String tidBegin1 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s1.getStartTokID()) + 4);
			String tidBegin2 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s2.getStartTokID()) + 4);
			ArrayList<String> tidConnBegin1 = getConnectiveTidArr("Temporal", s1.getStartTokID(), tidBegin1, "BEGIN");
			ArrayList<String> tidConnBegin2 = getConnectiveTidArr("Temporal", s1.getStartTokID(), tidBegin2, "BEGIN");
			
			if (!tidConnBegin2.isEmpty()) {
				String text = getString(tidConnBegin2.get(0), tidConnBegin2.get(tidConnBegin2.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN-BETWEEN", tidConnBegin2);
				int distance = Math.abs(doc.getTokenArr().indexOf(s2.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin2.get(0)));
				candidates.put(distance, m);
			} else if (!tidConnBegin1.isEmpty()) {
				String text = getString(tidConnBegin1.get(0), tidConnBegin1.get(tidConnBegin1.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN-BEFORE", tidConnBegin1);
				int distance = Math.abs(doc.getTokenArr().indexOf(s1.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin1.get(0)));
				candidates.put(distance + 100, m);
			}
		}
		
		if (!candidates.isEmpty()) {
			Object[] keys = candidates.keySet().toArray();
			Arrays.sort(keys);
			return candidates.get(keys[0]);
		} else {
			return new Marker("O", "O", "O", "O", "O");
		}
	}
	
	public Marker getCausalSignal() throws IOException {
		Map<String, String> signalList = null;
		signalList = ((CausalSignalList) this.getCausalSignalList()).getList();
		
		if (signalList != null) {	
			Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
			
			if (isSameSentence()) {
				Sentence s = doc.getSentences().get(e1.getSentID());
				
				String tidBefore1 = getTidEntityBeforeAfter(e1).get(0);
				//String tidAfter1 = getTidEntityBeforeAfter(e1).get(1);
				String tidStart1 = getTidBeforeAfter(e1).get(0);
				//String tidEnd1 = getTidBeforeAfter(e1).get(1);
				
				String tidBefore2 = getTidEntityBeforeAfter(e1).get(0);
				String tidAfter2 = getTidEntityBeforeAfter(e2).get(1);
				String tidStart2 = getTidBeforeAfter(e2).get(0);
				String tidEnd2 = getTidBeforeAfter(e2).get(1);
				
				String tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
				
				String contextBefore = getString(tidBefore1, tidStart1);
				String contextBetween = getString(tidBefore2, tidStart2);	//#only consider the context before e2 (after other entity)
				String contextAfter = getString(tidEnd2, tidAfter2);
				String contextBegin = getString(s.getStartTokID(), tidBegin);
				
				for (String key : signalList.keySet()) {
					if (contextBetween.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BETWEEN", contextBetween, tidBefore2);
						candidates.put(getSignalEntityDistance(key, contextBetween, "BETWEEN"), m);
					} else if (contextBefore.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BEFORE", contextBefore, tidBefore1);
						candidates.put(getSignalEntityDistance(key, contextBefore, "BEFORE") + 100, m);
					} else if (contextAfter.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "AFTER", contextAfter, tidEnd2);
						candidates.put(getSignalEntityDistance(key, contextAfter, "AFTER") + 200, m);
					} else if (contextBegin.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BEGIN", contextBegin, s.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin, "BEGIN") + 300, m);
					} 
				}
			} else { //consecutive sentences
				Sentence s1 = doc.getSentences().get(e1.getSentID());
				Sentence s2 = doc.getSentences().get(e2.getSentID());
				String tidBegin1 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s1.getStartTokID()) + 4);
				String tidBegin2 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s2.getStartTokID()) + 4);
				String contextBegin1 = getString(s1.getStartTokID(), tidBegin1);
				String contextBegin2 = getString(s2.getStartTokID(), tidBegin2);
				
				for (String key : signalList.keySet()) {
					if (contextBegin2.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BEGIN-BETWEEN", contextBegin2, s2.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin2, "BEGIN-BETWEEN"), m);
					} else if (contextBegin1.contains(" " + key + " ")) {
						Marker m = getSignalMarker(signalList, key, "BEGIN-BEFORE", contextBegin1, s1.getStartTokID());
						candidates.put(getSignalEntityDistance(key, contextBegin1, "BEGIN-BEFORE") + 100, m);
					}
				}
			}
			
			if (!candidates.isEmpty()) {
				Object[] keys = candidates.keySet().toArray();
				Arrays.sort(keys);
				return candidates.get(keys[0]);
			} else {
				return new Marker("O", "O", "O", "O", "O");
			}
		}
		return null;
	}
	
	public Marker getCausalConnective() {
		Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
		
		if (isSameSentence()) {
			Sentence s = doc.getSentences().get(e1.getSentID());
			
			String tidBefore1 = getTidEntityBeforeAfter(e1).get(0);
			//String tidAfter1 = getTidEntityBeforeAfter(e1).get(1);
			String tidStart1 = getTidBeforeAfter(e1).get(0);
			//String tidEnd1 = getTidBeforeAfter(e1).get(1);
			
			String tidBefore2 = getTidEntityBeforeAfter(e1).get(0);
			String tidAfter2 = getTidEntityBeforeAfter(e2).get(1);
			String tidStart2 = getTidBeforeAfter(e2).get(0);
			String tidEnd2 = getTidBeforeAfter(e2).get(1);
			
			String tidBegin = doc.getTokenArr().get(doc.getTokenArr().indexOf(s.getStartTokID()) + 4);
			
			ArrayList<String> tidConnBefore = getConnectiveTidArr("Contingency", tidBefore1, tidStart1, "BEFORE");
			ArrayList<String> tidConnBetween = getConnectiveTidArr("Contingency", tidBefore2, tidStart2, "BETWEEN");
			ArrayList<String> tidConnAfter = getConnectiveTidArr("Contingency", tidEnd2, tidAfter2, "AFTER");
			ArrayList<String> tidConnBegin = getConnectiveTidArr("Contingency", s.getStartTokID(), tidBegin, "BEGIN");
			
			if (!tidConnBetween.isEmpty()) {
				String text = getString(tidConnBetween.get(0), tidConnBetween.get(tidConnBetween.size()-1));
				Marker m = getConnectiveMarker(text, "BETWEEN", tidConnBetween);
				candidates.put(getConnectiveEntityDistance(e2, tidConnBetween, "BETWEEN"), m);
			} else if (!tidConnBefore.isEmpty()) {
				String text = getString(tidConnBefore.get(0), tidConnBefore.get(tidConnBefore.size()-1));
				Marker m = getConnectiveMarker(text, "BEFORE", tidConnBefore);
				candidates.put(getConnectiveEntityDistance(e1, tidConnBefore, "BEFORE") + 100, m);
			} else if (!tidConnAfter.isEmpty()) {
				String text = getString(tidConnAfter.get(0), tidConnAfter.get(tidConnAfter.size()-1));
				Marker m = getConnectiveMarker(text, "AFTER", tidConnAfter);
				candidates.put(getConnectiveEntityDistance(e2, tidConnAfter, "AFTER") + 200, m);
			} else if (!tidConnBegin.isEmpty()) {
				String text = getString(tidConnBegin.get(0), tidConnBegin.get(tidConnBegin.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN", tidConnBegin);
				int distance = Math.abs(doc.getTokenArr().indexOf(s.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin.get(0)));
				candidates.put(distance + 300, m);
			}
		} else { //consecutive sentences
			Sentence s1 = doc.getSentences().get(e1.getSentID());
			Sentence s2 = doc.getSentences().get(e2.getSentID());
			String tidBegin1 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s1.getStartTokID()) + 4);
			String tidBegin2 = doc.getTokenArr().get(doc.getTokenArr().indexOf(s2.getStartTokID()) + 4);
			ArrayList<String> tidConnBegin1 = getConnectiveTidArr("Contingency", s1.getStartTokID(), tidBegin1, "BEGIN");
			ArrayList<String> tidConnBegin2 = getConnectiveTidArr("Contingency", s1.getStartTokID(), tidBegin2, "BEGIN");
			
			if (!tidConnBegin2.isEmpty()) {
				String text = getString(tidConnBegin2.get(0), tidConnBegin2.get(tidConnBegin2.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN-BETWEEN", tidConnBegin2);
				int distance = Math.abs(doc.getTokenArr().indexOf(s2.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin2.get(0)));
				candidates.put(distance, m);
			} else if (!tidConnBegin1.isEmpty()) {
				String text = getString(tidConnBegin1.get(0), tidConnBegin1.get(tidConnBegin1.size()-1));
				Marker m = getConnectiveMarker(text, "BEGIN-BEFORE", tidConnBegin1);
				int distance = Math.abs(doc.getTokenArr().indexOf(s1.getStartTokID()) 
						- doc.getTokenArr().indexOf(tidConnBegin1.get(0)));
				candidates.put(distance + 100, m);
			}
		}
		if (!candidates.isEmpty()) {
			Object[] keys = candidates.keySet().toArray();
			Arrays.sort(keys);
			return candidates.get(keys[0]);
		} else {
			return new Marker("O", "O", "O", "O", "O");
		}
	}
	
	public Marker getCausalVerb() {
		Map<String, String> verbList = null;
		verbList = ((CausalSignalList) this.getCausalSignalList()).getVerbList();
		
		if (verbList != null) {	
			Map<Integer, Marker> candidates = new HashMap<Integer, Marker>();
			
			if (isSameSentence()) {
				//String tidStart1 = getTidBeforeAfter(e1).get(0);
				String tidEnd1 = getTidBeforeAfter(e1).get(1);
				String tidStart2 = getTidBeforeAfter(e2).get(0);
				//String tidEnd2 = getTidBeforeAfter(e2).get(1);
				
				ArrayList<String> tidBetweenArr = getTokenIDArr(tidEnd1, tidStart2); //all words between e1 and e2
				
				for (String tid : tidBetweenArr) {
					if (doc.getTokens().get(tid).getMainPos().equals("v")) {
						String lemma = doc.getTokens().get(tid).getLemma();
						if (lemma.equals("link") || lemma.equals("lead") || lemma.equals("depend") || lemma.equals("result")) {
							String[] possiblePrep = {"to", "with", "on", "in"};
							List<String> possiblePrepList = Arrays.asList(possiblePrep);
							Map<String, String> deps = doc.getTokens().get(tid).getDependencyRel();
							for (String prep : possiblePrepList) {
								if (deps.keySet().contains(prep)) {
									Marker m = getVerbMarker(verbList, lemma, "BETWEEN", tid);
									int distance = Math.abs(doc.getTokenArr().indexOf(tid) 
											- doc.getTokenArr().indexOf(e2.getStartTokID()));
									candidates.put(distance, m);
								}
							}
						} else if (lemma.equals("have")) {
							Boolean vc = false;
							Map<String, String> deps = doc.getTokens().get(tid).getDependencyRel();
							if (deps != null) {
								for (String depid : deps.keySet()) {
									if (deps.get(depid).equals("VC")) {
										vc = true; 
										break;
									}
								}
							}
							if (!vc) {
								Marker m = getVerbMarker(verbList, lemma, "BETWEEN", tid);
								int distance = Math.abs(doc.getTokenArr().indexOf(tid) 
										- doc.getTokenArr().indexOf(e2.getStartTokID()));
								candidates.put(distance, m);
							}
					    } else if (verbList.keySet().contains(lemma)) {
							Marker m = getVerbMarker(verbList, lemma, "BETWEEN", tid);
							int distance = Math.abs(doc.getTokenArr().indexOf(tid) 
									- doc.getTokenArr().indexOf(e2.getStartTokID()));
							candidates.put(distance, m);
						}
					}
				}
				
			}
			if (!candidates.isEmpty()) {
				Object[] keys = candidates.keySet().toArray();
				Arrays.sort(keys);
				return candidates.get(keys[0]);
			} else {
				return new Marker("O", "O", "O", "O", "O");
			}
		}
		return null;
	}
	
	public void addToVector(Feature feature) throws IOException {
		List<String> fields = null;
		if (this instanceof EventEventFeatureVector) {
			fields = EventEventFeatureVector.fields;
		} else if (this instanceof EventTimexFeatureVector) {
			fields = EventTimexFeatureVector.fields;
		}
		Marker m = null;
		if (fields != null) {
			switch(feature) {
				case id: 	
					getVectors().add(e1.getID());
					fields.set(getVectors().size()-1, "id1");
					getVectors().add(e2.getID());
					fields.set(getVectors().size()-1, "id2");
					break;
				case token: 	
					getVectors().add(getTokenAttribute(e1, Feature.token));
					fields.set(getVectors().size()-1, "token1");
					getVectors().add(getTokenAttribute(e2, Feature.token));
					fields.set(getVectors().size()-1, "token2");
					break;
				case lemma: 	
					getVectors().add(getTokenAttribute(e1, Feature.lemma));
					fields.set(getVectors().size()-1, "lemma1");
					getVectors().add(getTokenAttribute(e2, Feature.lemma));
					fields.set(getVectors().size()-1, "lemma2");
					break;
				case pos:
					getVectors().add(getCombinedTokenAttribute(Feature.pos));
					fields.set(getVectors().size()-1, "pos");
					break;
				case mainpos:
					getVectors().add(getCombinedTokenAttribute(Feature.mainpos));
					fields.set(getVectors().size()-1, "mainpos");
					break;
				case chunk:
					getVectors().add(getCombinedTokenAttribute(Feature.chunk));
					fields.set(getVectors().size()-1, "chunk");
					break;
				case ner:
					getVectors().add(getCombinedTokenAttribute(Feature.ner));
					fields.set(getVectors().size()-1, "ner");
					break;
				case samePos:
					getVectors().add(isSameTokenAttribute(Feature.pos) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "samePos");
					break;
				case sameMainPos:
					getVectors().add(isSameTokenAttribute(Feature.mainpos) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "sameMainPos");
					break;
				case entDistance:
					getVectors().add(getEntityDistance().toString());
					fields.set(getVectors().size()-1, "entDistance");
					break;
				case sentDistance:
					getVectors().add(getSentenceDistance().toString());
					fields.set(getVectors().size()-1, "sentDistance");
					break;
				case entOrder:
					getVectors().add(getOrder());
					fields.set(getVectors().size()-1, "entOrder");
					break;					
				case eventClass:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.eventClass) + "|" + 
								getEntityAttribute(e2, Feature.eventClass));
						fields.set(getVectors().size()-1, "eventClass1|eventClass2");
					} else if (this instanceof EventTimexFeatureVector) {
						this.getVectors().add(getEntityAttribute(e1, Feature.eventClass));
						fields.set(getVectors().size()-1, "eventClass");
					}
					break;
				case tense:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.tense) + "|" + 
								getEntityAttribute(e2, Feature.tense));
						fields.set(getVectors().size()-1, "tense1|tense2");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.tense));
						fields.set(getVectors().size()-1, "tense");
					}
					break;
				case aspect:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.aspect) + "|" + 
								getEntityAttribute(e2, Feature.aspect));
						fields.set(getVectors().size()-1, "aspect1|aspect2");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.aspect));
						fields.set(getVectors().size()-1, "aspect");
					}
					break;
				case tenseAspect:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.tense) + "-" + getEntityAttribute(e1, Feature.aspect) + "|" + 
								getEntityAttribute(e2, Feature.tense) + "-" + getEntityAttribute(e2, Feature.aspect));
						fields.set(getVectors().size()-1, "tense-aspect1|tense-aspect2");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.tense) + "-" + getEntityAttribute(e1, Feature.aspect));
						fields.set(getVectors().size()-1, "tense-aspect");
					}
					break;
				case polarity:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.polarity) + "|" + 
								getEntityAttribute(e2, Feature.polarity));
						fields.set(getVectors().size()-1, "polarity1|polarity2");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(getEntityAttribute(e1, Feature.polarity));
						fields.set(getVectors().size()-1, "polarity");
					}
					break;
				case sameEventClass:
					getVectors().add(getEntityAttribute(e1, Feature.eventClass).equals(getEntityAttribute(e2, Feature.eventClass)) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "sameEventClass");
					break;
				case sameTense:
					getVectors().add(getEntityAttribute(e1, Feature.tense).equals(getEntityAttribute(e2, Feature.tense)) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "sameTense");
					break;
				case sameAspect:
					getVectors().add(getEntityAttribute(e1, Feature.aspect).equals(getEntityAttribute(e2, Feature.aspect)) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "sameAspect");
					break;
				case sameTenseAspect:
					getVectors().add((getEntityAttribute(e1, Feature.tense).equals(getEntityAttribute(e2, Feature.tense)) &&
							getEntityAttribute(e1, Feature.aspect).equals(getEntityAttribute(e2, Feature.aspect))) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "sameTenseAspect");
					break;
				case samePolarity:
					getVectors().add(getEntityAttribute(e1, Feature.polarity).equals(getEntityAttribute(e2, Feature.polarity)) ? "TRUE" : "FALSE");
					fields.set(getVectors().size()-1, "samePolarity");
					break;
				case timexType:
					getVectors().add(getEntityAttribute(e2, Feature.timexType));
					fields.set(getVectors().size()-1, "timexType");
					break;
				case timexValue:
					getVectors().add(getEntityAttribute(e2, Feature.timexValue));
					fields.set(getVectors().size()-1, "timexValue");
					break;
				case timexValueTemplate:
					getVectors().add(getEntityAttribute(e2, Feature.timexValueTemplate));
					fields.set(getVectors().size()-1, "timexValueTemplate");
					break;
				case timexTypeValueTemplate:
					getVectors().add(getEntityAttribute(e2, Feature.timexType) + "|" + 
							getEntityAttribute(e2, Feature.timexValueTemplate));
					fields.set(getVectors().size()-1, "timexValueTemplate");
					break;
				case dct:
					getVectors().add(getEntityAttribute(e2, Feature.dct));
					fields.set(getVectors().size()-1, "dct");
					break;
				case mainVerb:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(((EventEventFeatureVector) this).getMateMainVerb());
						fields.set(getVectors().size()-1, "mainVerb1|mainVerb2");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(((EventTimexFeatureVector) this).getMateMainVerb());
						fields.set(getVectors().size()-1, "mainVerb");
					}
					break;
				case depPath:
					if (this instanceof EventEventFeatureVector) {
						getVectors().add(((EventEventFeatureVector) this).getMateDependencyPath());
						fields.set(getVectors().size()-1, "depPath");
					} else if (this instanceof EventTimexFeatureVector) {
						getVectors().add(((EventTimexFeatureVector) this).getMateDependencyPath());
						fields.set(getVectors().size()-1, "depPath");
					}
					break;
				case tempMarker:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerText-Position");
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerDep1-Dep2");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getCluster().replace(" ", "_") + "|" + m.getPosition());
							fields.set(getVectors().size()-1, "tempMarkerClusText-Position");
							getVectors().add(m.getDepRelE1() + "|" + m.getDepRelE2());
							fields.set(getVectors().size()-1, "tempMarkerDep1-Dep2");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getCluster().replace(" ", "_") + "|" + m.getPosition());
						fields.set(getVectors().size()-1, "tempMarkerClusText-Position");
						getVectors().add(m.getDepRelE1() + "|" + m.getDepRelE2());
						fields.set(getVectors().size()-1, "tempMarkerDep1-Dep2");
					}
					break;
				case tempMarkerText:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerText");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getText());
							fields.set(getVectors().size()-1, "tempMarkerText");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getText());
						fields.set(getVectors().size()-1, "tempMarkerText");
					}
					break;
				case tempMarkerClusText:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerClusText");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getCluster());
							fields.set(getVectors().size()-1, "tempMarkerClusText");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getCluster());
						fields.set(getVectors().size()-1, "tempMarkerClusText");
					}
					break;
				case tempMarkerPos:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerPos");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getPosition());
							fields.set(getVectors().size()-1, "tempMarkerPos");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getPosition());
						fields.set(getVectors().size()-1, "tempMarkerPos");
					}
					break;
				case tempMarkerDep1:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerDep1");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getDepRelE1());
							fields.set(getVectors().size()-1, "tempMarkerDep1");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getDepRelE1());
						fields.set(getVectors().size()-1, "tempMarkerDep1");
					}
					break;
				case tempMarkerDep2:
					if (this instanceof EventTimexFeatureVector) {
						//Assuming that the pair is already in event-timex order
						if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
							!isSameSentence()) {
							getVectors().add("O");
							fields.set(getVectors().size()-1, "tempMarkerDep2");
						} else {	
							m = getTemporalConnective();
							if (m.getText().equals("O")) m = getTemporalSignal();
							getVectors().add(m.getDepRelE2());
							fields.set(getVectors().size()-1, "tempMarkerDep2");
						}
					} else if (this instanceof EventEventFeatureVector) {
						m = getTemporalConnective();
						if (m.getText().equals("O")) m = getTemporalSignal();
						getVectors().add(m.getDepRelE2());
						fields.set(getVectors().size()-1, "tempMarkerText");
					}
					break;
				case causMarker:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getCluster().replace(" ", "_") + "|" + m.getPosition());
					fields.set(getVectors().size()-1, "causMarkerClusText-Position");
					getVectors().add(m.getDepRelE1() + "|" + m.getDepRelE2());
					fields.set(getVectors().size()-1, "causMarkerDep1-Dep2");
					break;
				case causMarkerText:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getText());
					fields.set(getVectors().size()-1, "causMarkerText");
					break;
				case causMarkerClusText:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getCluster());
					fields.set(getVectors().size()-1, "causMarkerClusTex");
					break;
				case causMarkerPos:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getPosition());
					fields.set(getVectors().size()-1, "causMarkerPos");
					break;
				case causMarkerDep1:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getDepRelE1());
					fields.set(getVectors().size()-1, "causMarkerDep1");
					break;
				case causMarkerDep2:
					m = getCausalConnective();
					if (m.getText().equals("O")) m = getCausalSignal();
					if (m.getText().equals("O")) m = getCausalVerb();
					getVectors().add(m.getDepRelE2());
					fields.set(getVectors().size()-1, "causMarkerDep2");
					break;
				case coref:
					getVectors().add(((EventEventFeatureVector) this).isCoreference() ? "COREF" : "NOCOREF");
					fields.set(getVectors().size()-1, "coref");
					break;
				case wnSim:
					getVectors().add(((EventEventFeatureVector) this).getWordSimilarity().toString());
					fields.set(getVectors().size()-1, "wnSim");
					break;
				case timexRule:
					getVectors().add(((EventTimexFeatureVector) this).getTimexRule());
					fields.set(getVectors().size()-1, "timexRule");
					break;
				case label:
					getVectors().add(getLabel());
					fields.set(getVectors().size()-1, "label");
					break;
			}
		}
	}
}