package model.feature;

import java.util.ArrayList;

import parser.entities.Document;

public class FeatureVector {
	
	private Document doc;
	private ArrayList<ArrayList<String>> vectors;
	
	public FeatureVector() {
		
	}
	
	public FeatureVector(Document doc) {
		this.setDoc(doc);
		
	}

	public Document getDoc() {
		return doc;
	}

	public void setDoc(Document doc) {
		this.doc = doc;
	}

	public ArrayList<ArrayList<String>> getColumns() {
		return vectors;
	}

	public void setColumns(ArrayList<ArrayList<String>> vectors) {
		this.vectors = vectors;
	}

}
