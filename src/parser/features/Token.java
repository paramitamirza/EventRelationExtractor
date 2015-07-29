package parser.features;

import java.util.HashMap;
import java.util.Map;

public class Token {
	
	private String ID;
	private String sentID;
	private String text;
	private String lemma;
	private String pos;
	private String mainPos;
	private String chunk;
	private String namedEntity;
	private String wnSupersense;
	private String discourseConn;
	private Boolean mainVerb;
	private Map<String, String> dependencyRel;
	private String eventID;
	private String timexID;
	private String cSignalID;
	
	public Token(String id) {
		this.setID(id);
		dependencyRel = new HashMap<String, String>();
	}
	
	public Token(String id, String sentid, String text) {
		this.ID = id;
		this.sentID = sentid;
		this.text = text;
		dependencyRel = new HashMap<String, String>();
	}
	
	public void setLemmaPosChunk(String lemma, String pos, String mainPos, String chunk) {
		this.lemma = lemma;
		this.pos = pos;
		this.mainPos = mainPos;
		this.chunk = chunk;
	}
	
	public void setTimeEntities(String eventid, String timexid, String csignalid) {
		this.eventID = eventid;
		this.timexID = timexid;
		this.cSignalID = csignalid;
	}
	
	public void setDependencyInfo(Boolean main, Map<String, String> dependencyRel) {
		this.mainVerb = main;
		this.dependencyRel = dependencyRel;
	}

	public String getID() {
		return ID;
	}

	public void setID(String iD) {
		ID = iD;
	}

	public String getSentID() {
		return sentID;
	}

	public void setSentID(String sentID) {
		this.sentID = sentID;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getLemma() {
		return lemma;
	}

	public void setLemma(String lemma) {
		this.lemma = lemma;
	}

	public String getPos() {
		return pos;
	}

	public void setPos(String pos) {
		this.pos = pos;
	}

	public String getChunk() {
		return chunk;
	}

	public void setChunk(String chunk) {
		this.chunk = chunk;
	}

	public String getNamedEntity() {
		return namedEntity;
	}

	public void setNamedEntity(String namedEntity) {
		this.namedEntity = namedEntity;
	}

	public String getWnSupersense() {
		return wnSupersense;
	}

	public void setWnSupersense(String wnSupersense) {
		this.wnSupersense = wnSupersense;
	}

	public String getDiscourseConn() {
		return discourseConn;
	}

	public void setDiscourseConn(String discourseConn) {
		this.discourseConn = discourseConn;
	}

	public Map<String, String> getDependencyRel() {
		return dependencyRel;
	}

	public void setDependencyRel(Map<String, String> dependencyRel) {
		this.dependencyRel = dependencyRel;
	}

	public String getEventID() {
		return eventID;
	}

	public void setEventID(String eventID) {
		this.eventID = eventID;
	}

	public String getTimexID() {
		return timexID;
	}

	public void setTimexID(String timexID) {
		this.timexID = timexID;
	}

	public String getcSignalID() {
		return cSignalID;
	}

	public void setcSignalID(String cSignalID) {
		this.cSignalID = cSignalID;
	}

	public boolean isMainVerb() {
		return mainVerb;
	}

	public void setMainVerb(boolean mainVerb) {
		this.mainVerb = mainVerb;
	}

	public String getMainPos() {
		return mainPos;
	}

	public void setMainPos(String mainPos) {
		this.mainPos = mainPos;
	}
}