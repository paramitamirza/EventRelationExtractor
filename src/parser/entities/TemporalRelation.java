package parser.entities;

public class TemporalRelation extends Relation{
	
	private String relType;
	private String signal;

	public TemporalRelation(String source, String target) {
		super(source, target);
		// TODO Auto-generated constructor stub
	}

	public String getSignal() {
		return signal;
	}

	public void setSignal(String signal) {
		this.signal = signal;
	}

	public String getRelType() {
		return relType;
	}

	public void setRelType(String relType) {
		this.relType = relType;
	}
}
