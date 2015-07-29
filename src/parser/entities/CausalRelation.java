package parser.entities;

public class CausalRelation extends Relation{
	
	private String signal;

	public CausalRelation(String source, String target) {
		super(source, target);
		// TODO Auto-generated constructor stub
	}

	public String getSignal() {
		return signal;
	}

	public void setSignal(String signal) {
		this.signal = signal;
	}
}
