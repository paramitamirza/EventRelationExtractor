package model.rule;

import java.util.ArrayList;

import model.feature.FeatureEnum.FeatureName;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.Event;
import parser.entities.Sentence;
import parser.entities.Timex;

public class EventTimexRelationRule {
	
	private String relType;
	private Boolean measureRel=false;
	
	public EventTimexRelationRule(Event e1, Timex t2, Doc doc, String depPath,
			Boolean measureRel) {
		this(e1, t2, doc, depPath);
		this.setMeasureRel(measureRel);
	}
	
	public EventTimexRelationRule(Event ev, Timex tmx, Doc doc, String depPath) {
		
		this.setRelType("O");
		
		String timexRule = getTimexRule(ev, tmx, doc, depPath); 
		if (!timexRule.equals("O")) {
			if (timexRule.equals("TMX-BEGIN")) {
				this.setRelType("BEGUN_BY");
			} else if (timexRule.equals("TMX_END")) {
				this.setRelType("ENDED_BY");
			}
		}
		if (measureRel && tmx.getType().equals("DURATION")) {
			this.setRelType("MEASURE");
		}
	}
	
	/**
	 * Feature for timespan timexes
	 * e.g. "between" tmx1 "and" tmx2, "from" tmx1 "to" tmx 2, tmx1 "-" tmx2, tmx1 "until" tmx2
	 *      we said that timex is tmx1 is "TMX-BEGIN" and tmx2 is "TMX-END"
	 * @return String timexRule
	 */
	public String getTimexRule(Event ev, Timex tmx, Doc doc, String depPath) {
		if (!tmx.isDct() && !tmx.isEmptyTag()) {	//not DCT not empty timex
			if (ev.getSentID().equals(tmx.getSentID())) {	//in the same sentence
				Sentence s = doc.getSentences().get(tmx.getSentID());
				ArrayList<String> entArr = s.getEntityArr();
				int eidx = entArr.indexOf(tmx.getID());
				
				int tidxStart = doc.getTokens().get(tmx.getStartTokID()).getIndex();
				int tidxStartSent = doc.getTokens().get(s.getStartTokID()).getIndex();
				
				if (tidxStart > tidxStartSent) {
					
					if (depPath.contains("TMP-PMOD") 
							&& (!depPath.contains("OBJ")
									&& !depPath.contains("SUB")
									&& !depPath.contains("NMOD"))) {
						String beforeTmx = doc.getTokens().get(doc.getTokenArr().get(tidxStart-1)).getTokenAttribute(FeatureName.lemma);
						if ((beforeTmx.equals("for")
								|| beforeTmx.equals("during"))
								&& tmx.getType().equals("DURATION")) {
							this.setRelType("SIMULTANEOUS");
						} else if ((beforeTmx.equals("in"))
								&& tmx.getType().equals("DURATION")) {
							this.setRelType("IS_INCLUDED");
						} else if ((beforeTmx.equals("in") || beforeTmx.equals("at") || beforeTmx.equals("on"))
								&& (tmx.getType().equals("DATE") || tmx.getType().equals("TIME"))) {
							this.setRelType("IS_INCLUDED");
						}
					}
					
					if (eidx < entArr.size()-1 && doc.getEntities().get(entArr.get(eidx+1)) instanceof Timex) {
						Entity tmx2 = doc.getEntities().get(entArr.get(eidx+1));
						int tmx2Idx = doc.getTokens().get(tmx2.getStartTokID()).getIndex();
						String beforeTmx1 = doc.getTokens().get(doc.getTokenArr().get(tidxStart-1)).getTokenAttribute(FeatureName.lemma);
						String beforeTmx2 = doc.getTokens().get(doc.getTokenArr().get(tmx2Idx-1)).getTokenAttribute(FeatureName.lemma);
						
						if (beforeTmx1.equals("between") && beforeTmx2.equals("and")) {
							return "TMX-BEGIN";
						} else if (beforeTmx1.equals("from") && 
								(beforeTmx2.equals("to") || beforeTmx2.equals("until") || beforeTmx2.equals("till"))) {
							return "TMX-BEGIN";
						} else if (beforeTmx2.equals("-")) {
							return "TMX-BEGIN";
						} else if (beforeTmx2.equals("until") || beforeTmx2.equals("until")) {
							return "TMX-BEGIN";
						}
					} else if (eidx > 0 && doc.getEntities().get(entArr.get(eidx-1)) instanceof Timex) {
						Entity tmx1 = doc.getEntities().get(entArr.get(eidx-1));
						int tmx1Idx = doc.getTokens().get(tmx1.getStartTokID()).getIndex();
						String beforeTmx1 = doc.getTokens().get(doc.getTokenArr().get(tmx1Idx-1)).getTokenAttribute(FeatureName.lemma);
						String beforeTmx2 = doc.getTokens().get(doc.getTokenArr().get(tidxStart-1)).getTokenAttribute(FeatureName.lemma);
						
						if (beforeTmx1.equals("between") && beforeTmx2.equals("and")) {
							return "TMX-END";
						} else if (beforeTmx1.equals("from") && 
								(beforeTmx2.equals("to") || beforeTmx2.equals("until") || beforeTmx2.equals("till"))) {
							return "TMX-END";
						} else if (beforeTmx2.equals("-")) {
							return "TMX-END";
						} else if (beforeTmx2.equals("until") || beforeTmx2.equals("until")) {
							return "TMX-END";
						}
					}
				}
			}
		}
		return "O";
	}
	
	public String getRelType() {
		return relType;
	}

	public void setRelType(String relType) {
		this.relType = relType;
	}
	
	public Boolean isMeasureRel() {
		return measureRel;
	}

	public void setMeasureRel(Boolean measureRel) {
		this.measureRel = measureRel;
	}

}
