package parser.features.entities;

public class TemporalRelation extends Relation{
	
	private enum Type {BEFORE, AFTER, IBEFORE, IAFTER, BEGINS, BEGUN_BY, ENDS, ENDED_BY,
		INCLUDES, IS_INCLUDED, DURING, DURING_INV, MEASURE, SIMULTANEOUS, IDENTITY};
	private Type relType;
	private String signal;

	public TemporalRelation(Entity source, Entity target) {
		super(source, target);
		// TODO Auto-generated constructor stub
	}

	public String getSignal() {
		return signal;
	}

	public void setSignal(String signal) {
		this.signal = signal;
	}

	public Type getRelType() {
		return relType;
	}

	public void setRelType(Type relType) {
		this.relType = relType;
	}

}
