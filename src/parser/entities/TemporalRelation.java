package parser.entities;

import java.util.Arrays;
import java.util.List;

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
	
	public static String getInverseRelation(String relType) {
        String[] relations = {"BEFORE", "AFTER", "INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", 
        	"IBEFORE", "IAFTER", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
        List<String> rels = Arrays.asList(relations);
        
        if (rels.contains(relType)) {
        	Integer relIdx = rels.indexOf(relType);
        	if (rels.indexOf(relType) % 2 == 0) {
        		return rels.get(relIdx + 1);
        	} else {
        		return rels.get(relIdx - 1);
        	}
        }
        else {
            return relType;
        }
	}
}
