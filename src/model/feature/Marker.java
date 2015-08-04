package model.feature;

public class Marker {
	
	private String txt;
	private String position;
	private String depRel;
	
	public Marker() {
		
	}
	
	public Marker(String txt, String position, String depRel) {
		this.setText(txt);
		this.setPosition(position);
		this.setDepRel(depRel);
	}
	
	public String getText() {
		return txt;
	}
	
	public void setText(String text) {
		this.txt = text;
	}
	
	public String getPosition() {
		return position;
	}
	
	public void setPosition(String position) {
		this.position = position;
	}
	
	public String getDepRel() {
		return depRel;
	}
	
	public void setDepRel(String depRel) {
		this.depRel = depRel;
	}

}
