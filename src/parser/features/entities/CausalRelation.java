package parser.features.entities;

public class CausalRelation extends Relation{
	
	private enum Type {CAUSE, ENABLE, PREVENT};
	private Type relType;
	private String signal;

	public CausalRelation(Entity source, Entity target) {
		super(source, target);
		// TODO Auto-generated constructor stub
	}

	public Type getRelType() {
		return relType;
	}

	public void setRelType(Type relType) {
		this.relType = relType;
	}

	public String getSignal() {
		return signal;
	}

	public void setSignal(String signal) {
		this.signal = signal;
	}

}
