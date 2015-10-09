package task;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import evaluator.TempEval3;
import javafx.util.Pair;
import model.feature.CausalSignalList;
import model.feature.EventEventFeatureVector;
import model.feature.EventTimexFeatureVector;
import model.feature.PairFeatureVector;
import model.feature.TemporalSignalList;
import model.feature.TimexTimexRelationRule;
import model.feature.FeatureEnum.Feature;
import model.feature.FeatureEnum.PairType;
import parser.TXPParser;
import parser.TXPParser.Field;
import parser.TimeMLParser;
import parser.entities.Doc;
import parser.entities.Entity;
import parser.entities.EntityEnum;
import parser.entities.Event;
import parser.entities.TemporalRelation;
import parser.entities.TimeMLDoc;
import parser.entities.Timex;
import server.RemoteServer;
import libsvm.*;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibLINEAR;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

class CopyOfTempEval3TaskBuild {
	
	private String name;
	private String trainTXPPath;
	private String trainEETMLPath;
	private String trainETTMLPath;
	private String evalTXPPath;
	private String evalTMLPath;
	private String systemTMLPath;
	private TemporalSignalList tsignalList;
	private CausalSignalList csignalList;
	
	private static int numEE = 0;
	private static int numET = 0;
	private static int numTT = 0;
	
	private static int numEEDeduced = 0;
	private static int numETDeduced = 0;
	private static int numTTDeduced = 0;
	
	private static enum VectorClassifier {yamcha, libsvm, weka, liblinear, none};
	private VectorClassifier classifier;
	
	private String dataDirPath;
	
	private Classifier eeCls;
	private Classifier etCls;
	
	private List<String> eeFeatureNames;
	private List<String> etFeatureNames;
	
	private List<Feature> eeFeatureList;
	private List<Feature> etFeatureList;
	
	private boolean includeInconsistent;
	private List<String> inconsistentFiles;
	
	private static enum FeatureType {conventional, wordEmbed, 
		wordEmbedConv, phraseEmbed, phraseEmbedConv, prob};
	private FeatureType featureType;
	
	private void initSignal() throws Exception {
		//temporal & causal signal list files
		tsignalList = new TemporalSignalList(EntityEnum.Language.EN);
		csignalList = new CausalSignalList(EntityEnum.Language.EN);
	}
	
	private void initClassifier() {
		//set the classifier
		classifier = VectorClassifier.liblinear;
		if (classifier.equals(VectorClassifier.weka)) {
			eeCls = new LibSVM();
			etCls = new LibSVM();
//					eeCls = new RandomForest();
//					etCls = new RandomForest();
		}
		
		if (classifier.equals(VectorClassifier.none)) {
			Feature[] eeFeatures = {
					Feature.tokenSpace, Feature.lemmaSpace,
					Feature.tokenChunk,
					Feature.tempMarkerTextSpace, Feature.causMarkerTextSpace
			};
			Feature[] etFeatures = {
					Feature.tokenSpace, Feature.lemmaSpace,
					Feature.tokenChunk,
					Feature.tempMarkerTextSpace
			};
			eeFeatureList = Arrays.asList(eeFeatures);
			etFeatureList = Arrays.asList(etFeatures);
			
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			Feature[] eeFeatures = {
					Feature.token, Feature.lemma,
					Feature.pos, /*Feature.mainpos,*/
					/*Feature.samePos,*/ /*Feature.sameMainPos,*/
					/*Feature.chunk,*/
					Feature.entDistance, Feature.sentDistance,
					Feature.eventClass, Feature.tense, Feature.aspect, /*Feature.polarity,*/
					/*Feature.sameEventClass,*/ /*Feature.sameTense,*/ /*Feature.sameAspect,*/ /*Feature.samePolarity,*/
					Feature.depPath,				
					Feature.mainVerb,
					Feature.tempMarkerClusTextPos,		
					/*Feature.tempMarkerPos,*/ 
					/*Feature.tempMarkerDep1Dep2,*/
					Feature.causMarkerClusTextPos,
					/*Feature.causMarkerPos,*/ 
					/*Feature.causMarkerDep1Dep2,*/
					/*Feature.coref,*/
					Feature.wnSim
			};
			Feature[] etFeatures = {
					//Feature.tokenSpace, Feature.lemmaSpace, Feature.tokenChunk,
					Feature.token, Feature.lemma,
					Feature.pos, /*Feature.mainpos,*/
					/*Feature.chunk, *//*Feature.samePos,*/ /*Feature.sameMainPos,*/
					Feature.entDistance, Feature.sentDistance, Feature.entOrder,
					Feature.eventClass, Feature.tense, Feature.aspect, /*Feature.polarity,*/
					Feature.dct,
					/*Feature.timexType,*/ 				
					/*Feature.timexValueTemplate,*/
					Feature.depPath,				
					/*Feature.mainVerb,*/ 
					Feature.tempMarkerClusTextPos,
					/*Feature.tempMarkerPos,*/ 
					/*Feature.tempMarkerDep1Dep2,*/
					/*Feature.timexRule*/
			};
			eeFeatureList = Arrays.asList(eeFeatures);
			etFeatureList = Arrays.asList(etFeatures);
			
		} else {
			Feature[] eeFeatures = {
					Feature.pos, /*Feature.mainpos,*/
					Feature.samePos, /*Feature.sameMainPos,*/
					Feature.chunk,
					Feature.entDistance, Feature.sentDistance,
					Feature.eventClass, Feature.tense, Feature.aspect, Feature.polarity,
					Feature.sameEventClass, Feature.sameTense, Feature.sameAspect, Feature.samePolarity,
					/*Feature.depPath,*/				
					Feature.mainVerb,
					Feature.tempSignalClusText, 
					Feature.tempSignalPos,
					/*Feature.causMarkerClusText,*/
					/*Feature.causMarkerPos,*/
					/*Feature.coref,*/
					Feature.wnSim
			};
			Feature[] etFeatures = {
					Feature.pos, Feature.mainpos,
					Feature.chunk, //Feature.samePos, Feature.sameMainPos,
					Feature.entDistance, Feature.sentDistance, Feature.entOrder,
					Feature.eventClass, Feature.tense, Feature.aspect, Feature.polarity,
					Feature.dct,
					Feature.timexType, 				
					Feature.mainVerb, 
					Feature.tempSignalClusText, 
					Feature.tempSignalPos,
					Feature.timexRule
			};
			eeFeatureList = Arrays.asList(eeFeatures);
			etFeatureList = Arrays.asList(etFeatures);
		}
	}
	
	public CopyOfTempEval3TaskBuild() throws Exception {
		name = "te3";
		trainTXPPath = "data/TempEval3-train_TXP2";
		trainEETMLPath = "data/TempEval3-train_TML_deduced";
		trainETTMLPath = "data/TempEval3-train_TML";
		evalTXPPath = "data/TempEval3-eval_TXP";
		evalTMLPath = "data/TempEval3-eval_TML";
		
		//TimeML directory for system result files
		systemTMLPath = "data/TempEval3-system_TML";
		File sysDir = new File(systemTMLPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		eeFeatureNames = new ArrayList<String>();
		etFeatureNames = new ArrayList<String>();
		
		initSignal();		
		
		featureType = FeatureType.conventional;
		initClassifier();
		ensureDataDirectory();
		
		includeInconsistent = true;
		if (includeInconsistent) {
			inconsistentFiles = new ArrayList<String>();
		} else {
			initInconsistentFiles();
		}
	}
	
	public CopyOfTempEval3TaskBuild(String trainTXP, String eeTrainTML, String etTrainTML, 
			String evalTXP, String evalTML,
			String feature, String inconsistency) throws Exception {
		name = "te3";
		trainTXPPath = trainTXP;
		trainEETMLPath = eeTrainTML;
		trainETTMLPath = etTrainTML;
		evalTXPPath = evalTXP;
		evalTMLPath = evalTML;
		
		//TimeML directory for system result files
		systemTMLPath = "data/TempEval3-system_TML";
		File sysDir = new File(systemTMLPath);
		// if the directory does not exist, create it
		if (!sysDir.exists()) {
			sysDir.mkdir();
		}
		
		eeFeatureNames = new ArrayList<String>();
		etFeatureNames = new ArrayList<String>();
		
		initSignal();		
		
		switch (feature) {
			case "conv": featureType = FeatureType.conventional; break;
			case "wembed" : featureType = FeatureType.wordEmbed; break;
			case "wembedconv" : featureType = FeatureType.wordEmbedConv; break;
			case "pembed" : featureType = FeatureType.phraseEmbed; break;
			case "pembedconv" : featureType = FeatureType.phraseEmbedConv; break;
		}		
		initClassifier();
		ensureDataDirectory();
		
		switch (inconsistency) {
			case "include": includeInconsistent = true; break;
			case "exclude": includeInconsistent = false; break;
		}		
		if (includeInconsistent) {
			inconsistentFiles = new ArrayList<String>();
		} else {
			initInconsistentFiles();
		}
	}
	
	private void ensureDataDirectory() {
		if (classifier.equals(VectorClassifier.none)) {
			dataDirPath = "data/tokens/";
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			dataDirPath = "data/yamcha/";
		} else if (classifier.equals(VectorClassifier.libsvm) ||
				classifier.equals(VectorClassifier.liblinear)) {
			dataDirPath = "data/libsvm/";
		} else if (classifier.equals(VectorClassifier.weka)) {
			dataDirPath = "data/weka/";
		}
		File dir = new File(dataDirPath);
		if (!dir.exists()) dir.mkdir();
	}
	
	public static void main(String [] args) {
		Field[] fields = {Field.token, Field.token_id, Field.sent_id, Field.pos, 
				Field.lemma, Field.deps, Field.tmx_id, Field.tmx_type, Field.tmx_value, 
				Field.ner, Field.ev_class, Field.ev_id, Field.role1, Field.role2, 
				Field.role3, Field.is_arg_pred, Field.has_semrole, Field.chunk, 
				Field.main_verb, Field.connective, Field.morpho, 
				Field.tense_aspect_pol, /*Field.coref_event,*/ Field.tlink};
		TXPParser txpParser = new TXPParser(EntityEnum.Language.EN, fields);		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		//dir_TXP <-- data/example_TXP
		try {
			CopyOfTempEval3TaskBuild task = new CopyOfTempEval3TaskBuild(args[0], args[1], args[2],
					args[3], args[4], args[5], args[6]);
			
			//task.printDataset(txpParser, tmlParser, false, false);
			
			task.train(txpParser, tmlParser, false, false);
			numEE = 0; numET = 0; numTT = 0;
			task.evaluate(txpParser, tmlParser, false, false);
			task.evaluateTE3(txpParser, tmlParser, false, false);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public void printDataset(TXPParser txpParser, TimeMLParser tmlParser, 
			boolean eecoref, boolean etrule) throws Exception{
		RemoteServer rs = new RemoteServer();
		
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		String eeFilepath, etFilepath;
		
		eeFilepath = dataDirPath + name + "-ee-train-tokens-deduced.data";
		if (eecoref) {
			getEventEventFeatureVector(txpParser, tmlParser, trainTXPPath, trainEETMLPath, ee, eeCoref);
		} else {
			getEventEventFeatureVector(txpParser, tmlParser, trainTXPPath, trainEETMLPath, ee, null);
		}
		writeEventEventDataset(rs, ee, eeFilepath);		//conventional features
		
		etFilepath = dataDirPath + name + "-et-train-tokens-deduced.data";
		if (etrule) {
			getEventTimexFeatureVector(txpParser, tmlParser, trainTXPPath, trainETTMLPath, et, etRule);
		} else {
			getEventTimexFeatureVector(txpParser, tmlParser, trainTXPPath, trainETTMLPath, et, null);
		}
		writeEventTimexDataset(rs, et, etFilepath);		//conventional features
		
		ee = new StringBuilder();
		et = new StringBuilder();
		eeCoref = new StringBuilder();
		etRule = new StringBuilder();
		
		eeFilepath = dataDirPath + name + "-ee-eval-conv.data";
		if (eecoref) {
			getEventEventFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, ee, eeCoref);
		} else {
			getEventEventFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, ee, null);
		}
		writeEventEventDataset(rs, ee, eeFilepath);		//conventional features
		
		etFilepath = dataDirPath + name + "-et-eval-conv.data";
		if (etrule) {
			getEventTimexFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, et, etRule);
		} else {
			getEventTimexFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, et, null);
		}
		writeEventTimexDataset(rs, et, etFilepath);		//conventional features
		
		rs.disconnect();
	}
	
	public void train(TXPParser txpParser, TimeMLParser tmlParser, 
			boolean eecoref, boolean etrule) throws Exception {
		RemoteServer rs = new RemoteServer();
		
		System.out.println("Building training data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		
		String eeFilepath, etFilepath;
		
		//Write training data - event-event
		eeFilepath = dataDirPath + name + "-ee-train-conv.data";
		switch (featureType) {
			case conventional:
				eeFilepath = dataDirPath + name + "-ee-train-conv.data";
				if (eecoref) {
					getEventEventFeatureVector(txpParser, tmlParser, trainTXPPath, trainEETMLPath, ee, eeCoref);
				} else {
					getEventEventFeatureVector(txpParser, tmlParser, trainTXPPath, trainEETMLPath, ee, null);
				}
				writeEventEventDataset(rs, ee, eeFilepath);		//conventional features
				break;
			
			case wordEmbed:
				eeFilepath = dataDirPath + name + "-ee-train-word-embed.data";
				writeEventEventEmbedding(rs, 					//word embedding
					"data/embedding/te3-ee-token-lemma-train-embedding.csv", eeFilepath, 600);
				break;
				
			case wordEmbedConv:
				eeFilepath = dataDirPath + name + "-ee-train-word-embed-conv.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//word embedding + conventional features
						txpParser, tmlParser, 
						trainTXPPath, trainEETMLPath,
						"data/embedding/te3-ee-token-lemma-train-embedding-no-label.csv", eeFilepath, 600);
				break;
				
			case phraseEmbed:
				eeFilepath = dataDirPath + name + "-ee-train-phrase-embed.data";
				writeEventEventEmbedding(rs, 					//chunk embedding
						"data/embedding/te3-ee-token-in-chunk-train-embedding.csv", eeFilepath, 9600);
				break;
				
			case phraseEmbedConv:
				eeFilepath = dataDirPath + name + "-ee-train-phrase-embed-conv.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//chunk embedding + conventional features
						txpParser, tmlParser, 
						trainTXPPath, trainEETMLPath,
						"data/embedding/te3-ee-token-in-chunk-train-embedding-no-label.csv", eeFilepath, 9600);
				break;
				
			case prob:
				eeFilepath = dataDirPath + name + "-ee-train-word-embed-prob.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//chunk embedding + conventional features
						txpParser, tmlParser, 
						trainTXPPath, trainEETMLPath,
						"data/probs/prob1.csv", eeFilepath, 7);
				break;
		}		
		
		//Write training data - event-timex
		etFilepath = dataDirPath + name + "-et-train-conv.data";
		if (etrule) {
			getEventTimexFeatureVector(txpParser, tmlParser, trainTXPPath, trainETTMLPath, et, etRule);
		} else {
			getEventTimexFeatureVector(txpParser, tmlParser, trainTXPPath, trainETTMLPath, et, null);
		}
		writeEventTimexDataset(rs, et, etFilepath);		//conventional features
		
		//Field/column titles of features
		System.out.println("event-event features: " + String.join(",", eeFeatureNames));
		System.out.println("event-timex features: " + String.join(",", etFeatureNames));
		
		System.out.println("num event-event TLINKs: " + numEE);		
		System.out.println("num event-timex TLINKs: " + numET);
		System.out.println("num timex-timex TLINKs: " + numTT);
		
		System.out.println("num deduced event-event TLINKs: " + numEEDeduced);		
		System.out.println("num deduced event-timex TLINKs: " + numETDeduced);
		System.out.println("num deduced timex-timex TLINKs: " + numTTDeduced);
		
		System.out.println("Train models...");
		trainModels(rs, eeFilepath, etFilepath);
		
		rs.disconnect();
	}
	
	public void evaluate(TXPParser txpParser, TimeMLParser tmlParser, boolean eecoref, boolean etrule) 
			throws Exception {
		RemoteServer rs = new RemoteServer();
		
		System.out.println("Building testing data...");
		StringBuilder ee = new StringBuilder();
		StringBuilder et = new StringBuilder();
		StringBuilder eeCoref = new StringBuilder();
		StringBuilder etRule = new StringBuilder();
		
		String eeFilepath, etFilepath;
		
		//Write evaluation data - event-event
		eeFilepath = dataDirPath + name + "-ee-eval-conv.data";
		switch (featureType) {
			case conventional:
				eeFilepath = dataDirPath + name + "-ee-eval-conv.data";
				if (eecoref) {
					getEventEventFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, ee, eeCoref);
				} else {
					getEventEventFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, ee, null);
				}
				writeEventEventDataset(rs, ee, eeFilepath);		//conventional features
				break;
			
			case wordEmbed:
				eeFilepath = dataDirPath + name + "-ee-eval-word-embed.data";
				writeEventEventEmbedding(rs, 					//word embedding
						"data/embedding/te3-ee-token-lemma-eval-embedding.csv", eeFilepath, 600);
				break;
				
			case wordEmbedConv:
				eeFilepath = dataDirPath + name + "-ee-eval-word-embed-conv.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//word embedding + conventional features
						txpParser, tmlParser, 
						evalTXPPath, evalTMLPath,
						"data/embedding/te3-ee-token-lemma-eval-embedding-no-label.csv", eeFilepath, 600);
				break;
				
			case phraseEmbed:
				eeFilepath = dataDirPath + name + "-ee-eval-phrase-embed.data";
				writeEventEventEmbedding(rs, 					//chunk embedding
						"data/embedding/te3-ee-token-in-chunk-eval-embedding.csv", eeFilepath, 9600);
				break;
				
			case phraseEmbedConv:
				eeFilepath = dataDirPath + name + "-ee-eval-phrase-embed-conv.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//chunk embedding + conventional features
						txpParser, tmlParser, 
						evalTXPPath, evalTMLPath,
						"data/embedding/te3-ee-token-in-chunk-eval-embedding-no-label.csv", eeFilepath, 9600);
				break;
				
			case prob:
				eeFilepath = dataDirPath + name + "-ee-eval-word-embed-prob.data";
				combineEventEventFeatureVectorEmbedding(rs, 	//chunk embedding + conventional features
						txpParser, tmlParser, 
						evalTXPPath, evalTMLPath,
						"data/probs/prob1.csv", eeFilepath, 7);
				break;
		}
		
		//Write evaluation data - event-timex
		etFilepath = dataDirPath + name + "-et-eval-conv.data";
		if (etrule) {
			getEventTimexFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, et, etRule);
		} else {
			getEventTimexFeatureVector(txpParser, tmlParser, evalTXPPath, evalTMLPath, et, null);
		}
		writeEventTimexDataset(rs, et, etFilepath);
		
		System.out.println("num event-event TLINKs: " + numEE);		
		System.out.println("num event-timex TLINKs: " + numET);
		System.out.println("num timex-timex TLINKs: " + numTT);
		
		System.out.println("Test models...");

		String eeTrainFilepath = dataDirPath + name + "-ee-train.data";
		String etTrainFilepath = dataDirPath + name + "-et-train.data";
		
//		String eeTrainFilepath = dataDirPath + name + "-ee-train-conv.data";
//		String etTrainFilepath = dataDirPath + name + "-et-train-conv.data";

		evaluateModels(rs, txpParser, tmlParser, eeTrainFilepath, etTrainFilepath,
				eeFilepath, etFilepath);
		
		rs.disconnect();
	}	
	
	public void evaluateTE3(TXPParser txpParser, TimeMLParser tmlParser, 
			boolean eecoref, boolean etrule) throws Exception {
		File[] txpFiles = new File(evalTXPPath).listFiles();
		
		RemoteServer rs = new RemoteServer();
		
		//(Delete if exist and) create gold/ and system/ directories in remote server
		rs.executeCommand("rm -rf ~/data/gold/ && mkdir ~/data/gold/");
		rs.executeCommand("rm -rf ~/data/system/ && mkdir ~/data/system/");
		
		String eeFilepath = dataDirPath + name + "-ee-eval.data";
		String etFilepath = dataDirPath + name + "-et-eval.data";
		
		//For each file in the evaluation dataset
		for (File txpFile : txpFiles) {
			if (txpFile.isFile()) {	
				//System.out.println("Test " + txpFile.getName() + "...");
				File tmlFile = new File(evalTMLPath, txpFile.getName().replace(".txp", ""));
				
				StringBuilder ee = new StringBuilder();
				StringBuilder et = new StringBuilder();
				StringBuilder eeCoref = new StringBuilder();
				StringBuilder etRule = new StringBuilder();
				getFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile, 
						ee, et, eeCoref, etRule, eecoref, etrule);
				
				//Write evaluation data				
				writeEventEventDataset(rs, ee, eeFilepath);
				writeEventTimexDataset(rs, et, etFilepath);
				
				List<String> eeResult = new ArrayList<String>();
				List<String> etResult = new ArrayList<String>();
				List<String> ttResult = getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile);
				
				String[] eeCorefArr = eeCoref.toString().split("\\r?\\n");
				eeResult.addAll(Arrays.asList(eeCorefArr));
				
				String[] etRuleArr = etRule.toString().split("\\r?\\n");
				etResult.addAll(Arrays.asList(etRuleArr));
				
				eeResult.addAll(predictEventEventPerFile(rs, eeFilepath, txpParser, tmlParser, 
						txpFile, tmlFile, eecoref, etrule));
				etResult.addAll(predictEventTimexPerFile(rs, etFilepath, txpParser, tmlParser, 
						txpFile, tmlFile, eecoref, etrule));
				
				//Write the TimeML document with new TLINKs
				writeTimeMLFile(tmlParser, tmlFile, eeResult, etResult, ttResult);
			}
		}
		rs.disconnect();
		
		TempEval3 te3 = new TempEval3(evalTMLPath, systemTMLPath);
		te3.evaluate();		
	}
	
	private void trainModels(RemoteServer rs, String eeFilepath, String etFilepath) throws Exception {
		if (classifier.equals(VectorClassifier.yamcha)) {	//Train models using Yamcha
			String cmdCd = "cd tools/yamcha-0.33/";
			String cmdTrainEE = "make CORPUS=~/" + eeFilepath + " "
//					+ "MULTI_CLASS=2 "
					+ "MODEL=~/models/" + name + "-ee "
					+ "FEATURE=\"F:0:2..\" "
					+ "SVM_PARAM=\"-t1 -d4 -c1 -m 512\" train";
			String cmdTrainET = "make CORPUS=~/" + etFilepath + " "
//					+ "MULTI_CLASS=2 "
					+ "MODEL=~/models/" + name + "-et "
					+ "FEATURE=\"F:0:2..\" "
					+ "SVM_PARAM=\"-t1 -d4 -c1 -m 512\" train";
			rs.executeCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
			
		} else if (classifier.equals(VectorClassifier.libsvm)) {	//Train models using LibSVM
			String cmdCd = "cd tools/libsvm-3.20/";
			String cmdTrainEE = "./svm-train "
					+ "-s 0 -t 2 -d 3 -g 0.0 -r 0.0 -c 1 -n 0.5 -p 0.1 -m 128 -e 0.001 "
					+ "~/" + eeFilepath + ".libsvm "
					+ "~/models/" + name + "-ee-libsvm.model";
			String cmdTrainET = "./svm-train "
					+ "-s 0 -t 2 -d 3 -g 0.0 -r 0.0 -c 1 -n 0.5 -p 0.1 -m 128 -e 0.001 "
					+ "~/" + etFilepath + ".libsvm "
					+ "~/models/" + name + "-et-libsvm.model";
			
			rs.executeCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
			
		} else if (classifier.equals(VectorClassifier.liblinear)) {	//Train models using LibLINEAR
			String cmdCd = "cd tools/liblinear-2.01/";
			String cmdTrainEE = "./train "
					+ "-s 1 -c 1.0 -e 0.01 -B 1.0 "
					+ "~/" + eeFilepath + ".libsvm "
					+ "~/models/" + name + "-ee-liblinear.model";
			String cmdTrainET = "./train "
					+ "-s 1 -c 1.0 -e 0.01 -B 1.0 "
					+ "~/" + etFilepath + ".libsvm "
					+ "~/models/" + name + "-et-liblinear.model";
			
			System.out.println(cmdCd + " && " + cmdTrainEE);
			rs.executeCommand(cmdCd + " && " + cmdTrainEE + " && " + cmdTrainET);
			
		}else if (classifier.equals(VectorClassifier.weka)) {	//Train models using Weka
			Instances eeTrain = new DataSource(eeFilepath + ".arff").getDataSet();
			eeTrain.setClassIndex(eeTrain.numAttributes() - 1); 
			eeCls.buildClassifier(eeTrain);
		    
		    Instances etTrain = new DataSource(etFilepath + ".arff").getDataSet();
			etTrain.setClassIndex(etTrain.numAttributes() - 1);
			etCls.buildClassifier(etTrain);
		}
	}
	
	private void evaluateModels(RemoteServer rs, TXPParser txpParser, TimeMLParser tmlParser,
			String eeTrainFilepath, String etTrainFilepath,
			String eeTestFilepath, String etTestFilepath) throws Exception {
		String eeAccuracy = "", etAccuracy = "";
		if (classifier.equals(VectorClassifier.yamcha)) {
			String cmdCd = "cd tools/yamcha-0.33/";			
			String cmdTestEE = "./usr/local/bin/yamcha -m ~/models/"+name+"-ee.model"
					+ " < ~/" + eeTestFilepath + " "
					+ " | cut -f1,2," + (eeFeatureNames.size()) + "," + (eeFeatureNames.size()+1);
					//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
			String cmdTestET = "./usr/local/bin/yamcha -m ~/models/"+name+"-et.model"
					+ " < ~/" + etTestFilepath + " "
					+ " | cut -f1,2," + (etFeatureNames.size()) + "," + (etFeatureNames.size()+1);
					//+ " > ~/data/"+name+"-et-eval-tagged.tlinks";
			
			List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
			List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
			eeAccuracy = String.format( "%.2f", accuracy(eeResult)*100);
			etAccuracy = String.format( "%.2f", accuracy(etResult)*100);
			
		} else if (classifier.equals(VectorClassifier.libsvm)) {
			String cmdCd = "cd tools/libsvm-3.20/";		
			String cmdTestEE = "./svm-predict "
					+ "~/" + eeTestFilepath + ".libsvm "
					+ "~/models/" + name + "-ee-libsvm.model "
					+ "~/data/" + name + "-ee-eval.tagged";
			String cmdTestET = "./svm-predict "
					+ "~/" + etTestFilepath + ".libsvm "
					+ "~/models/" + name + "-et-libsvm.model "
					+ "~/data/" + name + "-et-eval.tagged";
			
			List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
			List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
			eeAccuracy = eeResult.get(0);
			etAccuracy = etResult.get(0);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
			
		} else if (classifier.equals(VectorClassifier.liblinear)) {
			String cmdCd = "cd tools/liblinear-2.01/";		
			String cmdTestEE = "./predict "
					+ "~/" + eeTestFilepath + ".libsvm "
					+ "~/models/" + name + "-ee-liblinear.model "
					+ "~/data/" + name + "-ee-eval.tagged";
			String cmdTestET = "./predict "
					+ "~/" + etTestFilepath + ".libsvm "
					+ "~/models/" + name + "-et-liblinear.model "
					+ "~/data/" + name + "-et-eval.tagged";
			
			System.out.println(cmdCd + " && " + cmdTestEE);
			List<String> eeResult = rs.executeCommand(cmdCd + " && " + cmdTestEE);
			List<String> etResult = rs.executeCommand(cmdCd + " && " + cmdTestET);
			eeAccuracy = eeResult.get(0);
			etAccuracy = etResult.get(0);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			Instances eeTrain = new DataSource(eeTrainFilepath + ".arff").getDataSet();
			eeTrain.setClassIndex(eeTrain.numAttributes() - 1);
			Instances eeTest = new DataSource(eeTestFilepath + ".arff").getDataSet();
			eeTest.setClassIndex(eeTest.numAttributes() - 1);
			Evaluation eeEval = new Evaluation(eeTrain);
		    eeEval.evaluateModel(eeCls, eeTest);
		    System.out.println(eeEval.toSummaryString("\nEvent-event Results\n======\n", false));
		    
		    Instances etTrain = new DataSource(etTrainFilepath + ".arff").getDataSet();
			etTrain.setClassIndex(etTrain.numAttributes() - 1);
			Instances etTest = new DataSource(etTestFilepath + ".arff").getDataSet();
			etTest.setClassIndex(etTest.numAttributes() - 1);
			Evaluation etEval = new Evaluation(etTrain);
		    etEval.evaluateModel(etCls, etTest);
		    System.out.println(etEval.toSummaryString("\nEvent-timex Results\n======\n", false));
		}
		
		List<String> ttResult = getTimexTimexTlinks(txpParser, tmlParser, evalTXPPath, evalTMLPath);

		System.out.println("Accuracy event-event: " + eeAccuracy);
		System.out.println("Accuracy event-timex: " + etAccuracy);
		System.out.println("Accuracy timex-timex: " + String.format( "%.2f", accuracy(ttResult)*100) + "%"
				+ " (" + numCorrect(ttResult) + "/" + numInstance(ttResult) + ")");		
	}
	
	public Map<Pair<String,String>,String> getTimexTimexRuleRelation(Doc doc) {
		Object[] entArr = doc.getEntities().keySet().toArray();
		Map<Pair<String,String>,String> ttlinks = new HashMap<Pair<String,String>,String>();
		Pair<String,String> pair = null;
		for (int i = 0; i < entArr.length; i++) {
			for (int j = i; j < entArr.length; j++) {
				if (!entArr[i].equals(entArr[j]) && doc.getEntities().get(entArr[i]) instanceof Timex && 
						doc.getEntities().get(entArr[j]) instanceof Timex) {
					TimexTimexRelationRule timextimex = new TimexTimexRelationRule(((Timex)doc.getEntities().get(entArr[i])), 
							((Timex)doc.getEntities().get(entArr[j])), doc.getDct());
					if (!timextimex.getRelType().equals("O")) {
						pair = new Pair<String,String>(((String) entArr[i]), ((String) entArr[j]));
						ttlinks.put(pair, timextimex.getRelType());
					}
				}
			}
		}
		return ttlinks;
	}
	
	public List<String> getTimexTimexTlinksPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		List<String> tt = new ArrayList<String>();
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		//Determine the relation type of every timex-timex pair in the document via rules 
		Map<Pair<String,String>,String> ttlinks = getTimexTimexRuleRelation(docTxp);
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.timex_timex)) {
					if (tlink.isDeduced()) numTTDeduced ++;
					Pair<String,String> st = new Pair<String, String>(tlink.getSourceID(), tlink.getTargetID());
					Pair<String,String> ts = new Pair<String, String>(tlink.getTargetID(), tlink.getSourceID());
					if (ttlinks.containsKey(st)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + ttlinks.get(st));
					} else if (ttlinks.containsKey(ts)) {
						tt.add(tlink.getSourceID() + "\t" + tlink.getTargetID() + "\t" + 
								tlink.getRelType() + "\t" + TemporalRelation.getInverseRelation(ttlinks.get(ts)));
					}
				}
			}
		}
		return tt;
	}
	
	public List<String> getTimexTimexTlinks(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath) throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();
		List<String> tt = new ArrayList<String>();
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			tt.addAll(getTimexTimexTlinksPerFile(txpParser, tmlParser, txpFile, tmlFile));
		}		
		return tt;
	}
	
	private void getPairIDPerFile (TXPParser txpParser, TimeMLParser tmlParser,
			File txpFile, File tmlFile, 
			StringBuilder eePair, StringBuilder etPair,
			StringBuilder eeCorefPair, StringBuilder etRulePair,
			boolean eecoref, boolean etrule) throws Exception {
		if (eecoref) {
			getEventEventPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, eePair, eeCorefPair);
		} else {
			getEventEventPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, eePair, null);
		}
		if (etrule) {
			getEventTimexPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, etPair, etRulePair);
		} else {
			getEventTimexPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, etPair, null);
		}
	}
	
	public void getEventEventPairIDPerFile (TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, StringBuilder ee, StringBuilder eeCoref) 
					throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}				
				
				if (fv instanceof EventEventFeatureVector) {
					if (tlink.isDeduced()) numEEDeduced += 1;
					if (eeCoref != null) {
						if (((EventEventFeatureVector) fv).isCoreference()) {
							//skip event-event pairs with COREF for training, assign IDENTITY/SIMULTANEOUS directly
							eeCoref.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\t" + "IDENTITY" + "\n");
						} else {
							ee.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\n");
						}
					} else {
						ee.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								tlink.getRelType() + "\n");
					}
				}
			}
		}
	}
	
	public void getEventTimexPairIDPerFile (TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile, StringBuilder et, StringBuilder etRule) 
					throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				
				if (fv.getPairType().equals(PairType.event_event)) {
					fv = new EventEventFeatureVector(fv);
				} else if (fv.getPairType().equals(PairType.event_timex)) {
					fv = new EventTimexFeatureVector(fv);
				}				
				
				if (fv instanceof EventTimexFeatureVector) {
					if (tlink.isDeduced()) numETDeduced += 1;
					if (etRule != null) {
						String timexRule = ((EventTimexFeatureVector) fv).getTimexRule();
						if (!timexRule.equals("O")) {
							//skip event-timex pairs identified using rules for training, assign the BEGUN_BY or ENDED_BY directly
							if (timexRule.equals("TMX-BEGIN")) {
								etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
										tlink.getRelType() + "\t" + "BEGUN_BY" + "\n");
							} else if (timexRule.equals("TMX-END")) {
								etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
										tlink.getRelType() + "\t" + "ENDED_BY" + "\n");
							}
						} else {
							et.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
									tlink.getRelType() + "\n");
						}
					} else {
						et.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								tlink.getRelType() + "\n");
					}
				}
			}
		}
	}
	
	private void writeEventEventDataset(RemoteServer rs, StringBuilder ee, String eeFilepath) 
			throws Exception {
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.none)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".csv", "UTF-8");		
			eePW.write(ee.toString());
			eePW.close();
			
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			PrintWriter eePW = new PrintWriter(eeFilepath, "UTF-8");		
			eePW.write(ee.toString());
			eePW.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File eeFile = new File(eeFilepath);
			rs.copyFile(eeFile, "data/yamcha/");
			
			//Delete file in local directory
			eeFile.delete();
			
		} else if (classifier.equals(VectorClassifier.libsvm) || classifier.equals(VectorClassifier.liblinear)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".libsvm", "UTF-8");		
			eePW.write(ee.toString());
			eePW.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File eeFile = new File(eeFilepath + ".libsvm");
			rs.copyFile(eeFile, "data/libsvm/");
			
			//Delete file in local directory
			eeFile.delete();
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".arff", "UTF-8");	
			writeArffFile(eePW, ee, eeFeatureNames);
		}
	}
	
	private void writeEventTimexDataset(RemoteServer rs, StringBuilder et, String etFilepath) 
			throws Exception {
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.none)) {
			PrintWriter etPW = new PrintWriter(etFilepath + ".csv", "UTF-8");		
			etPW.write(et.toString());
			etPW.close();
			
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			PrintWriter etPW = new PrintWriter(etFilepath, "UTF-8");		
			etPW.write(et.toString());
			etPW.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File etFile = new File(etFilepath);
			rs.copyFile(etFile, "data/yamcha/");
			
			//Delete file in local directory
			etFile.delete();
			
		} else if (classifier.equals(VectorClassifier.libsvm) || classifier.equals(VectorClassifier.liblinear)) {
			PrintWriter etPW = new PrintWriter(etFilepath + ".libsvm", "UTF-8");		
			etPW.write(et.toString());
			etPW.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File etFile = new File(etFilepath + ".libsvm");
			rs.copyFile(etFile, "data/libsvm/");
			
			//Delete file in local directory
			etFile.delete();
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter etPW = new PrintWriter(etFilepath + ".arff", "UTF-8");	
			writeArffFile(etPW, et, etFeatureNames);
		}
	}
	
	public void printFeatureVector(StringBuilder pair, PairFeatureVector fv) {
		if (classifier.equals(VectorClassifier.libsvm) || classifier.equals(VectorClassifier.liblinear)) {
			pair.append(fv.printLibSVMVectors() + "\n");
		} else if (classifier.equals(VectorClassifier.weka)) {
			pair.append(fv.printCSVVectors() + "\n");
		} else if (classifier.equals(VectorClassifier.yamcha)) {
			pair.append(fv.printVectors() + "\n");
		} else if (classifier.equals(VectorClassifier.none)) {
			pair.append(fv.printCSVVectors() + "\n");
		}
	}
	
	public void printEventEventFeatureVector(List<PairFeatureVector> vectors, StringBuilder ee, StringBuilder eeCoref) {
		for (PairFeatureVector fv : vectors) {
			if (eeCoref != null) {
				if (((EventEventFeatureVector) fv).isCoreference()) {
					//skip event-event pairs with COREF for training, assign IDENTITY/SIMULTANEOUS directly
					eeCoref.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
							fv.getLabel() + "\t" + "IDENTITY" + "\n");
				} else {
					printFeatureVector(ee, fv);
				}
			} else {
				printFeatureVector(ee, fv);
			}
		}
		if (classifier.equals(VectorClassifier.yamcha)) ee.append("\n");
	}
	
	public void printEventTimexFeatureVector(List<PairFeatureVector> vectors, StringBuilder et, StringBuilder etRule) {
		for (PairFeatureVector fv : vectors) {
			if (etRule != null) {
				String timexRule = ((EventTimexFeatureVector) fv).getTimexRule();
				if (!timexRule.equals("O")) {
					//skip event-timex pairs identified using rules for training, assign the BEGUN_BY or ENDED_BY directly
					if (timexRule.equals("TMX-BEGIN")) {
						etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								fv.getLabel() + "\t" + "BEGUN_BY" + "\n");
					} else if (timexRule.equals("TMX-END")) {
						etRule.append(fv.getE1().getID() + "\t" + fv.getE2().getID() + "\t" + 
								fv.getLabel() + "\t" + "ENDED_BY" + "\n");
					}
				} else {
					printFeatureVector(et, fv);
				}
			} else {
				printFeatureVector(et, fv);
			}
		}
		if (classifier.equals(VectorClassifier.yamcha)) et.append("\n");
	}
	
	private void getEventEventFeatureVector(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath,
			StringBuilder ee, StringBuilder eeCoref) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			if (isConsistent(txpFile.getName())) {
				File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
				List<PairFeatureVector> vectors = getEventEventFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
				//Field/column titles of features
				eeFeatureNames.clear();
				for (String s : EventEventFeatureVector.fields) {
					if (s!= null) eeFeatureNames.add(s);
				}
				printEventEventFeatureVector(vectors, ee, eeCoref);
			}
		}	
	}
	
	private void getEventTimexFeatureVector(TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath,
			StringBuilder et, StringBuilder etRule) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;
		
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			if (isConsistent(txpFile.getName())) {
				File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
				List<PairFeatureVector> vectors = getEventTimexFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
				etFeatureNames.clear();
				for (String s : EventTimexFeatureVector.fields) {
					if (s!= null) etFeatureNames.add(s);
				}
				printEventTimexFeatureVector(vectors, et, etRule);
			}
		}	
	}
	
	private List<PairFeatureVector> getEventEventFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		List<PairFeatureVector> vectors = new ArrayList<PairFeatureVector>();
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				if (fv.getPairType().equals(PairType.event_event)) {					
					numEE ++;
					if (tlink.isDeduced()) numEEDeduced ++;
					fv = new EventEventFeatureVector(fv);
						
					if (classifier.equals(VectorClassifier.yamcha)) {
						fv.addToVector(Feature.id);
					}
					
//					//TODO: get phrase embedding for token, lemma features
//					fv.addPhraseFeatureToVector(Feature.tokenSpace);
//					fv.addPhraseFeatureToVector(Feature.lemmaSpace);
//					fv.addPhraseFeatureToVector(Feature.tokenChunk);					
					
					//Add features to feature vector
					for (Feature f : eeFeatureList) {
						if (classifier.equals(VectorClassifier.libsvm) ||
								classifier.equals(VectorClassifier.liblinear) ||
								classifier.equals(VectorClassifier.weka)) {
							fv.addBinaryFeatureToVector(f);
						} else if (classifier.equals(VectorClassifier.yamcha) ||
								classifier.equals(VectorClassifier.none)) {
							fv.addToVector(f);
						}
					}
					
					//TODO addToVector phrase embedding for temporal & causal signal
					//fv.addPhraseFeatureToVector(Feature.tempMarkerTextPhrase);
					//fv.addPhraseFeatureToVector(Feature.causMarkerTextPhrase);
					
					if (classifier.equals(VectorClassifier.libsvm) || 
							classifier.equals(VectorClassifier.liblinear)) {
						fv.addBinaryFeatureToVector(Feature.label);
					} else if (classifier.equals(VectorClassifier.yamcha) ||
							classifier.equals(VectorClassifier.weka) ||
							classifier.equals(VectorClassifier.none)){
						fv.addToVector(Feature.label);
					}
					
					vectors.add(fv);
					
				} else if (fv.getPairType().equals(PairType.timex_timex)) {
					//////////////only for counting
					numTT ++;
					if (tlink.isDeduced()) numTTDeduced ++;
				}
			}
		}
		return vectors;
	}
	
	private List<PairFeatureVector> getEventTimexFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile) throws Exception {
		
		Doc docTxp = txpParser.parseDocument(txpFile.getPath());
		Doc docTml = tmlParser.parseDocument(tmlFile.getPath());
		
		List<PairFeatureVector> vectors = new ArrayList<PairFeatureVector>();
		
		//for (TemporalRelation tlink : docTxp.getTlinks()) {	//for every TLINK in TXP file: candidate pairs
		for (TemporalRelation tlink : docTml.getTlinks()) {	//for every TLINK in TML file: gold annotated pairs
			if (!tlink.getSourceID().equals(tlink.getTargetID())
					&& docTxp.getEntities().containsKey(tlink.getSourceID())
					&& docTxp.getEntities().containsKey(tlink.getTargetID())
					&& !tlink.getRelType().equals("NONE")
					) {	//classifying the relation task
				
				Entity e1 = docTxp.getEntities().get(tlink.getSourceID());
				Entity e2 = docTxp.getEntities().get(tlink.getTargetID());
				
				PairFeatureVector fv = new PairFeatureVector(docTxp, e1, e2, tlink.getRelType(), tsignalList, csignalList);	
				if (fv.getPairType().equals(PairType.event_timex)) {
					numET ++;
					if (tlink.isDeduced()) numETDeduced ++;
					fv = new EventTimexFeatureVector(fv);
				
					if (classifier.equals(VectorClassifier.yamcha)) {
						fv.addToVector(Feature.id);
					}
				
					//TODO: get phrase embedding for token, lemma features
//					fv.addPhraseFeatureToVector(Feature.tokenSpace);
//					fv.addPhraseFeatureToVector(Feature.lemmaSpace);
//					fv.addPhraseFeatureToVector(Feature.tokenChunk);
				
					//Add features to feature vector
					for (Feature f : etFeatureList) {
						if (classifier.equals(VectorClassifier.libsvm) ||
								classifier.equals(VectorClassifier.liblinear) ||
								classifier.equals(VectorClassifier.weka)) {
							fv.addBinaryFeatureToVector(f);
						} else if (classifier.equals(VectorClassifier.yamcha) ||
								classifier.equals(VectorClassifier.none)) {
							fv.addToVector(f);
						}
					}
					
					//TODO addToVector phrase embedding for temporal signal
					//fv.addPhraseFeatureToVector(Feature.tempMarkerTextPhrase);
					
					if (classifier.equals(VectorClassifier.libsvm) || 
							classifier.equals(VectorClassifier.liblinear)) {
						fv.addBinaryFeatureToVector(Feature.label);
					} else if (classifier.equals(VectorClassifier.yamcha) ||
							classifier.equals(VectorClassifier.weka) ||
							classifier.equals(VectorClassifier.none)){
						fv.addToVector(Feature.label);
					}
					
					vectors.add(fv);
				}
			}
		}
		return vectors;
	}
	
	private void getFeatureVectorPerFile(TXPParser txpParser, TimeMLParser tmlParser,
			File txpFile, File tmlFile, 
			StringBuilder ee, StringBuilder et, 
			StringBuilder eeCoref, StringBuilder etRule,
			boolean eecoref, boolean etrule) throws Exception {
		List<PairFeatureVector> eeVectors = getEventEventFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
		List<PairFeatureVector> etVectors = getEventTimexFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
		
		//Field/column titles of features
		eeFeatureNames.clear();
		for (String s : EventEventFeatureVector.fields) {
			if (s!= null) eeFeatureNames.add(s);
		}
		etFeatureNames.clear();
		for (String s : EventTimexFeatureVector.fields) {
			if (s!= null) etFeatureNames.add(s);
		}
		
		if (eecoref) {
			printEventEventFeatureVector(eeVectors, ee, eeCoref);
		} else {
			printEventEventFeatureVector(eeVectors, ee, null);
		}
		if (etrule) {
			printEventEventFeatureVector(etVectors, et, etRule);
		} else {
			printEventEventFeatureVector(etVectors, et, null);
		}
	}
	
	private void writeArffFile(PrintWriter pw, StringBuilder vec, List<String> featureNames) {
		//Header
		pw.write("@relation " + name + "-ee\n\n");
		
		//Field/column titles of features
		for (String s : featureNames) {
			if (s!= null) {
//				if (s.equals("entDistance") || s.equals("sentDistance")) {
//					eePW.write("@attribute " + s + " numeric\n");
//				} else if (s.equals("wnSim")) {
//					eePW.write("@attribute " + s + " numeric\n");
//				} else 
					if (s.equals("label")) {
					pw.write("@attribute " + s + " {BEFORE, AFTER, IBEFORE, IAFTER, IDENTITY, "
							+ "SIMULTANEOUS, INCLUDES, IS_INCLUDED, DURING, DURING_INV, "
							+ "BEGINS, BEGUN_BY, ENDS, ENDED_BY}\n");
				} else {
					pw.write("@attribute " + s + " {0,1}\n");
				}
			}
		}
		
		//Vectors
		pw.write("\n@data\n");
		pw.write(vec.toString());
		
		pw.close();
	}
	
	private double averageF1(List<String> pairs) {
		int[] tp = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		int[] fp = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		int[] total = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		double[] precision = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		double[] recall = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		double[] f1 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		String[] label = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "BEGINS", "BEGUN_BY",
				"ENDS", "ENDED_BY", "INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV",
				"IDENTITY", "SIMULTANEOUS"};
		List<String> labelList = Arrays.asList(label);
		int idxLabel, idxPred;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				String[] cols = s.split("\t");
				idxLabel = labelList.indexOf(cols[2]);
				idxPred = labelList.indexOf(cols[3]);
				total[idxLabel] ++;
				if (idxPred == idxLabel) tp[idxPred] ++;
				else fp[idxPred] ++;
			}
		}
		double totalf1 = 0;
		for (int i=0; i<labelList.size(); i++) {
			precision[i] = tp[i]/(tp[i]+fp[i]);
			recall[i] = tp[i]/total[i];
			f1[i] = (2*precision[i]*recall[i])/(precision[i]+recall[i]);
			totalf1 += f1[i];
		}
		return totalf1/labelList.size();
	}
	
	private double accuracy(List<String> pairs) {
		int eeCorrect = 0;
		int eeInstance = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				String[] cols = s.split("\t");
				if (cols[2].equals(cols[3])) eeCorrect += 1;
				eeInstance += 1;
			}
		}
		return (double)eeCorrect/(double)eeInstance;
	}
	
	private int numCorrect(List<String> pairs) {
		int eeCorrect = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				String[] cols = s.split("\t");
				if (cols[2].equals(cols[3])) eeCorrect += 1;
			}
		}
		return eeCorrect;
	}
	
	private int numInstance(List<String> pairs) {
		int eeInstance = 0;
		for (String s : pairs) { //e1	e2	label	predicted
			if (!s.isEmpty()) {
				eeInstance += 1;
			}
		}
		return eeInstance;
	}
	
	private String getLabelFromNum(String num) {
		String[] temp_rel_type = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> temp_rel_type_list = Arrays.asList(temp_rel_type);
		return temp_rel_type_list.get(Integer.valueOf(num)-1);
	}
	
	private int getNumFromLabel(String label) {
		String[] temp_rel_type = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		List<String> temp_rel_type_list = Arrays.asList(temp_rel_type);
		return temp_rel_type_list.indexOf(label) + 1;
	}
	
	private List<String> predictEventEventPerFile(RemoteServer rs, String eeFilepath,
			TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile,
			boolean eecoref, boolean etrule) throws Exception {
		List<String> eeResult = new ArrayList<String>();
		
		if (classifier.equals(VectorClassifier.yamcha)) {
			String cmdCd = "cd tools/yamcha-0.33/";			
			String cmdTestEE = "./usr/local/bin/yamcha -m ~/models/" + name + "-ee.model"
					+ " < ~/" + eeFilepath
					+ " | cut -f1,2," + (eeFeatureNames.size()) + "," + (eeFeatureNames.size()+1);
					//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
			List<String> eeLabel = rs.executeCommand(cmdCd + " && " + cmdTestEE);
			eeResult.addAll(eeLabel);
			
		} else if (classifier.equals(VectorClassifier.libsvm)) {
			String cmdCd = "cd tools/libsvm-3.20/";				
			String cmdTestEE = "./svm-predict -q"
					+ " ~/" + eeFilepath + ".libsvm"
					+ " ~/models/" + name + "-ee-libsvm.model"
					+ " ~/data/" + name + "-ee-eval.tagged";
			String cmdCatEE = "cat ~/data/" + name + "-ee-eval.tagged";			
			List<String> eeLabel = rs.executeCommand(cmdCd + " && " + cmdTestEE + " && " + cmdCatEE);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
						
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : eePair.toString().split("\n")) {
				eeResult.add(pair + "\t" + getLabelFromNum(eeLabel.get(i)));
				i += 1;
			}
		} else if (classifier.equals(VectorClassifier.liblinear)) {
			String cmdCd = "cd tools/liblinear-2.01/";				
			String cmdTestEE = "./predict -q"
					+ " ~/" + eeFilepath + ".libsvm"
					+ " ~/models/" + name + "-ee-liblinear.model"
					+ " ~/data/" + name + "-ee-eval.tagged";
			String cmdCatEE = "cat ~/data/" + name + "-ee-eval.tagged";		
			List<String> eeLabel = rs.executeCommand(cmdCd + " && " + cmdTestEE + " && " + cmdCatEE);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
						
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : eePair.toString().split("\n")) {
				eeResult.add(pair + "\t" + getLabelFromNum(eeLabel.get(i)));
				i += 1;
			}
		} else if (classifier.equals(VectorClassifier.weka)) {
			List<String> eeLabel = new ArrayList<String>();
			double clsLabel;
			
			Instances eeTest = new DataSource(eeFilepath + ".arff").getDataSet();
			eeTest.setClassIndex(eeTest.numAttributes() - 1);
			for (int i = 0; i < eeTest.numInstances(); i++) {
				clsLabel = eeCls.classifyInstance(eeTest.instance(i)); 
				eeLabel.add(eeTest.classAttribute().value((int) clsLabel));
			}
			
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : eePair.toString().split("\n")) {
				eeResult.add(pair + "\t" + eeLabel.get(i));
				i += 1;
			}
		}
		return eeResult;
	}
	
	private List<String> predictEventTimexPerFile(RemoteServer rs, String etFilepath,
			TXPParser txpParser, TimeMLParser tmlParser, 
			File txpFile, File tmlFile,
			boolean eecoref, boolean etrule) throws Exception {
		List<String> etResult = new ArrayList<String>();
		
		if (classifier.equals(VectorClassifier.yamcha)) {
			String cmdCd = "cd tools/yamcha-0.33/";			
			String cmdTestET = "./usr/local/bin/yamcha -m ~/models/" + name + "-et.model"
					+ " < ~/" + etFilepath + " "
					+ " | cut -f1,2," + (etFeatureNames.size()) + "," + (etFeatureNames.size()+1);
					//+ " > ~/data/"+name+"-ee-eval-tagged.tlinks";
			List<String> etLabel = rs.executeCommand(cmdCd + " && " + cmdTestET);
			etResult.addAll(etLabel);
			
		} else if (classifier.equals(VectorClassifier.libsvm)) {
			String cmdCd = "cd tools/libsvm-3.20/";	
			String cmdTestET = "./svm-predict -q"
					+ " ~/" + etFilepath + ".libsvm"
					+ " ~/models/" + name + "-et-libsvm.model"
					+ " ~/data/" + name + "-et-eval.tagged";
			String cmdCatET = "cat ~/data/" + name + "-et-eval.tagged";			
			List<String> etLabel = rs.executeCommand(cmdCd + " && " + cmdTestET + " && " + cmdCatET);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
			
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : etPair.toString().split("\n")) {
				etResult.add(pair + "\t" + getLabelFromNum(etLabel.get(i)));
				i += 1;
			}
		} else if (classifier.equals(VectorClassifier.liblinear)) {
			String cmdCd = "cd tools/liblinear-2.01/";	
			String cmdTestET = "./predict -q"
					+ " ~/" + etFilepath + ".libsvm"
					+ " ~/models/" + name + "-et-liblinear.model"
					+ " ~/data/" + name + "-et-eval.tagged";
			String cmdCatET = "cat ~/data/" + name + "-et-eval.tagged";			
			List<String> etLabel = rs.executeCommand(cmdCd + " && " + cmdTestET + " && " + cmdCatET);
			
			String rmTagged = "cd ~/data/ && rm *.tagged";
			rs.executeCommand(rmTagged);
			
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : etPair.toString().split("\n")) {
				etResult.add(pair + "\t" + getLabelFromNum(etLabel.get(i)));
				i += 1;
			}
		} else if (classifier.equals(VectorClassifier.weka)) {
			List<String> etLabel = new ArrayList<String>();
			double clsLabel;
			
			Instances etTest = new DataSource(etFilepath + ".arff").getDataSet();
			etTest.setClassIndex(etTest.numAttributes() - 1);
			for (int i = 0; i < etTest.numInstances(); i++) {
				clsLabel = etCls.classifyInstance(etTest.instance(i)); 
				etLabel.add(etTest.classAttribute().value((int) clsLabel));
			}
			
			StringBuilder eePair = new StringBuilder();
			StringBuilder etPair = new StringBuilder();
			StringBuilder eeCorefPair = new StringBuilder();
			StringBuilder etRulePair = new StringBuilder();
			getPairIDPerFile(txpParser, tmlParser, txpFile, tmlFile, 
					eePair, etPair, eeCorefPair, etRulePair, eecoref, etrule);
			
			int i = 0;
			for (String pair : etPair.toString().split("\n")) {
				etResult.add(pair + "\t" + etLabel.get(i));
				i += 1;
			}
		}
		return etResult;
	}
	
	private void writeTimeMLFile(TimeMLParser tmlParser, File tmlFile,
			List<String> eeResult, List<String> etResult, List<String> ttResult) 
					throws Exception {
		Doc dTml = tmlParser.parseDocument(tmlFile.getPath());
		TimeMLDoc tml = new TimeMLDoc(tmlFile.getPath());
		tml.removeLinks();
		
		int linkId = 1;
		TemporalRelation tlink = new TemporalRelation();
		for (String eeStr : eeResult) {
			if (!eeStr.isEmpty()) {
				String[] cols = eeStr.split("\t");
				tlink.setSourceID(dTml.getInstancesInv().get(cols[0]).replace("tmx", "t"));
				tlink.setTargetID(dTml.getInstancesInv().get(cols[1]).replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Event");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String etStr : etResult) {
			if (!etStr.isEmpty()) {
				String[] cols = etStr.split("\t");
				tlink.setSourceID(dTml.getInstancesInv().get(cols[0]).replace("tmx", "t"));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Event");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		for (String ttStr : ttResult) {
			if (!ttStr.isEmpty()) {
				String[] cols = ttStr.split("\t");
				tlink.setSourceID(cols[0].replace("tmx", "t"));
				tlink.setTargetID(cols[1].replace("tmx", "t"));
				tlink.setRelType(cols[3]);
				tlink.setSourceType("Timex");
				tlink.setTargetType("Timex");
				tml.addLink(tlink.toTimeMLNode(tml.getDoc(), linkId));
				linkId += 1;
			}
		}
		
		File sysTmlPath = new File(systemTMLPath + "/" + tmlFile.getName());
		PrintWriter sysTML = new PrintWriter(sysTmlPath.getPath());
		sysTML.write(tml.toString());
		sysTML.close();
	}
	
	private void combineEventEventFeatureVectorEmbedding(RemoteServer rs,
			TXPParser txpParser, TimeMLParser tmlParser, 
			String txpDirpath, String tmlDirpath,
			String eeWordEmbeddingPath, String eeFilepath,
			int numEmbeddingCols) 
					throws Exception {
		File[] txpFiles = new File(txpDirpath).listFiles();		
		if (txpFiles == null) return;
		
		List<String> fvLines = new ArrayList<String>();
		for (File txpFile : txpFiles) {	//assuming that there is no sub-directory
			File tmlFile = new File(tmlDirpath, txpFile.getName().replace(".txp", ""));
			List<PairFeatureVector> vectors = getEventEventFeatureVectorPerFile(txpParser, tmlParser, txpFile, tmlFile);
			//Field/column titles of features
			eeFeatureNames.clear();
			for (String s : EventEventFeatureVector.fields) {
				if (s!= null) eeFeatureNames.add(s);
			}
			for (PairFeatureVector fv : vectors) {
				fvLines.add(fv.printCSVVectors());
			}
		}	
		
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.libsvm) ||
				classifier.equals(VectorClassifier.liblinear)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".libsvm", "UTF-8");
			int idx;
			String label;
			
			int weIdx = 0;
			BufferedReader br = new BufferedReader(new FileReader(eeWordEmbeddingPath));
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.isEmpty()) {
			    	String[] weCols = line.split(",");
			    	String[] fvCols = fvLines.get(weIdx).split(",");
			    	label = fvCols[fvCols.length-1].trim();
			    	eePW.write(label);
			    	idx = 1;
			    	for (int i=0; i<weCols.length-1; i++) {
			    		eePW.write(" " + idx + ":" + weCols[i].trim());
			    		idx += 1;
			    	}
			    	for (int i=0; i<fvCols.length-1; i++) {
			    		eePW.write(" " + idx + ":" + fvCols[i].trim());
			    		idx += 1;
			    	}
			    	eePW.write("\n");
			    	weIdx += 1;
				}
		    }
			eePW.close();
			br.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File eeFile = new File(eeFilepath + ".libsvm");
			rs.copyFile(eeFile, "data/libsvm/");
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".arff", "UTF-8");	
			eePW.write("@relation " + name + "-ee-word-embedding-feature-vector\n\n");
			
			int idx = 1;
			for (int i=0; i<numEmbeddingCols; i++) {
	    		eePW.write("@attribute attr" + idx + " numeric\n");
	    		idx += 1;
	    	}
			for (String s : eeFeatureNames) {
				if (s!= null) {
//					if (s.equals("entDistance") || s.equals("sentDistance")) {
//						eePW.write("@attribute " + s + " numeric\n");
//					} else if (s.equals("wnSim")) {
//						eePW.write("@attribute " + s + " numeric\n");
//					} else 
						if (s.equals("label")) {
							eePW.write("@attribute " + s + " {BEFORE, AFTER, IBEFORE, IAFTER, IDENTITY, "
								+ "SIMULTANEOUS, INCLUDES, IS_INCLUDED, DURING, DURING_INV, "
								+ "BEGINS, BEGUN_BY, ENDS, ENDED_BY}\n");
					} else {
						eePW.write("@attribute " + s + " {0,1}\n");
					}
				}
			}
			
			eePW.write("\n@data\n");
			
			int weIdx = 0;
			BufferedReader br = new BufferedReader(new FileReader(eeWordEmbeddingPath));
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.isEmpty()) {
					eePW.write(line + "," + fvLines.get(weIdx) + "\n");
					weIdx += 1;
				}
			}
			eePW.close();
			br.close();
		}		
	}
	
	private void writeEventEventEmbedding (RemoteServer rs, String eeWordEmbeddingPath, 
			String eeFilepath, int numEmbeddingCols) throws Exception {
		System.setProperty("line.separator", "\n");
		if (classifier.equals(VectorClassifier.libsvm) ||
				classifier.equals(VectorClassifier.liblinear)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".libsvm", "UTF-8");
			int idx;
			String label;
			
			BufferedReader br = new BufferedReader(new FileReader(eeWordEmbeddingPath));
			String line;
			while ((line = br.readLine()) != null) {
		    	String[] cols = line.split(",");
		    	label = cols[cols.length-1].trim();
		    	eePW.write(String.valueOf(getNumFromLabel(label)));
		    	idx = 1;
		    	for (int i=0; i<cols.length-1; i++) {
		    		eePW.write(" " + idx + ":" + cols[i].trim());
		    		idx += 1;
		    	}
		    	eePW.write("\n");
		    }
			eePW.close();
			br.close();
			
			//Copy training data to server
			//System.out.println("Copy training data...");
			File eeFile = new File(eeFilepath + ".libsvm");
			rs.copyFile(eeFile, "data/libsvm/");
			
		} else if (classifier.equals(VectorClassifier.weka)) {
			PrintWriter eePW = new PrintWriter(eeFilepath + ".arff", "UTF-8");	
			eePW.write("@relation " + name + "-ee-word-embedding\n\n");
			
			int idx = 1;
			for (int i=0; i<numEmbeddingCols; i++) {
	    		eePW.write("@attribute attr" + idx + " numeric\n");
	    		idx += 1;
	    	}
			eePW.write("@attribute label {BEFORE, AFTER, IBEFORE, IAFTER, IDENTITY, "
					+ "SIMULTANEOUS, INCLUDES, IS_INCLUDED, DURING, DURING_INV, "
					+ "BEGINS, BEGUN_BY, ENDS, ENDED_BY}\n");
			
			eePW.write("\n@data\n");
			
			BufferedReader br = new BufferedReader(new FileReader(eeWordEmbeddingPath));
			String line;
			while ((line = br.readLine()) != null) {
				eePW.write(line + "\n");
			}
			eePW.close();
			br.close();
		}
	}
	
	private void initInconsistentFiles() throws Exception {
		inconsistentFiles = new ArrayList<String>();
		String inconsistentLog = "data/inconsistent.txt";
		BufferedReader br = new BufferedReader(new FileReader(inconsistentLog));
		String line;
		while ((line = br.readLine()) != null) {
			inconsistentFiles.add(line);
		}
		br.close();
	}
	
	private boolean isConsistent(String txpFilename) {
		String tmlFilename = txpFilename.replace(".txp", "");
		if (!inconsistentFiles.contains(tmlFilename)) return true;
		else return false;
	}

}
