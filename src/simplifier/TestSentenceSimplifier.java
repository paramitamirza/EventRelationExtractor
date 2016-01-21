package simplifier;

import java.util.ArrayList;
import java.util.HashMap;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import parser.TXPParser;
import parser.TimeMLParser;
import parser.TXPParser.Field;
import parser.entities.EntityEnum;

public class TestSentenceSimplifier {

	public static void main(String [] args) throws Exception {
		
		String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	    LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);
	    		
		String s = "Extremists have bombed two predominantly pro-British Protestant towns and fired mortar shells at a police station since Sinn Fein was expelled from peace talks on Feb. 20 in punishment for two killings blamed on the Irish Republican Army .";
		int fidx1 = 20;
		int fidx2 = 28;
		SentenceSimplifier ss = new SentenceSimplifier(lp, s, true);
		System.out.println(ss.simplifiedString(fidx1, fidx2, false));
		
		String s1 = "The main negative is the risk that the Pope 's visit will persuade a great many more Cubans to break loose of the Cuban government .";
		String s2 = "If so , then the Pope 's visit would really open up a new chapter in the government 's relations with its own society .";
		fidx1 = 19;
		fidx2 = 10;
		SentenceSimplifier ss1 = new SentenceSimplifier(lp, s1, true);
		SentenceSimplifier ss2 = new SentenceSimplifier(lp, s2, true);
//		System.out.println(ss1.simplifiedString(fidx1));
//		System.out.println(ss2.simplifiedString(fidx2));
		
		String s3 = "Last month , after deadly air pollution hit record levels in northern China , officials led by Wen Jiabao , then the prime minister , put forward strict new fuel standards that the oil companies had blocked for years .";
		fidx1 = 25;
		ArrayList<Integer> fidxs2 = new ArrayList<Integer>();
		fidxs2.add(38); 
//		SentenceSimplifier ss3 = new SentenceSimplifier(lp, s3, true);
//		System.out.println(ss3.simplifiedString(fidx1, fidxs2, false));
	}
	
}
