package model.feature;

public final class FeatureEnum {
	
	public static enum PairType { 
		event_event, event_timex, timex_timex; 
	}
	
	public static enum Feature {
		token, lemma,
		pos, mainpos, chunk,
		ner, supersense,
		entDistance, sentDistance, entOrder,
		mainVerb, depRel, depPath, depOrder,
		eventClass, tense, aspect, polarity,
		timexType, timexValue, dct, timexValueTemplate;
	}

}
