package model.classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import evaluator.PairEvaluator;
import model.classifier.PairClassifier.VectorClassifier;
import model.feature.CausalSignalList;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.FeatureEnum.FeatureName;
import parser.TXPParser;
import parser.TimeMLParser;
import parser.entities.EntityEnum;
import server.RemoteServer;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;

public class EventTimexRelationClassifier extends PairClassifier {
	
	private void initFeatureVector() {
		
		super.setPairType(PairType.event_timex);
				
		if (classifier.equals(VectorClassifier.none)) {
			FeatureName[] etFeatures = {
					FeatureName.tokenSpace, FeatureName.lemmaSpace,
					FeatureName.tokenChunk,
					FeatureName.tempMarkerTextSpace
			};
			featureList = Arrays.asList(etFeatures);
			
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			FeatureName[] etFeatures = {
					//Feature.tokenSpace, Feature.lemmaSpace, Feature.tokenChunk,
					FeatureName.token, FeatureName.lemma,
					FeatureName.pos, /*Feature.mainpos,*/
					FeatureName.samePos, /*Feature.sameMainPos,*/
					/*Feature.chunk,*/
					FeatureName.entDistance, FeatureName.sentDistance, FeatureName.entOrder,
					FeatureName.eventClass, FeatureName.tense, FeatureName.aspect, /*Feature.polarity,*/
					FeatureName.dct,
					/*Feature.timexType,*/ 				
					/*Feature.timexValueTemplate,*/
					FeatureName.depTmxPath,				
					FeatureName.mainVerb,
					FeatureName.tempSignalText,
					FeatureName.tempSignalPos,
					/*Feature.tempMarkerClusTextPos,*/
					/*Feature.tempMarkerPos,*/ 
					/*Feature.tempMarkerDep1Dep2,*/
					/*Feature.timexRule*/
			};
			featureList = Arrays.asList(etFeatures);
			
		} else {
			FeatureName[] etFeatures = {
					FeatureName.pos, /*Feature.mainpos,*/
					FeatureName.samePos, /*Feature.sameMainPos,*/
					FeatureName.chunk, 
					FeatureName.entDistance, FeatureName.sentDistance, FeatureName.entOrder,
					FeatureName.eventClass, FeatureName.tense, FeatureName.aspect, FeatureName.polarity,
					FeatureName.dct,
					FeatureName.timexType, 				
					FeatureName.mainVerb, 
//					Feature.depTmxPath,
//					Feature.tempSignalClusText,
//					Feature.tempSignalPos,
					FeatureName.tempSignal1ClusText,
					FeatureName.tempSignal1Pos,
//					Feature.tempSignal2ClusText,
//					Feature.tempSignal2Pos,
					FeatureName.timexRule
			};
			featureList = Arrays.asList(etFeatures);
		}
	}
	
	public EventTimexRelationClassifier(String taskName, 
			String classifier) throws Exception {
		super(taskName, classifier);
		initFeatureVector();
	}
	
	public EventTimexRelationClassifier(String taskName, 
			String classifier, String feature, 
			String lblGrouping, String probVecFile) throws Exception {
		super(taskName, classifier,
				feature, lblGrouping, probVecFile);
		initFeatureVector();
	}
	
	public EventTimexRelationClassifier(String taskName,
			String classifier, String inconsistency) throws Exception {
		super(taskName, classifier,
				inconsistency);
		initFeatureVector();
	}

	public EventTimexRelationClassifier(String taskName, 
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
			pe.evaluatePerLabelIdx();
		}
	}
}
