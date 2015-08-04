package model.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import model.feature.FeatureEnum.Feature;
import parser.entities.Document;
import parser.entities.Entity;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.Timex;

public class EventTimexFeatureVector extends FeatureVector{

	public EventTimexFeatureVector(Document doc, Entity e1, Entity e2, String label, SignalList signalList) {
		super(doc, e1, e2, label, signalList);
		orderPair();
	}
	
	public EventTimexFeatureVector(FeatureVector fv) {
		super(fv.getDoc(), fv.getE1(), fv.getE2(), fv.getVectors(), fv.getLabel(), fv.getSignalList());
		orderPair();
	}
	
	public void orderPair() {
		//if in timex-event order, switch!
		if (e1 instanceof Timex && e2 instanceof Event) {
			Entity temp = e1;
			this.setE1(e2);
			this.setE2(temp);
			this.setLabel(TemporalRelation.getInverseRelation(label));
		}
	}
	
	protected String getEntityAttribute(Entity e, Feature feature) {
		if (e instanceof Event) {
			return ((Event)e).getAttribute(feature);
		} else if (e instanceof Timex){
			return ((Timex)e).getAttribute(feature);
		}
		return null;
	}
	
	public ArrayList<String> getEntityAttributes() {
		ArrayList<String> entityAttrs = new ArrayList<String>();
		
		entityAttrs.add(getEntityAttribute(e1, Feature.eventClass));
		entityAttrs.add(getEntityAttribute(e1, Feature.tense));
		entityAttrs.add(getEntityAttribute(e1, Feature.aspect));
		entityAttrs.add(getEntityAttribute(e1, Feature.polarity));
		
		entityAttrs.add(getEntityAttribute(e2, Feature.timexType));
		entityAttrs.add(getEntityAttribute(e2, Feature.timexValue));
		entityAttrs.add(getEntityAttribute(e2, Feature.timexValueTemplate));
		entityAttrs.add(getEntityAttribute(e2, Feature.dct));
		
		return entityAttrs;
	}
	
	public String getMateDependencyPath() {
		//Assuming that the pair is already in event-timex order
		if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag())) {
			return "O";
		} else if (isSameSentence()){
			ArrayList<String> tokenArr1 = getTokenIDArr(e1.getStartTokID(), e1.getEndTokID());
			ArrayList<String> tokenArr2 = getTokenIDArr(e2.getStartTokID(), e2.getEndTokID());
			
			for (String govID : tokenArr1) {
				List<String> paths = new ArrayList<String>();
				List<String> visited = new ArrayList<String>();
				if (getTokenAttribute(e1, Feature.mainpos).equals("v")) {
					govID = getHeadVerb(govID);
				} else if (getTokenAttribute(e1, Feature.mainpos).equals("adj") &&
					getVerbFromAdj(govID) != null) {
					govID = getVerbFromAdj(govID);
				}
				generateDependencyPath(govID, tokenArr2, paths, "", visited);
				if (!paths.isEmpty()) {
					return paths.get(0).substring(1);
				}
				if (getCoordVerb(govID) != null) {
					generateDependencyPath(getCoordVerb(govID), tokenArr2, paths, "", visited);
					if (!paths.isEmpty()) {
						return paths.get(0).substring(1);
					}
				}
			}
		} 
		return "O";
	}
	
	public String getMateMainVerb() {
		return super.getMateMainVerb(e1);
	}
	
	public ArrayList<String> getTemporalSignal() throws IOException {
		ArrayList<String> tSignals = new ArrayList<String>();
		
		//Assuming that the pair is already in event-timex order
		if ((e2 instanceof Timex && ((Timex)e2).isDct()) || (e2 instanceof Timex && ((Timex)e2).isEmptyTag()) ||
			!isSameSentence()) {
			tSignals.add("O");
			tSignals.add("O");
			tSignals.add("O");
			tSignals.add("O");
			tSignals.add("O");
			tSignals.add("O");
		} else {	
			Marker me1 = super.getTemporalSignal(e1);
			Marker me2 = super.getTemporalSignal(e2);
			tSignals.add(me1.getText());
			tSignals.add(me1.getPosition());
			tSignals.add(me1.getDepRel());
			tSignals.add(me2.getText());
			tSignals.add(me2.getPosition());
			tSignals.add(me2.getDepRel());
		}
		return tSignals;
	}

}
