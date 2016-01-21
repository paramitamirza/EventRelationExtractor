package model.feature;

public final class FeatureEnum {
	
	public static enum PairType { 
		event_event, event_timex, timex_timex; 
	}
	
	public static enum FeatureName {
		id,
		token, lemma, tokenSpace, lemmaSpace, tokenChunk,
		pos, mainpos, chunk, posCombined, mainposCombined, chunkCombined,
		ner, supersense, nerCombined, supersenseCombined,
		samePos, sameMainPos,
		entDistance, sentDistance, entOrder,
		mainVerb, depRel, depPath, depOrder,
		depTmxPath, depEvPath,
		eventClass, tense, aspect, tenseAspect, polarity,
		eventClassCombined, tenseCombined, aspectCombined, tenseAspectCombined, polarityCombined,
		sameEventClass, sameTense, sameAspect, sameTenseAspect, samePolarity,
		timexType, timexValue, dct, timexValueTemplate, timexTypeValueTemplate,
		tempMarker, causMarker, tempMarkerText, causMarkerText, tempMarkerClusText, causMarkerClusText,
		tempMarkerClusTextPos, causMarkerClusTextPos,
		tempMarkerTextSpace, causMarkerTextSpace, tempMarkerClusTextSpace, causMarkerClusTextSpace,
		tempMarkerPos, causMarkerPos, 
		tempMarkerDep1, tempMarkerDep2, tempMarkerDep1Dep2,
		causMarkerDep1, causMarkerDep2, causMarkerDep1Dep2,
		coref, wnSim, timexRule,
		tempMarkerTextPhrase, causMarkerTextPhrase,
		tempSignalClusText, tempSignalText, tempSignalPos,
		tempSignal1ClusText, tempSignal1Text, tempSignal1Pos, tempSignal2ClusText, tempSignal2Text, tempSignal2Pos,
		causSignal1ClusText, causSignal1Text, causSignal1Pos, causSignal2ClusText, causSignal2Text, causSignal2Pos,
		causVerbClusText, causVerbPos,
		tempConnText, tempConnPos,
		label, labelCaus, 
		labelCollapsed,
		labelCollapsed1, labelCollapsed2, labelCollapsed3,
		labelCollapsed4, labelCollapsed5, labelCollapsed6,
		labelCollapsed01, labelCollapsed02, labelCollapsed03;
	}

}
