/**
 * $Id$
 */
package com.untangle.node.wan_failover;

import java.util.List;
import java.util.LinkedList;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.IntfConstants;
import com.untangle.uvm.ExecManagerResult;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.network.InterfaceSettings;

/**
 * The WanFailoverTesterMonitor is a daemon thread that launches and monitors the "Tester" threads.
 * Each tester thread handles a single tests and reports back to the Monitor when something important happens
 * (like a WAN changes state).
 * The Monitor is reponsible for taking the results from the Testers and effecting some action.
 * It also handles same basic status reporting for the UI
 */
public class WanFailoverTesterMonitor
{
    private static final int SLEEP_DELAY_MS = 5000;

    private static final String UPLINK_SCRIPT  = System.getProperty( "uvm.bin.dir" ) + "/wan-failover-set-active-wan.sh";

    private final Logger logger = Logger.getLogger( this.getClass());
    
    private WanFailoverApp node;

    private List<WanFailoverTester> testers;
    
    private boolean isRunning = false;
    
    private Boolean wanStatusArray[] = new Boolean[IntfConstants.MAX_INTF+1];

    
    public WanFailoverTesterMonitor( WanFailoverApp node )
    {
        this.node = node;
        this.testers = new LinkedList<WanFailoverTester>();
    }
    
    public String runTest( WanTestSettings test  )
    {
        return WanFailoverTester.runTest( test );
    }
    
    public synchronized void wanStateChange( Integer interfaceId, boolean active )
    {
        if ( ! this.node.isLicenseValid()) {
            logger.warn("Invalid license - ignoring wanStateChange");
        }

        Boolean previousState = wanStatusArray[interfaceId];
        if ( previousState != null && previousState == active ) {
            logger.info("wanStateChange WAN (" + interfaceId + ") ignored. Already in state: " + active );
            return;
        }

        // update status
        wanStatusArray[interfaceId] = active;
        logger.info("wanStateChange WAN (" + interfaceId + "):  " + previousState + " -> " + active );

        // change the routing table and notify interested parties
        syncStateToSystem();

        // log the event and bling the blingers
        this.node.incrementMetric( WanFailoverApp.STAT_CHANGE );
        
        InterfaceSettings ic = UvmContextFactory.context().networkManager().findInterfaceId( interfaceId );
        String systemName = ic.getSystemDev();
        if (ic == null) {
            logger.warn("Unable to log event, cant find interface : " + interfaceId);
        } else {
            if (active) {
                this.node.incrementMetric( WanFailoverApp.STAT_RECONNECTS );
                this.node.logEvent( new WanFailoverEvent( WanFailoverEvent.Action.CONNECTED, interfaceId, ic.getName(), ic.getSystemDev() ));
            } else {
                this.node.incrementMetric( WanFailoverApp.STAT_DISCONNECTS );
                this.node.logEvent( new WanFailoverEvent( WanFailoverEvent.Action.DISCONNECTED, interfaceId, ic.getName(), ic.getSystemDev() ));
            }
        }
    }
    
    public synchronized void reconfigure()
    {
        logger.info("reconfigure()");

        // first stop all previous testers
        for ( WanFailoverTester tester : this.testers ) {
            tester.stop();
        }

        // reinitialize the wanStatusArray
        wanStatusArray[0] = null; // there is no interface "0"
        for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
            wanStatusArray[i] = null;
        }

        // assume all WANs are online (until notified otherwise)
        // if the WAN has no test, it will forever be consider online.
        for ( InterfaceSettings intf : UvmContextFactory.context().networkManager().getEnabledInterfaces() ) {
            if ( intf.getIsWan() ) {
                int interfaceId = intf.getInterfaceId();
                Boolean previousState = wanStatusArray[interfaceId];
                logger.info("wanState reset WAN (" + interfaceId + "):  " + previousState + " -> " + Boolean.TRUE );
                wanStatusArray[interfaceId] = Boolean.TRUE;
            }
        }
        
        // initiate new testers
        this.testers = new LinkedList<WanFailoverTester>();
        if (this.isRunning) {
            WanFailoverSettings settings = this.node.getSettings();
            if (settings == null) {
                logger.warn("reconfigure(): NULL settings");
            } else {
                for ( WanTestSettings wanTest : settings.getTests() ) {
                    WanFailoverTester tester = new WanFailoverTester( wanTest, this, this.node );
                    new Thread(tester).start();
                    logger.info("Launching new Tester: (" + wanTest.getInterfaceId() + ", " + wanTest.getType() + ")");
                    this.testers.add(tester);
                }
            }
        }

        syncStateToSystem();
    }

    public synchronized void start()
    {
        isRunning = true;
        reconfigure();
        syncStateToSystem();
    }

    public synchronized void stop()
    {
        isRunning = false;
        reconfigure();
        syncStateToSystem();
    }
    
    public synchronized List<WanStatus> getWanStatus( )
    {
        List<WanStatus> statusList = new LinkedList<WanStatus>();
        
        for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
            InterfaceSettings ic = UvmContextFactory.context().networkManager().findInterfaceId( i );
            if ( ic != null && ic.getIsWan() ) {
                statusList.add(new WanStatus( i, ic.getName(), ic.getSystemDev(), (this.wanStatusArray[i] == null ? Boolean.TRUE : this.wanStatusArray[i])));
            }
        }

        // for each tester update the corresponding WanStatus stats
        for ( WanFailoverTester tester : this.testers ) {
            for ( WanStatus wanStatus : statusList ) {
                if ( tester.getInterfaceId().equals( wanStatus.getInterfaceId() )) {
                    wanStatus.setTotalTestsRun( wanStatus.getTotalTestsRun() + tester.getTotalTestsRun());
                    wanStatus.setTotalTestsPassed( wanStatus.getTotalTestsPassed() + tester.getTotalTestsPassed());
                    wanStatus.setTotalTestsFailed( wanStatus.getTotalTestsFailed() + tester.getTotalTestsFailed());
                }
            }
        }

        return statusList;
    }

    private synchronized void syncStateToSystem()
    {
        runWanStateChangeScript();
        updateWanAvailabilityCount();
    }

    private void updateWanAvailabilityCount()
    {
        // update the connected and disabled WAN blingers
        int connectedWans = 0;
        int disconnectedWans = 0;
        for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
            if (wanStatusArray[i] != null)
                if (wanStatusArray[i])
                    connectedWans++;
                else
                    disconnectedWans++;
        }
        this.node.setMetric( WanFailoverApp.STAT_CONNECTED, (long) connectedWans);
        this.node.setMetric( WanFailoverApp.STAT_DISCONNECTED, (long) disconnectedWans);
    }
    
    private void runWanStateChangeScript()
    {
        //first find the lowest id that is an active WAN
        int defaultWan = 0;
        for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
            if (wanStatusArray[i] != null && wanStatusArray[i]) {
                defaultWan = i;
                break;
            }
        }

        if (defaultWan == 0) {
            logger.warn("No active WANs found, using first WAN");
            for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
                InterfaceSettings ic = UvmContextFactory.context().networkManager().findInterfaceId( i );
                if ( ic != null && ic.getIsWan() ) {
                    defaultWan = i;
                    break;
                }
            }
        }

        if (defaultWan == 0) {
            logger.warn("No WANs found, using interface 1");
            defaultWan = 1;
        }

        String onlineWans  = "";
        String offlineWans = "";
        for (int i = 1 ; i < IntfConstants.MAX_INTF+1 ; i++) {
            if (wanStatusArray[i] != null) {
                if (wanStatusArray[i]) {
                    onlineWans += " " + i;
                } else {
                    offlineWans += " " + i;
                }
            }
        }

        onlineWans = "\"" + onlineWans + "\"";
        offlineWans = "\"" + offlineWans + "\"";
        
        try {
            logger.info("updateWanStateScript( " + defaultWan + ", " + onlineWans + ", " + offlineWans + ")");
            ExecManagerResult result = WanFailoverApp.execManager.exec( UPLINK_SCRIPT + " " + String.valueOf(defaultWan) + " " + onlineWans + " " + offlineWans);
            logger.info("updateWanStateScript( " + defaultWan + ", " + onlineWans + ", " + offlineWans + ") = " + result.getResult());

            try {
                String lines[] = result.getOutput().split("\\r?\\n");
                logger.info(UPLINK_SCRIPT + ":");
                for ( String line : lines )
                    logger.info(UPLINK_SCRIPT + ": " + line);
            } catch (Exception e) {}
        } catch (Exception e) {
            logger.warn("updateWanStateScript( " + defaultWan + ", " + onlineWans + ", " + offlineWans + ") failed", e);
        }

        if ( node.getSettings().getResetUdpOnWanStateChange() ) {
            logger.info("Reseting UDP sessions...");
            WanFailoverApp.execManager.exec( "conntrack -D --proto udp" );
        }
        
    }
}