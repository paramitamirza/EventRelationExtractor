package parser.entities;

public class Event extends Entity{
	
	private String eventClass;
	private String tense;
	private String aspect;
	private String polarity;

	public Event(String id, String start, String end) {
		super(id, start, end);
		// TODO Auto-generated constructor stub
	}
	
	public void setAttributes(String eventClass, String tense, String aspect, String pol) {
		this.eventClass = eventClass;
		this.tense = tense;
		this.aspect = aspect;
		this.polarity = pol;
	}

	public String getEventClass() {
		return eventClass;
	}

	public void setEventClass(String eventClass) {
		this.eventClass = eventClass;
	}

	public String getTense() {
		return tense;
	}

	public void setTense(String tense) {
		this.tense = tense;
	}

	public String getAspect() {
		return aspect;
	}

	public void setAspect(String aspect) {
		this.aspect = aspect;
	}

	public String getPolarity() {
		return polarity;
	}

	public void setPolarity(String polarity) {
		this.polarity = polarity;
	}

	
	
}
