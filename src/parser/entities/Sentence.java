package parser.entities;

import java.util.ArrayList;

public class Sentence {
	
	private String ID;
	private Integer index;
	private String startTokID;
	private String endTokID;
	private ArrayList<String> entityArr;
	
	public Sentence(String id, String start, String end) {
		this.setID(id);
		this.setStartTokID(start);
		this.setEndTokID(end);
		this.setEntityArr(new ArrayList<String>());
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

	public ArrayList<String> getEntityArr() {
		return entityArr;
	}

	public void setEntityArr(ArrayList<String> entityArr) {
		this.entityArr = entityArr;
	}

}
