package parser.features.entities;

public class Relation {
	
	private Entity source;
	private Entity target;
	
	public Relation(Entity source, Entity target) {
		this.setSource(source);
		this.setTarget(target);
	}

	public Entity getSource() {
		return source;
	}

	public void setSource(Entity source) {
		this.source = source;
	}

	public Entity getTarget() {
		return target;
	}

	public void setTarget(Entity target) {
		this.target = target;
	}

}
