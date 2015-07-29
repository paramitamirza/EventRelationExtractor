package parser.entities;

public class TemporalRelation extends Relation{
	
	private EntityEnum.TlinkType relType;
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

	public EntityEnum.TlinkType getRelType() {
		return relType;
	}

	public void setRelType(EntityEnum.TlinkType relType) {
		this.relType = relType;
	}

}
