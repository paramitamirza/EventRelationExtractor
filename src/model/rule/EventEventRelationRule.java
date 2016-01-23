package model.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import model.feature.FeatureEnum.FeatureName;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.Event;
import parser.entities.Sentence;
import parser.entities.Timex;

public class EventEventRelationRule {
	
	private String relType;
	private Boolean identityRel=true;
	
	public EventEventRelationRule(Event e1, Event e2, Doc doc, String depPath,
			Boolean identity) {
		this(e1, e2, doc, depPath);
		this.setIdentityRel(identity);
	}
	
	public EventEventRelationRule(Event e1, Event e2, Doc doc, String depPath) {
		
		this.setRelType("O");
		
		String eventRule = getEventRule(e1, e2, doc, depPath); 
		if (!eventRule.equals("O")) {
			if (eventRule.equals("EV_SIM")) {
				this.setRelType("SIMULTANEOUS");
			} else if (eventRule.equals("EV_AFTER")) {
				this.setRelType("AFTER");
			} else if (eventRule.equals("EV_BEFORE")) {
				this.setRelType("BEFORE");
			} else if (eventRule.equals("EV_BEGIN")) {
				this.setRelType("BEGINS");
			} else if (eventRule.equals("EV_END")) {
				this.setRelType("ENDS");
			} else if (eventRule.equals("EV_INCL")) {
				this.setRelType("INCLUDES");
			} else if (eventRule.equals("EV_ISINCL")) {
				this.setRelType("IS_INCLUDED");
			} else if (eventRule.equals("EV_DURINV")) {
				this.setRelType("DURING_INV");
			} else if (eventRule.equals("EV_DUR")) {
				this.setRelType("DURING");
			}
		}
		if (!identityRel && this.getRelType().equals("IDENTITY")) {
			this.setRelType("SIMULTANEOUS");
		}
		/***** TempEval3 *****/
		if (this.getRelType().equals("DURING") || this.getRelType().equals("DURING_INV")) {
			this.setRelType("SIMULTANEOUS");
		}
	}
	
	
	public String getEventRule(Event e1, Event e2, Doc doc, String depPath) {
		String[] aspectual_initiation = {"begin", "start", "initiate", "commence", "launch"};
		String[] aspectual_termination = {"stop", "finish", "terminate", "cease"};
		String[] aspectual_continuation = {"continue", "retain", "keep"};
		String[] aspectual_remain = {"remain"};
		
		List<String> asp_init_list = Arrays.asList(aspectual_initiation);
		List<String> asp_term_list = Arrays.asList(aspectual_termination);
		List<String> asp_cont_list = Arrays.asList(aspectual_continuation);
		List<String> asp_remain_list = Arrays.asList(aspectual_remain);
				
		if (e1.getSentID().equals(e2.getSentID())) {	//in the same sentence
			Sentence s = doc.getSentences().get(e1.getSentID());
			ArrayList<String> entArr = s.getEntityArr();
			int eidx1 = entArr.indexOf(e1.getID());
			int eidx2 = entArr.indexOf(e2.getID());
			
			if (eidx1 < eidx2 && eidx2-eidx1 == 1
					&& depPath.equals("LGS-PMOD")) {
				return "EV_AFTER";
			} else if (eidx1 < eidx2 && eidx2-eidx1 == 1
					&& (depPath.equals("OPRD-IM") 
//							|| depPath.equals("OPRD")
						)
					) {
				if (asp_init_list.contains(doc.getTokens().get(e1.getStartTokID()).getLemma())) {
					return "EV_BEGIN";
				} else if (asp_term_list.contains(doc.getTokens().get(e1.getStartTokID()).getLemma())) {
					return "EV_END";
				} else if (asp_cont_list.contains(doc.getTokens().get(e1.getStartTokID()).getLemma())) {
					return "EV_INCL";
				} else if (asp_remain_list.contains(doc.getTokens().get(e1.getStartTokID()).getLemma())) {
					return "EV_DURINV";
				} else {
					if (e1.getAspect().equals("PERFECTIVE_PROGRESSIVE")) {
						return "EV_SIM";
					} else {
						return "EV_BEFORE";
					}
				}
			} else if (depPath.equals("LOC-PMOD")) {
				return "EV_ISINCL";
			} else if (depPath.equals("PMOD-LOC")) {
				return "EV_INCL";
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

	public Boolean getIdentityRel() {
		return identityRel;
	}

	public void setIdentityRel(Boolean identity) {
		this.identityRel = identity;
	}

}
