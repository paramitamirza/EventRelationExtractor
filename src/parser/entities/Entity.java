package parser.entities;

public class Entity {
	
	private String ID;
	private Integer index;
	private String startTokID;
	private String endTokID;
	private String sentID;
	
	public Entity(String id, String start, String end) {
		this.ID = id;
		this.startTokID = start;
		this.endTokID = end;
	}
	
	public String getID() {
		return ID;
	}
	
	public void setID(String iD) {
		ID = iD;
	}
	
	public String getStartTokID() {
		return startTokID;
	}
	
	public void setStartTokID(String startTokID) {
		this.startTokID = startTokID;
	}
	
	public String getEndTokID() {
		return endTokID;
	}
	
	public void setEndTokID(String endTokID) {
		this.endTokID = endTokID;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public String getSentID() {
		return sentID;
	}

	public void setSentID(String sentID) {
		this.sentID = sentID;
	}
	
}
