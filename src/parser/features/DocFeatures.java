package parser.features;

public class DocFeatures {
	
	private String filename;
	private enum Language {EN, IT};
	private Language lang;
	
	public DocFeatures() {
		this.setLang(Language.EN);
	}
	
	public DocFeatures(Language lang) {
		this.setLang(lang);
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public Language getLang() {
		return lang;
	}

	public void setLang(Language lang) {
		this.lang = lang;
	}

}
