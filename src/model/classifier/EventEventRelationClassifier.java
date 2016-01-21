package model.classifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import model.classifier.PairClassifier.PairType;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.PairFeatureVector;
import model.feature.FeatureEnum.FeatureName;

import de.bwaldvogel.liblinear.*;
import evaluator.PairEvaluator;

public class EventEventRelationClassifier extends PairClassifier {
	
	private String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	
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
					FeatureName.token, FeatureName.lemma,
					FeatureName.pos, /*Feature.mainpos,*/
					FeatureName.samePos, /*Feature.sameMainPos,*/
					/*Feature.chunk,*/
					FeatureName.entDistance, FeatureName.sentDistance,
					FeatureName.eventClass, FeatureName.tense, FeatureName.aspect, FeatureName.polarity,
					FeatureName.sameEventClass, FeatureName.sameTense, FeatureName.sameAspect, FeatureName.samePolarity,
					FeatureName.depEvPath,				
					FeatureName.mainVerb,
					FeatureName.tempSignalText,
					FeatureName.tempSignalPos,
					FeatureName.tempConnText,
					FeatureName.tempConnPos,
					/*Feature.causMarkerClusTextPos,*/
					/*Feature.causMarkerPos,*/ 
					/*Feature.causMarkerDep1Dep2,*/
					/*Feature.coref,*/
					/*Feature.wnSim*/
			};
			featureList = Arrays.asList(eeFeatures);
			
		} else {
			FeatureName[] eeFeatures = {
					FeatureName.pos, /*Feature.mainpos,*/
					FeatureName.samePos, /*Feature.sameMainPos,*/
					FeatureName.chunk,
					FeatureName.entDistance, FeatureName.sentDistance,
					FeatureName.eventClass, FeatureName.tense, FeatureName.aspect, FeatureName.polarity,
					FeatureName.sameEventClass, FeatureName.sameTenseAspect, /*Feature.sameAspect,*/ FeatureName.samePolarity,
					FeatureName.depEvPath,			
					FeatureName.mainVerb,
//					Feature.tempSignalClusText,
//					Feature.tempSignalPos,
//					Feature.tempSignal1ClusText,
//					Feature.tempSignal1Pos,
					FeatureName.tempSignal2ClusText,
					FeatureName.tempSignal2Pos,
//					Feature.causMarkerClusText,
//					Feature.causMarkerPos,
//					/*Feature.coref,*/
					FeatureName.wnSim
			};
			featureList = Arrays.asList(eeFeatures);
		}
	}
	
	public EventEventRelationClassifier(String taskName, 
			String classifier) throws Exception {
		super(taskName, classifier);
		initFeatureVector();
	}
	
	public EventEventRelationClassifier(String taskName, 
			String classifier, String feature, 
			String lblGrouping, String probVecFile) throws Exception {
		super(taskName, classifier,
				feature, lblGrouping, probVecFile);
		initFeatureVector();
	}
	
	public EventEventRelationClassifier(String taskName,
			String classifier, String inconsistency) throws Exception {
		super(taskName, classifier,
				inconsistency);
		initFeatureVector();
	}

	public EventEventRelationClassifier(String taskName, 
			String classifier, String feature, 
			String lblGrouping, String probVecFile,
			String inconsistency) throws Exception {
		super(taskName, classifier,
				feature, lblGrouping, probVecFile, inconsistency);
		initFeatureVector();
	}
	
	public void train(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Train model...");

		int nInstances = vectors.size();
		int nFeatures = vectors.get(0).getVectors().size()-1;
		
		if (classifier.equals(VectorClassifier.liblinear)) {
			//Prepare training data
			Feature[][] instances = new Feature[nInstances][nFeatures];
			double[] labels = new double[nInstances];
			
			int row = 0;
			for (PairFeatureVector fv : vectors) {				
				int idx = 1, col = 0;
				for (int i=0; i<nFeatures; i++) {
					labels[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
					instances[row][col] = new FeatureNode(idx, Double.valueOf(fv.getVectors().get(i)));
					idx ++;
					col ++;
				}
				row ++;
			}
			
			//Train
			Problem problem = new Problem();
			problem.l = nInstances;
			problem.n = nFeatures;
			problem.x = instances;
			problem.y = labels;
			problem.bias = 1.0;
			
			SolverType solver = SolverType.L2R_L2LOSS_SVC_DUAL; // -s 1
			double C = 1.0;    // cost of constraints violation
			double eps = 0.01; // stopping criteria

			Parameter parameter = new Parameter(solver, C, eps);
			Model model = Linear.train(problem, parameter);
			File modelFile = new File(modelPath);
			model.save(modelFile);
		}
	}
	
	public void evaluate(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Evaluate model...");

		int nInstances = vectors.size();
		int nFeatures = vectors.get(0).getVectors().size()-1;
		
		if (classifier.equals(VectorClassifier.liblinear)) {
			//Prepare evaluation data
			Feature[][] instances = new Feature[nInstances][nFeatures];
			double[] labels = new double[nInstances];
			
			int row = 0;
			for (PairFeatureVector fv : vectors) {				
				int idx = 1, col = 0;
				for (int i=0; i<nFeatures; i++) {
					labels[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
					instances[row][col] = new FeatureNode(idx, Double.valueOf(fv.getVectors().get(i)));
					idx ++;
					col ++;
				}
				row ++;
			}
			
			//Test
			File modelFile = new File(modelPath);
			Model model = Model.load(modelFile);
			double[] predictions = new double[nInstances];
			int p = 0;
			for (Feature[] instance : instances) {
				predictions[p] = Linear.predict(model, instance);
				p ++;
			}
			
			List<String> result = new ArrayList<String>();
			for (int i=0; i<labels.length; i++) {
				result.add(((int)labels[i]) + "\t" + ((int)predictions[i]));
			}
			
			PairEvaluator pe = new PairEvaluator(result);
			pe.evaluatePerLabelIdx(label);
		}
	}
	
	public List<String> predict(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Test model...");

		int nInstances = vectors.size();
		int nFeatures = vectors.get(0).getVectors().size()-1;
		
		List<String> predictionLabels = new ArrayList<String>();
		
		if (classifier.equals(VectorClassifier.liblinear)) {
			//Prepare test data
			Feature[][] instances = new Feature[nInstances][nFeatures];
			double[] labels = new double[nInstances];
			
			int row = 0;
			for (PairFeatureVector fv : vectors) {				
				int idx = 1, col = 0;
				for (int i=0; i<nFeatures; i++) {
					labels[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
					instances[row][col] = new FeatureNode(idx, Double.valueOf(fv.getVectors().get(i)));
					idx ++;
					col ++;
				}
				row ++;
			}
			
			//Test
			File modelFile = new File(modelPath);
			Model model = Model.load(modelFile);
			for (Feature[] instance : instances) {
				predictionLabels.add(label[(int)Linear.predict(model, instance)-1]);
			}
		}
		
		return predictionLabels;
	}
}
