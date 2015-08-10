package model.feature;

public final class FeatureEnum {
	
	public static enum PairType { 
		event_event, event_timex, timex_timex; 
	}
	
	public static enum Feature {
		id,
		token, lemma,
		pos, mainpos, chunk,
		ner, supersense,
		samePos, sameMainPos,
		entDistance, sentDistance, entOrder,
		mainVerb, depRel, depPath, depOrder,
		eventClass, tense, aspect, tenseAspect, polarity,
		sameEventClass, sameTense, sameAspect, sameTenseAspect, samePolarity,
		timexType, timexValue, dct, timexValueTemplate, timexTypeValueTemplate,
		tempMarker, causMarker, tempMarkerText, causMarkerText, tempMarkerClusText, causMarkerClusText, 
		tempMarkerPos, causMarkerPos, tempMarkerDep1, tempMarkerDep2, causMarkerDep1, causMarkerDep2,
		coref, wnSim, timexRule,
		label;
	}

}
