package model.classifier;

import java.util.Arrays;

import model.classifier.PairClassifier.PairType;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.FeatureEnum.FeatureName;

public class EventEventCausalClassifier extends PairClassifier {
	
	private void initFeatureVector() {
		
		super.setPairType(PairType.event_event);
				
		if (classifier.equals(VectorClassifier.none)) {
			FeatureName[] eeFeatures = {
					FeatureName.tokenSpace, FeatureName.lemmaSpace,
					FeatureName.tokenChunk,
					FeatureName.tempMarkerTextSpace, FeatureName.causMarkerTextSpace
			};
			featureList = Arrays.asList(eeFeatures);
			
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			FeatureName[] eeFeatures = {
					/*Feature.token,*/ /*Feature.lemma,*/
					/*Feature.supersense,*/
					/*Feature.pos,*/ FeatureName.mainposCombined,
					/*Feature.samePos,*/ FeatureName.sameMainPos,
					FeatureName.chunkCombined,
					FeatureName.entDistance, FeatureName.sentDistance,
					/*Feature.eventClass,*/ FeatureName.tenseCombined, FeatureName.aspectCombined, FeatureName.polarityCombined,
//					Feature.sameEventClass, Feature.sameTenseAspect, /*Feature.sameAspect,*/ Feature.samePolarity,
					FeatureName.depEvPath,				
					FeatureName.mainVerb,
					FeatureName.tempMarkerClusText,
					FeatureName.tempMarkerPos,
					FeatureName.tempMarkerDep1Dep2,
//					Feature.tempConnText,
//					Feature.tempConnPos,
					FeatureName.causMarkerClusText,
					FeatureName.causMarkerPos, 
					FeatureName.causMarkerDep1Dep2,
//					Feature.causSignal1ClusText,
//					Feature.causSignal1Pos,
//					Feature.causSignal2ClusText,
//					Feature.causSignal2Pos,
//					Feature.causVerbClusText,
//					Feature.causVerbPos,
					FeatureName.coref,
					/*eature.wnSim*/
			};
			featureList = Arrays.asList(eeFeatures);
			
		} else {
			FeatureName[] eeFeatures = {
//					Feature.lemma,
					/*Feature.pos,*/ FeatureName.mainpos,
					/*Feature.samePos,*/ FeatureName.sameMainPos,
					FeatureName.chunk,
					FeatureName.entDistance, FeatureName.sentDistance,
					/*Feature.eventClass,*/ FeatureName.tense, FeatureName.aspect, FeatureName.polarity,
//					Feature.sameEventClass, Feature.sameTenseAspect, /*Feature.sameAspect,*/ Feature.samePolarity,
					FeatureName.depEvPath,			
					FeatureName.mainVerb,
//					Feature.tempSignalClusText,
//					Feature.tempSignalPos,
//					Feature.tempSignal1ClusText,
//					Feature.tempSignal1Pos,
//					Feature.tempSignal2ClusText,
//					Feature.tempSignal2Pos,
					FeatureName.causMarkerClusText,
					FeatureName.causMarkerPos,
					FeatureName.causMarkerDep1Dep2,
//					Feature.causSignal1ClusText,
//					Feature.causSignal1Pos,
//					Feature.causSignal2ClusText,
//					Feature.causSignal2Pos,
//					Feature.causVerbClusText,
//					Feature.causVerbPos,
					FeatureName.coref,
					/*Feature.wnSim*/
			};
			featureList = Arrays.asList(eeFeatures);
		}
	}
	
	public EventEventCausalClassifier(String taskName, 
			String classifier) throws Exception {
		super(taskName, classifier);
		initFeatureVector();
	}
	
	public EventEventCausalClassifier(String taskName, 
			String classifier, String feature, 
			String lblGrouping, String probVecFile) throws Exception {
		super(taskName, classifier,
				feature, lblGrouping, probVecFile);
		initFeatureVector();
	}
	
	public EventEventCausalClassifier(String taskName,
			String classifier, String inconsistency) throws Exception {
		super(taskName, classifier,
				inconsistency);
		initFeatureVector();
	}

	public EventEventCausalClassifier(String taskName, 
			String classifier, String feature, 
			String lblGrouping, String probVecFile,
			String inconsistency) throws Exception {
		super(taskName, classifier,
				feature, lblGrouping, probVecFile, inconsistency);
		initFeatureVector();
	}
	
}
