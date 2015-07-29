package parser.features.entities;

public class Entity {
	
	private String ID;
	private String startTokID;
	private String endTokID;
	
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
	
}
