package parser;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import parser.entities.CausalRelation;
import parser.entities.Doc;
import parser.entities.EntityEnum;
import parser.entities.TemporalRelation;
import parser.entities.TimeMLDoc;

public class TimeMLParser {
	
	private String timeMLPath;
	private EntityEnum.Language language;
	
	public TimeMLParser(EntityEnum.Language lang) {
		this.setLanguage(lang);
	}
	
	public TimeMLParser(String filepath) {
		this.setTimeMLPath(filepath);
		this.setLanguage(EntityEnum.Language.EN);
	}
	
	public TimeMLParser(String filepath, EntityEnum.Language lang) {
		this.setTimeMLPath(filepath);
		this.setLanguage(lang);
	}

	public EntityEnum.Language getLanguage() {
		return language;
	}

	public void setLanguage(EntityEnum.Language language) {
		this.language = language;
	}

	public String getTimeMLPath() {
		return timeMLPath;
	}

	public void setTimeMLPath(String timeMLPath) {
		this.timeMLPath = timeMLPath;
	}
	
	public Document getTimeML(String filepath) throws ParserConfigurationException, SAXException, IOException {
		TimeMLDoc tmlDoc = new TimeMLDoc(filepath);
		return tmlDoc.getDoc();
	}
	
	public Doc parseDocument(String filepath) throws ParserConfigurationException, SAXException, IOException, TransformerException {
		TimeMLDoc tmlDoc = new TimeMLDoc(filepath);
		
		Doc d = new Doc();
		setInstances(tmlDoc, d);
		setTlinks(tmlDoc, d);
		setClinks(tmlDoc, d);
		d.setFilename(new File(filepath).getName());
		
		return d;
	}
	
	public List<String> getEvents(TimeMLDoc tmlDoc) {
		List<String> arrEvents = new ArrayList<String>();
		NodeList events = tmlDoc.getDoc().getElementsByTagName("EVENT");
		for (int index = 0; index < events.getLength(); index++) {
			Node event = events.item(index);
			NamedNodeMap attrs = event.getAttributes();			
			for (int i = 0; i < attrs.getLength(); i++) {
				if (attrs.item(i).getNodeName().equals("eid")) {
					arrEvents.add(attrs.item(i).getNodeValue());
				}
			}
		}
		return arrEvents;
	}
	
	public Map<String, String> getEventTenseAspectPolarity(TimeMLDoc tmlDoc) {
		Map<String, String> mapInstances = new HashMap<String, String>();
		NodeList instances = tmlDoc.getDoc().getElementsByTagName("MAKEINSTANCE");
		String eid = "", tense = "", aspect = "", polarity = "";
		for (int index = 0; index < instances.getLength(); index++) {
			Node event = instances.item(index);
			NamedNodeMap attrs = event.getAttributes();			
			for (int i = 0; i < attrs.getLength(); i++) {
				if (attrs.item(i).getNodeName().equals("eventID")) {
					eid = attrs.item(i).getNodeValue();
				} else if (attrs.item(i).getNodeName().equals("tense")) {
					tense = attrs.item(i).getNodeValue();
				} else if (attrs.item(i).getNodeName().equals("aspect")) {
					aspect = attrs.item(i).getNodeValue();
				} else if (attrs.item(i).getNodeName().equals("polarity")) {
					polarity = attrs.item(i).getNodeValue();
				}  
				if (!eid.equals(""))
					mapInstances.put(eid, tense + "+" + aspect + "+" + polarity);
			}
		}
		return mapInstances;
	}
	
	public static String nodeToString(Node node) throws TransformerFactoryConfigurationError, TransformerException 
	{
		Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		t.setOutputProperty(OutputKeys.INDENT, "yes");
		StringWriter sw = new StringWriter();
		t.transform(new DOMSource(node), new StreamResult(sw));
		return sw.toString();
	}
	
	public String getText(TimeMLDoc tmlDoc) throws TransformerFactoryConfigurationError, TransformerException {
		List<String> arrEvents = new ArrayList<String>();
		Node text = tmlDoc.getDoc().getElementsByTagName("TEXT").item(0);
		String nodeStr = nodeToString(text);
		nodeStr = nodeStr.replaceAll("<TEXT>", "");
		nodeStr = nodeStr.replaceAll("</TEXT>", "");
		return nodeStr.trim();
	}
	
	public String getTextOnly(TimeMLDoc tmlDoc) throws TransformerFactoryConfigurationError, TransformerException {
		List<String> arrEvents = new ArrayList<String>();
		Node text = tmlDoc.getDoc().getElementsByTagName("TEXT").item(0);
		return text.getTextContent();
	}
	
	public void setTlinks(TimeMLDoc tmlDoc, Doc d) {
		NodeList tlinks = tmlDoc.getDoc().getElementsByTagName("TLINK");
		ArrayList<TemporalRelation> tlinkArr = d.getTlinks();
		String source = null, target = null, relType = null;
		String sourceType = null, targetType = null;
		
		for (int index = tlinks.getLength() - 1; index >= 0; index--) {
			boolean deduced = false;
			Node tlink = tlinks.item(index);
			NamedNodeMap attrs = tlink.getAttributes();			
			for (int i = 0; i < attrs.getLength(); i++) {
				switch(attrs.item(i).getNodeName()) {
					case "eventInstanceID": 
						source = attrs.item(i).getNodeValue();
						sourceType = "Event";
						break;
					case "timeID":
						source = attrs.item(i).getNodeValue().replace("t", "tmx");
						sourceType = "Timex";
						break;
					case "relatedToEventInstance":
						target = attrs.item(i).getNodeValue();
						targetType = "Event";
						break;
					case "relatedToTime":
						target = attrs.item(i).getNodeValue().replace("t", "tmx");
						targetType = "Timex";
						break;
					case "relType":
						relType = attrs.item(i).getNodeValue();
						break;
					case "deduced":
						if (attrs.item(i).getNodeValue().equals("true")) {
							deduced = true;
						}
						break;
				}
				if (d.getInstances().containsKey(source)) {
					source = d.getInstances().get(source);
				} 
				if (d.getInstances().containsKey(target)) {
					target = d.getInstances().get(target);
				}
			}
			TemporalRelation tl = new TemporalRelation(source, target);
			tl.setSourceType(sourceType); tl.setTargetType(targetType);
			tl.setRelType(relType);
			tl.setDeduced(deduced);
			tlinkArr.add(tl);
		}
	}
	
	public void setClinks(TimeMLDoc tmlDoc, Doc d) {
		NodeList clinks = tmlDoc.getDoc().getElementsByTagName("CLINK");
		ArrayList<CausalRelation> clinkArr = d.getClinks();
		String source = null, target = null;
		String sourceType = null, targetType = null;
		
		for (int index = clinks.getLength() - 1; index >= 0; index--) {
			Node tlink = clinks.item(index);
			NamedNodeMap attrs = tlink.getAttributes();			
			for (int i = 0; i < attrs.getLength(); i++) {
				switch(attrs.item(i).getNodeName()) {
					case "eventInstanceID": 
						source = attrs.item(i).getNodeValue();
						sourceType = "Event";
						break;
					case "relatedToEventInstance":
						target = attrs.item(i).getNodeValue();
						targetType = "Event";
						break;
				}
				if (d.getInstances().containsKey(source)) {
					source = d.getInstances().get(source);
				} 
				if (d.getInstances().containsKey(target)) {
					target = d.getInstances().get(target);
				}
			}
			CausalRelation cl = new CausalRelation(source, target);
			cl.setSourceType(sourceType); cl.setTargetType(targetType);
			clinkArr.add(cl);
		}
	}
	
	public void setInstances(TimeMLDoc tmlDoc, Doc d) {
		NodeList instances = tmlDoc.getDoc().getElementsByTagName("MAKEINSTANCE");
		Map<String, String> instMap = d.getInstances();
		Map<String, String> instInvMap = d.getInstancesInv();
		String eid = null, eiid = null;
		for (int index = instances.getLength() - 1; index >= 0; index--) {
			Node instance = instances.item(index);
			NamedNodeMap attrs = instance.getAttributes();			
			for (int i = 0; i < attrs.getLength(); i++) {
				if (attrs.item(i).getNodeName().equals("eventID")) {
					eid = attrs.item(i).getNodeValue();
				} else if (attrs.item(i).getNodeName().equals("eiid")) {
					eiid = attrs.item(i).getNodeValue();
				} 
			}
			instMap.put(eiid, eid);
			instInvMap.put(eid, eiid);
		}		
	}
	
	public static void main(String[] args) {
		
		TimeMLParser tmlParser = new TimeMLParser(EntityEnum.Language.EN);
		
		try {
			TimeMLDoc tmlDoc = new TimeMLDoc("./data/example_TML/wsj_1014.tml");
			
//			//List all events
//			List<String> events = tmlParser.getEvents(tmlDoc);
//			for (String s : events)
//				System.out.println(s);
			
			//Text content
			System.out.println(tmlParser.getText(tmlDoc));
			
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
