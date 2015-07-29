package parser.entities;

public class CausalRelation extends Relation{
	
	private EntityEnum.ClinkType relType;
	private String signal;

	public CausalRelation(Entity source, Entity target) {
		super(source, target);
		// TODO Auto-generated constructor stub
	}

	public EntityEnum.ClinkType getRelType() {
		return relType;
	}

	public void setRelType(EntityEnum.ClinkType relType) {
		this.relType = relType;
	}

	public String getSignal() {
		return signal;
	}

	public void setSignal(String signal) {
		this.signal = signal;
	}

}
