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
	
	protected ArrayList<String> getTokenIDArr(Doc doc, String startTokID, String endTokID) {
		ArrayList<String> tokIDs = new ArrayList<String>();
		int startTokIdx = doc.getTokens().get(startTokID).getIndex();
		int endTokIdx = doc.getTokens().get(endTokID).getIndex();
		for (int i=startTokIdx; i<endTokIdx+1; i++) {
			tokIDs.add(doc.getTokenArr().get(i));
		}
		return tokIDs;
	}
	
	protected String getString(Doc doc, String startTokID, String endTokID) {
		ArrayList<String> tokIDs = getTokenIDArr(doc, startTokID, endTokID);
		ArrayList<String> context = new ArrayList<String>();
		for (String tokID : tokIDs) {
			context.add(doc.getTokens().get(tokID).getTokenAttribute(FeatureName.lemma).toLowerCase());
		}
		return String.join(" ", context);
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
				
				int tidxEnd = doc.getTokens().get(tmx.getEndTokID()).getIndex();
				int tidxEndSent = doc.getTokens().get(s.getEndTokID()).getIndex();
				
				if (tidxStart > tidxStartSent) {
					
//					if (depPath.equals("NMOD-PMOD")) {
//						String tidAfterEnd;
//						if (tidxEnd+1 < tidxEndSent) {
//							tidAfterEnd = doc.getTokenArr().get(tidxEnd+1);
//							String tidAfter = doc.getTokenArr().get(tidxEnd+1);
//							String afterTmx = getString(doc, tidAfter, tidAfterEnd);
//							if (afterTmx.equals("of")
//									&& tmx.getType().equals("DURATION")) {
//								this.setRelType("DURING");
//							}
//						}
//					}
//					
//					else 
					if (depPath.contains("TMP") 
							&& (!depPath.contains("OBJ")
							&& !depPath.contains("SUB")
							&& !depPath.contains("NMOD"))
							) {
						
						String tidBeforeStart;
						if (tidxStart-1 > 0) tidBeforeStart = doc.getTokenArr().get(tidxStart-1);
						else tidBeforeStart = doc.getTokenArr().get(tidxStartSent);
						String tidBefore = doc.getTokenArr().get(tidxStart-1);
						String beforeTmx = getString(doc, tidBeforeStart, tidBefore);
						
						if ((beforeTmx.equals("for")
								|| beforeTmx.equals("during")
								|| beforeTmx.equals("through")
								|| beforeTmx.equals("throughout"))
								&& tmx.getType().equals("DURATION")) {
							if (depPath.contains("OPRD-IM")) {
								this.setRelType("BEFORE");
							} else {
								this.setRelType("DURING");
							}
						} else if ((beforeTmx.equals("in")
								|| beforeTmx.equals("within"))
								&& tmx.getType().equals("DURATION")) {
							if (depPath.contains("OPRD-IM")) {
								this.setRelType("BEFORE");
							} else {
								this.setRelType("IS_INCLUDED");
							}
						} else if ((beforeTmx.equals("in") || beforeTmx.equals("at") || beforeTmx.equals("on"))
								&& (tmx.getType().equals("DATE") || tmx.getType().equals("TIME"))) {
							this.setRelType("IS_INCLUDED");
						} else if (beforeTmx.equals("after")) {
							this.setRelType("AFTER");
						} else if (beforeTmx.equals("before")) {
							this.setRelType("BEFORE");
						} else if (beforeTmx.equals("from") || beforeTmx.equals("since")) {
							this.setRelType("BEGUN_BY");
						} else if ((beforeTmx.equals("until") || beforeTmx.equals("till"))
								&& ev.getPolarity().equals("POS")) {
							this.setRelType("ENDED_BY");
						} else {
							this.setRelType("IS_INCLUDED");
						}
					}
					
					if (eidx < entArr.size()-1 && doc.getEntities().get(entArr.get(eidx+1)) instanceof Timex) {
						Entity tmx2 = doc.getEntities().get(entArr.get(eidx+1));
						int tmx2Idx = doc.getTokens().get(tmx2.getStartTokID()).getIndex();
						String beforeTmx1 = doc.getTokens().get(doc.getTokenArr().get(tidxStart-1)).getTokenAttribute(FeatureName.lemma);
						String beforeTmx2 = doc.getTokens().get(doc.getTokenArr().get(tmx2Idx-1)).getTokenAttribute(FeatureName.lemma);
						
						if (((Timex) tmx2).getType().equals("DATE") || ((Timex) tmx2).getType().equals("TIME")) {
							if (beforeTmx1.equals("between") && beforeTmx2.equals("and")) {
								return "TMX-BEGIN";
							} else if (beforeTmx1.equals("from") && 
									(beforeTmx2.equals("to") || beforeTmx2.equals("until") || beforeTmx2.equals("till"))) {
								return "TMX-BEGIN";
							} else if (beforeTmx2.equals("-")) {
								return "TMX-BEGIN";
							} else if (beforeTmx2.equals("until") || beforeTmx2.equals("till")) {
								return "TMX-BEGIN";
							}
						}
						
					} else if (eidx > 0 && doc.getEntities().get(entArr.get(eidx-1)) instanceof Timex) {
						Entity tmx1 = doc.getEntities().get(entArr.get(eidx-1));
						int tmx1Idx = doc.getTokens().get(tmx1.getStartTokID()).getIndex();
						String beforeTmx1 = doc.getTokens().get(doc.getTokenArr().get(tmx1Idx-1)).getTokenAttribute(FeatureName.lemma);
						String beforeTmx2 = doc.getTokens().get(doc.getTokenArr().get(tidxStart-1)).getTokenAttribute(FeatureName.lemma);
						
						if (((Timex) tmx1).getType().equals("DATE") || ((Timex) tmx1).getType().equals("TIME")) {
							if (beforeTmx1.equals("between") && beforeTmx2.equals("and")) {
								return "TMX-END";
							} else if (beforeTmx1.equals("from") && 
									(beforeTmx2.equals("to") || beforeTmx2.equals("until") || beforeTmx2.equals("till"))) {
								return "TMX-END";
							} else if (beforeTmx2.equals("-")) {
								return "TMX-END";
							} else if (beforeTmx2.equals("until")) {
								return "TMX-END";
							}
						}
					}
				}
			}
		} else if (tmx.isDct()) {
			if (ev.getTense().equals("FUTURE")) {
				this.setRelType("AFTER");
				
			} else if (ev.getTense().equals("PRESENT")
					&& (ev.getAspect().equals("PROGRESSIVE")
						|| ev.getAspect().equals("PERFECTIVE_PROGRESSIVE"))) {
				this.setRelType("INCLUDES");
				
			} else if (ev.getTense().equals("PAST")
					&& ev.getAspect().equals("PERFECTIVE")) {
				this.setRelType("BEFORE");
				
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
