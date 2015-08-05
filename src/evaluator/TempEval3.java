package evaluator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TempEval3 {
	
	private String goldPath;
	private String systemPath;
	
	public TempEval3(String gold, String system) {
		this.setGoldPath(gold);
		this.setSystemPath(system);
	}
	
	public void evaluate() throws IOException {
		ProcessBuilder pb = new ProcessBuilder("python", 
				"tools/TempEval3-evaluation-tool/TE3-evaluation.py", 
				goldPath, 
				systemPath);
		Process p = pb.start();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		while ((line = in.readLine()) != null) { 
			System.out.println(line);
		}
	}

	public String getGoldPath() {
		return goldPath;
	}

	public void setGoldPath(String goldPath) {
		this.goldPath = goldPath;
	}

	public String getSystemPath() {
		return systemPath;
	}

	public void setSystemPath(String systemPath) {
		this.systemPath = systemPath;
	}

}
