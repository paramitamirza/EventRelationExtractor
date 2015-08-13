package evaluator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import server.RemoteServer;

public class TempEval3 {
	
	private String goldPath;
	private String systemPath;
	
	public TempEval3(String gold, String system) {
		this.setGoldPath(gold);
		this.setSystemPath(system);
	}
	
	public void evaluate() throws IOException, JSchException, SftpException {
		RemoteServer rs = new RemoteServer();
		
		//Copy gold and system files to remote server
		File[] gold = new File(goldPath).listFiles();
		File[] system = new File(systemPath).listFiles();
		rs.copyFiles(gold, "data/gold/");
		rs.copyFiles(system, "data/system/");
		
		//Run the TempEval3 evaluation tool in remote server
		String cdTE3 = "cd ~/tools/TempEval3-evaluation-tool/";
		String shTE3 = "sh TempEval3-eval.sh";
		List<String> te3Result = rs.executeCommand(cdTE3 + " && " + shTE3);
		for (String te3Str : te3Result) {
			if (!te3Str.isEmpty()) {
				System.out.println(te3Str);
			}
		}
		rs.disconnect();
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
