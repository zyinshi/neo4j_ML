package org.neo4j.shell.extension.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.neo4j.shell.Output;
public class runGL {
	public static StringBuffer excuteCmd(Output out) 
	   {
			try{
				ProcessBuilder pb = new ProcessBuilder("python","/Users/zys/projects/javaworkspace/test_jython/simple.py");
				Process p = pb.start();
				 
				BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
				StringBuffer log = new StringBuffer();
				String line;
				while(( line=in.readLine()) != null) {
	    			// display each output line form python script
   					out.println(line);
	            }
				if(p.waitFor() != 0){
	                InputStream error = p.getErrorStream();
	                System.out.println(error);
				}
				return log;
			}
			catch(Exception e){
				e.printStackTrace();
				return null;
			}
			

	   }
}
