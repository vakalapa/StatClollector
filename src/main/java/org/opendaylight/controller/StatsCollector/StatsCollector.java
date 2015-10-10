/*
 * Copyright (C) 2014 SDN Hub

 Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.
 You may not use this file except in compliance with this License.
 You may obtain a copy of the License at

    http://www.gnu.org/licenses/gpl-3.0.txt

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied.

 *
 */

package org.opendaylight.controller;

import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.reader.FlowOnNode;
import org.opendaylight.controller.sal.reader.NodeConnectorStatistics;
import org.opendaylight.controller.sal.reader.IReadService;
import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.statisticsmanager.IStatisticsManager;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.oro.text.regex.MalformedPatternException;

import java.util.*;
import com.jcraft.jsch.*;
import expect4j.Closure;
import expect4j.Expect4j;
import expect4j.ExpectUtils;
import expect4j.ExpectState;
import expect4j.matches.Match;
import expect4j.matches.RegExpMatch;

public class StatsCollector {
    private static final int COMMAND_EXECUTION_SUCCESS_OPCODE = -2;
    private static String ENTER_CHARACTER = "\r";
    private static final int SSH_PORT = 22;
    private static final Logger logger = LoggerFactory
            .getLogger(StatsCollector.class);
    private Set<NodeConnector> allConnectors = null;
    private String userName = "admin";
    private String password = "";
    private String host = "";
    private Expect4j expect = null;
    List<String> lstCmds = new ArrayList<String>();
    StringBuilder buffer = new StringBuilder();


    void init() {
        logger.info("INIT called!");
    }

    void destroy() {
        logger.info("DESTROY called!");
    }

    void start() {
        logger.info("START called!");
        getFlowStatistics();
    }

    void stop() {
        logger.info("STOP called!");
    }

    public Expect4j SSH() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(userName, host, 22);
        if (password != null) {
            session.setPassword(password);
        }
        Hashtable<String,String> config = new Hashtable<String,String>();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(60000);
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        Expect4j expect = new Expect4j(channel.getInputStream(), channel.getOutputStream());
        channel.connect();
        return expect;
    }

    private boolean checkResult(int intRetVal) {
        if (intRetVal == COMMAND_EXECUTION_SUCCESS_OPCODE) {
            return true;
        }
        return false;
    }

    public boolean isSuccess(List<Match> objPattern, String strCommandPattern) {
        try {
            boolean isFailed = checkResult(expect.expect(objPattern));
 
            if (!isFailed) {
                expect.send(strCommandPattern);
                expect.send(ENTER_CHARACTER);
                return true;
            }
            return false;
        } catch (MalformedPatternException ex) {
            ex.printStackTrace();
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public String execute(List<String> cmdsToExecute) {

	lstCmds = cmdsToExecute;
 
        Closure closure = new Closure() {
            public void run(ExpectState expectState) throws Exception {
                buffer.append(expectState.getBuffer());
            }
        };

        List<Match> lstPattern =  new ArrayList<Match>();
	String[] linuxPromptRegEx = new String[]{"\\>","#"};

        for (String regexElement : linuxPromptRegEx) {
            try {
                Match mat = new RegExpMatch(regexElement, closure);
                lstPattern.add(mat);
            } catch (MalformedPatternException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
 
        try {
            expect = SSH();
            boolean isSuccess = true;
            for(String strCmd : lstCmds) {
                isSuccess = isSuccess(lstPattern,strCmd);
                if (!isSuccess) {
                    isSuccess = isSuccess(lstPattern,strCmd);
                }
            }
 
            checkResult(expect.expect(lstPattern));
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (expect!=null) {
            	expect.close();
	    }
        }
        return buffer.toString();
    }


    void getFlowStatistics() {
        String containerName = "default";
        IStatisticsManager statsManager = (IStatisticsManager) ServiceHelper
                .getInstance(IStatisticsManager.class, containerName, this);

        ISwitchManager switchManager = (ISwitchManager) ServiceHelper
                .getInstance(ISwitchManager.class, containerName, this);

	ITopologyManager topologyManager = (ITopologyManager) ServiceHelper
                .getInstance(ITopologyManager.class, containerName, this);

	IReadService readStats = (IReadService) ServiceHelper
		.getInstance(IReadService.class, containerName, this);

        for (Node node : switchManager.getNodes()) {
            System.out.println("\n\nNode: " + node);
            for (FlowOnNode flow : statsManager.getFlows(node)) {
                System.out.println(" DST: "
                        + flow.getFlow().getMatch().getField(MatchType.NW_DST)
                        + " Bytes: " + flow.getByteCount());
            }

		/* New Code */
		allConnectors = switchManager.getNodeConnectors(node);
		for(NodeConnector connector : allConnectors) {
			if(readStats.getTransmitRate(connector) > 0) {
				System.out.println("Node Connector: " + connector.toString());
				if(topologyManager.getHostsAttachedToNodeConnector(connector) != null)
					System.out.println("getHostsAttachedtoNodeConnector: " + topologyManager.getHostsAttachedToNodeConnector(connector).toString());
				if(statsManager.getNodeConnectorStatistics(connector) != null)
					System.out.println("Get Node Connector Statistics " + statsManager.getNodeConnectorStatistics(connector).toString());
				System.out.println("Transmit Rate for this connector: " + readStats.getTransmitRate(connector) + "bps");
				System.out.println("\n");
			}
		}
        }

	if(topologyManager.getEdges() != null)
		System.out.println("getEdges: " + topologyManager.getEdges().toString());
	if(topologyManager.getNodeConnectorWithHost() != null)
		System.out.println("getNodeConnectorWithHost: " + topologyManager.getNodeConnectorWithHost().toString());
	if(topologyManager.getNodeEdges() != null)
		System.out.println("getNodeEdges: " + topologyManager.getNodeEdges().toString());
	if(topologyManager.getNodesWithNodeConnectorHost() != null)
		System.out.println("getNodesWithNodeConnectorHost: " + topologyManager.getNodesWithNodeConnectorHost().toString());
	
	List<String> cmdsToExecute = new ArrayList<String>();
        cmdsToExecute.add("ls");
        cmdsToExecute.add("pwd");
        cmdsToExecute.add("mkdir testdir");
        String outputLog = execute(cmdsToExecute);
        System.out.println(outputLog);
    }
}
