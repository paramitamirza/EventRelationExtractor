package model.feature;

public final class FeatureEnum {
	
	public static enum PairType { 
		event_event, event_timex, timex_timex; 
	}
	
	public static enum Feature {
		id,
		token, lemma, tokenSpace, lemmaSpace, tokenChunk,
		pos, mainpos, chunk, posCombined, mainposCombined, chunkCombined,
		ner, supersense, nerCombined, supersenseCombined,
		samePos, sameMainPos,
		entDistance, sentDistance, entOrder,
		mainVerb, depRel, depPath, depOrder,
		eventClass, tense, aspect, tenseAspect, polarity,
		eventClassCombined, tenseCombined, aspectCombined, tenseAspectCombined, polarityCombined,
		sameEventClass, sameTense, sameAspect, sameTenseAspect, samePolarity,
		timexType, timexValue, dct, timexValueTemplate, timexTypeValueTemplate,
		tempMarker, causMarker, tempMarkerText, causMarkerText, tempMarkerClusText, causMarkerClusText, 
		tempMarkerPos, causMarkerPos, 
		tempMarkerDep1, tempMarkerDep2, tempMarkerDep1Dep2,
		causMarkerDep1, causMarkerDep2, causMarkerDep1Dep2,
		coref, wnSim, timexRule,
		tempMarkerTextPhrase, causMarkerTextPhrase,
		label;
	}

}
