package com.metavize.tran.ids;

import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.*;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import java.net.InetAddress;
import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.PortRange;
import com.metavize.mvvm.tran.firewall.IPMatcher;
import com.metavize.mvvm.tran.ParseException;

public class IDSRuleManager {

	public static final int ALERT = 0;
	public static final int LOG = 1;
	public static final String[] ACTIONS = { "alert", "log" };
			
	private List<IDSRuleHeader> ruleHeaders = Collections.synchronizedList(new ArrayList<IDSRuleHeader>());
	
	private static Pattern variablePattern = Pattern.compile("\\$[^ \n\r\t]+");
	
	public static List<IDSVariable> defaultVariables = new ArrayList<IDSVariable>(); 
	static {
		defaultVariables.add(new IDSVariable("$EXTERNAL_NET","!10.0.0.1/24","This is a description"));
		defaultVariables.add(new IDSVariable("$HOME_NET", "10.0.0.1/24","This is a description"));
		defaultVariables.add(new IDSVariable("$HTTP_PORTS", ":80","This is a description"));
		defaultVariables.add(new IDSVariable("$HTTP_SERVERS", "10.0.0.1/24","This is a description"));
		defaultVariables.add(new IDSVariable("$SMTP_SERVERS", "any","This is a description"));
		defaultVariables.add(new IDSVariable("$SSH_PORTS", "any","This is a description"));
		defaultVariables.add(new IDSVariable("$SQL_SERVERS", "any","This is a description"));
		defaultVariables.add(new IDSVariable("$TELNET_SERVERS", "any","This is a description"));
		defaultVariables.add(new IDSVariable("$ORACLE_PORTS", "any","This is a description"));
		defaultVariables.add(new IDSVariable("$AIM_SERVERS", "any","This is a description"));
	}
																													
	private static final Logger log = Logger.getLogger(IDSRuleManager.class);
	static {
		log.setLevel(Level.INFO);
	}
	public IDSRuleManager() {
	}

	public IDSRuleHeader addRule(String rule) throws ParseException {
		
		if(rule.length() <= 0 || rule.charAt(0) == '#')
			return null;
		rule = substituteVariables(rule);
		String ruleParts[] 			= IDSStringParser.parseRuleSplit(rule);
		IDSRuleHeader header		= IDSStringParser.parseHeader(ruleParts[0]);
		IDSRuleSignature signature	= IDSStringParser.parseSignature(ruleParts[1], header.getAction());
	
		signature.setToString(ruleParts[1]);
		//Might want to change this.
		if(!signature.remove()) {
			header.setSignature(signature);
			ruleHeaders.add(header);
			return header;
		}
		return null;
	}

	public List<IDSRuleSignature> matchesHeader(Protocol protocol, InetAddress clientAddr, int clientPort, InetAddress serverAddr, int serverPort) {
		List<IDSRuleSignature> returnList = new LinkedList();
	//	System.out.println(ruleHeaders.size()); /** *****************************************/
	
		synchronized(ruleHeaders) {
			Iterator<IDSRuleHeader> it = ruleHeaders.iterator();
			while(it.hasNext()) {
				IDSRuleHeader header = it.next();
				if(header.matches(protocol, clientAddr, clientPort, serverAddr, serverPort)) {
					returnList.add(header.getSignature());
				//	System.out.println("\n\n"+header+"\n"+header.getSignature());
				}
			}
		}
	//	System.out.println(returnList.size()); /** *****************************************/
		
		return returnList;
	}

	/*For debug yo*/
	public List<IDSRuleHeader> getHeaders() {
		return ruleHeaders;
	}

	public void clear() {
		ruleHeaders.clear();
	}

	private String substituteVariables(String string) {
		Matcher match = variablePattern.matcher(string);
		if(match.find()) {
			List<IDSVariable> varList;
			if(IDSDetectionEngine.instance().getSettings() == null)
				varList = defaultVariables;
			else {
				varList = (List<IDSVariable>) IDSDetectionEngine.instance().getSettings().getVariables();
			}
			Iterator<IDSVariable> it = varList.iterator();
			while(it.hasNext()) {
				IDSVariable var = it.next();
				string = string.replaceAll("\\"+var.getVariable(),var.getDefinition());
			}																		
		}
		return string;
	}
}
