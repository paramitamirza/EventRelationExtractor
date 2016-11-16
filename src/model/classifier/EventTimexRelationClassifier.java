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
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;
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
	
	protected String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
	protected String[] labelDense = {"BEFORE", "AFTER", "SIMULTANEOUS", 
			"INCLUDES", "IS_INCLUDED", "VAGUE"};
	
	protected void initFeatureVector() {
		
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
					FeatureName.hasModal,
//					FeatureName.modalVerb,
//					FeatureName.depTmxPath,
					FeatureName.tempSignalClusText,		//TimeBank-Dense
					FeatureName.tempSignalPos,			//TimeBank-Dense
					FeatureName.tempSignalDep1Dep2,		//TimeBank-Dense
//					FeatureName.tempSignal1ClusText,	//TempEval3
//					FeatureName.tempSignal1Pos,			//TempEval3
//					FeatureName.tempSignal1Dep			//TempEval3
//					FeatureName.tempSignal2ClusText,
//					FeatureName.tempSignal2Pos,
//					FeatureName.tempSignal2Dep,
//					FeatureName.timexRule
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
		
		System.out.println(nInstances + "-" + vectors.get(0).getVectors().size());
		
		if (classifier.equals(VectorClassifier.liblinear)
				|| classifier.equals(VectorClassifier.logit)
				) {
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
			
			SolverType solver = SolverType.L2R_L2LOSS_SVC_DUAL; // SVM, by default
			
			double C = 1.0;    // cost of constraints violation
			double eps = 0.01; // stopping criteria
			
			if (classifier.equals(VectorClassifier.logit)) {
				solver = SolverType.L2R_LR_DUAL; // Logistic Regression
			}

			Parameter parameter = new Parameter(solver, C, eps);
			Model model = Linear.train(problem, parameter);
			File modelFile = new File(modelPath);
			model.save(modelFile);
		}
	}
	
	public void train2(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Train model...");

		int nInstances = vectors.size();
		int nFeatures = vectors.get(0).getFeatures().length-1;
		
		if (classifier.equals(VectorClassifier.liblinear)
				|| classifier.equals(VectorClassifier.logit)
				) {
			//Prepare training data
			Feature[][] instances = new Feature[nInstances][nFeatures];
			double[] labels = new double[nInstances];
			
			int row = 0;
			for (PairFeatureVector fv : vectors) {				
				int idx = 1, col = 0;
				labels[row] = fv.getFeatures()[nFeatures];	//last column is label
				for (int i=0; i<nFeatures; i++) {
					instances[row][col] = new FeatureNode(idx, fv.getFeatures()[i]);
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
			
			SolverType solver = SolverType.L2R_L2LOSS_SVC_DUAL; // SVM, by default
			
			double C = 1.0;    // cost of constraints violation
			double eps = 0.01; // stopping criteria
			
			if (classifier.equals(VectorClassifier.logit)) {
				solver = SolverType.L2R_LR_DUAL; // Logistic Regression
//				C = Math.pow(2.0, 0.0);
//				eps = Math.pow(2.0, -10.0);
			}

			Parameter parameter = new Parameter(solver, C, eps);
			Model model = Linear.train(problem, parameter);
			File modelFile = new File(modelPath);
			model.save(modelFile);
		}
	}
	
	public svm_model trainSVM(List<PairFeatureVector> vectors) throws Exception {
		
		System.err.println("Train model...");

		int nInstances = vectors.size();
		int nFeatures = vectors.get(0).getVectors().size()-1;
		
		if (classifier.equals(VectorClassifier.libsvm)) {
			//Prepare training data
			svm_problem prob = new svm_problem();
			prob.l = nInstances;
			prob.x = new svm_node[nInstances][nFeatures];
			prob.y = new double[nInstances];
			
			int row = 0;
			for (PairFeatureVector fv : vectors) {				
				int idx = 1, col = 0;
				for (int i=0; i<nFeatures; i++) {
					svm_node node = new svm_node();
					node.index = idx;
					node.value = Double.valueOf(fv.getVectors().get(i));
					prob.x[row][col] = node;
					idx ++;
					col ++;
				}
				prob.y[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
				row ++;
			}
			
			//Train
			svm_parameter param = new svm_parameter();
		    param.probability = 1;
		    param.gamma = 0.125;
//		    param.nu = 0.5;
		    param.C = 8;
		    param.svm_type = svm_parameter.C_SVC;
		    param.kernel_type = svm_parameter.RBF; 
		    param.degree = 2;
		    param.cache_size = 20000;
		    param.eps = 0.001;
		    
		    svm_model model = svm.svm_train(prob, param);
		    return model;
		}
		return null;
	}
	
	public void evaluate(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Evaluate model...");
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getVectors().size()-1;
			
			if (classifier.equals(VectorClassifier.liblinear)
					|| classifier.equals(VectorClassifier.logit)
					) {
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
	}
	
	public List<String> predict(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Test model...");
		
		List<String> predictionLabels = new ArrayList<String>();
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getVectors().size()-1;
			
			if (classifier.equals(VectorClassifier.liblinear)
					|| classifier.equals(VectorClassifier.logit)
					) {
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
		}
		
		return predictionLabels;
	}
	
	public List<String> predict2(List<PairFeatureVector> vectors, String modelPath,
			String[] arrLabel) throws Exception {
		
//		System.err.println("Test model...");

		List<String> predictionLabels = new ArrayList<String>();
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getFeatures().length-1;
			
			if (classifier.equals(VectorClassifier.liblinear)
					|| classifier.equals(VectorClassifier.logit)
					) {
				//Prepare test data
				Feature[][] instances = new Feature[nInstances][nFeatures];
				double[] labels = new double[nInstances];
				
				int row = 0;
				for (PairFeatureVector fv : vectors) {			
					int idx = 1, col = 0;
					labels[row] = fv.getFeatures()[nFeatures];	//last column is label
					for (int i=0; i<nFeatures; i++) {
						instances[row][col] = new FeatureNode(idx, fv.getFeatures()[i]);
						idx ++;
						col ++;
					}
					row ++;
				}
				
				//Test
				File modelFile = new File(modelPath);
				Model model = Model.load(modelFile);
				for (Feature[] instance : instances) {
					predictionLabels.add(arrLabel[(int)Linear.predict(model, instance)-1]);
				}
			}
		}
		
		return predictionLabels;
	}
	
	public List<String> predict(List<PairFeatureVector> vectors, svm_model model) throws Exception {
		
		System.err.println("Test model...");
		
		List<String> predictionLabels = new ArrayList<String>();
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getVectors().size()-1;
			
			if (classifier.equals(VectorClassifier.libsvm)) {
				//Prepare test data
				svm_node[][] instances = new svm_node[nInstances][nFeatures];
				double[] labels = new double[nInstances];
				
				int row = 0;
				for (PairFeatureVector fv : vectors) {				
					int idx = 1, col = 0;
					for (int i=0; i<nFeatures; i++) {
						svm_node node = new svm_node();
						node.index = idx;
						node.value = Double.valueOf(fv.getVectors().get(i));
						instances[row][col] = node;
						idx ++;
						col ++;
					}
					labels[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
					row ++;
				}
				
				//Test
				int totalClasses = 3;
				double[] predictions = new double[nInstances];
				
				for (svm_node[] instance : instances) {
					int[] classes = new int[totalClasses];
			        svm.svm_get_labels(model, classes);
			        double[] probs = new double[totalClasses];
			        predictionLabels.add(label[((int)svm.svm_predict_probability(model, instance, probs))-1]);
				}
			}
		}
		
		return predictionLabels;
	}
	
	public List<String> predictDense(List<PairFeatureVector> vectors, String modelPath) throws Exception {
		
		System.err.println("Test model...");
		
		List<String> predictionLabels = new ArrayList<String>();
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getVectors().size()-1;		
			
			if (classifier.equals(VectorClassifier.liblinear)
					|| classifier.equals(VectorClassifier.logit)) {
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
					predictionLabels.add(labelDense[(int)Linear.predict(model, instance)-1]);
				}
			}
		}
		
		return predictionLabels;
	}
	
	public List<String> predictDense(List<PairFeatureVector> vectors, svm_model model) throws Exception {
		
		System.err.println("Test model...");

		List<String> predictionLabels = new ArrayList<String>();
		
		if (vectors.size() > 0) {

			int nInstances = vectors.size();
			int nFeatures = vectors.get(0).getVectors().size()-1;
			
			if (classifier.equals(VectorClassifier.libsvm)) {
				//Prepare test data
				svm_node[][] instances = new svm_node[nInstances][nFeatures];
				double[] labels = new double[nInstances];
				
				int row = 0;
				for (PairFeatureVector fv : vectors) {				
					int idx = 1, col = 0;
					for (int i=0; i<nFeatures; i++) {
						svm_node node = new svm_node();
						node.index = idx;
						node.value = Double.valueOf(fv.getVectors().get(i));
						instances[row][col] = node;
						idx ++;
						col ++;
					}
					labels[row] = Double.valueOf(fv.getVectors().get(nFeatures));	//last column is label
					row ++;
				}
				
				//Test
				int totalClasses = labelDense.length;
				double[] predictions = new double[nInstances];
				
				for (svm_node[] instance : instances) {
					int[] classes = new int[totalClasses];
			        svm.svm_get_labels(model, classes);
			        double[] probs = new double[totalClasses];
			        predictionLabels.add(labelDense[((int)svm.svm_predict_probability(model, instance, probs))-1]);
				}
			}
		}
		
		return predictionLabels;
	}
}
