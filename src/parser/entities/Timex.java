package parser.entities;

public class Timex extends Entity{
	
	private String type;
	private String value;
	private Boolean dct;
	private Boolean emptyTag;

	public Timex(String id, String start, String end) {
		super(id, start, end);
		// TODO Auto-generated constructor stub
	}
	
	public void setAttributes(String type, String value) {
		this.type = type;
		this.value = value;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Boolean getDct() {
		return dct;
	}

	public void setDct(Boolean dct) {
		this.dct = dct;
	}

	public Boolean getEmptyTag() {
		return emptyTag;
	}

	public void setEmptyTag(Boolean emptyTag) {
		this.emptyTag = emptyTag;
	}
	
}
