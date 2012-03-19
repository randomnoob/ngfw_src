/**
 * $Id$
 */
package com.untangle.uvm.engine;

import java.net.InetSocketAddress;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SyslogAppender;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.NetworkManager;
import com.untangle.uvm.logging.LogEvent;
import com.untangle.uvm.logging.LoggingSettings;
import com.untangle.uvm.logging.SyslogManager;
import com.untangle.uvm.logging.SyslogPriority;
import com.untangle.uvm.networking.NetworkConfigurationListener;
import com.untangle.uvm.networking.NetworkConfiguration;
import com.untangle.uvm.node.script.ScriptRunner;
import com.untangle.uvm.node.script.ScriptRunner.ScriptException;

/**
 * Implements SyslogManager.
 */
class SyslogManagerImpl implements SyslogManager
{
    private static final SyslogManagerImpl MANAGER = new SyslogManagerImpl();

    private static final String RSYSLOG = "/etc/init.d/rsyslog";
    private static final File CONF_FILE = new File("/etc/rsyslog.d/untangle-remote.conf");
    private static final String CONF_LINE = ":msg, regex, \"uvm\\[[0-9]*\\]:\" @";

    private final ThreadLocal<SyslogSender> syslogSenders;
    private final Logger logger = Logger.getLogger(getClass());

    private boolean isOn;

    private volatile int facility;
    private volatile SyslogPriority threshold;
    private volatile String hostname;
    private volatile int port;
    private volatile String protocol;

    private SyslogManagerImpl()
    {
        syslogSenders = new ThreadLocal<SyslogSender>();
    }

    // static factories -------------------------------------------------------

    static SyslogManagerImpl manager()
    {
        return MANAGER;
    }

    // SyslogManager methods --------------------------------------------------

    public void sendSyslog(LogEvent e, String tag)
    {
        synchronized (this) {
            if (!isOn)
                return;
        }

        SyslogSender syslogSender = syslogSenders.get();
        if (null == syslogSender) {
            syslogSender = new SyslogSender();
            syslogSenders.set(syslogSender);
        }

        syslogSender.sendSyslog(e, tag);
    }

    // package protected methods ----------------------------------------------

    void postInit()
    {
        final NetworkManager nmi = UvmContextFactory.context().networkManager();

        nmi.registerListener(new NetworkConfigurationListener() {
                public void event(NetworkConfiguration s)
                {
                    hostname = nmi.getHostname().toString();
                }
            });
        
        hostname = nmi.getHostname().toString();
    }

    void reconfigure(LoggingSettings loggingSettings)
    {
        if (loggingSettings != null && loggingSettings.isSyslogEnabled()) {
            isOn = true;
            hostname = loggingSettings.getSyslogHost();
            port = loggingSettings.getSyslogPort();
            facility = loggingSettings.getSyslogFacility().getFacilityValue();
            threshold = loggingSettings.getSyslogThreshold();
            protocol = loggingSettings.getSyslogProtocol();

            SyslogAppender sa = (SyslogAppender)logger.getAppender("EVENTS");
            sa.setFacility("LOCAL" + facility);
            sa.setThreshold(threshold.getLevel());

            // set rsylsog conf
            String conf = CONF_LINE;
            if (protocol.equalsIgnoreCase("TCP"))
                conf += "@";
            conf += hostname + ":" + port;

            // write conf file
            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new FileWriter(CONF_FILE));
                out.write(conf, 0, conf.length());
            } catch (IOException ex) {
                logger.error("Unable to write file", ex);
                return;
            }
            try {
                out.close();
            } catch (IOException ex) {
                logger.error("Unable to close file", ex);
                return;
            }
        } else {
            isOn = false;
            CONF_FILE.delete();            
        }

        // restart syslog
        try {
            ScriptRunner.getInstance().exec(RSYSLOG, "restart");
        } catch (ScriptException ex) {
            logger.error("Could not restart rsyslog", ex);
        }
    }

    // private classes --------------------------------------------------------

    private class SyslogSender
    {
        private final SyslogBuilderImpl sb = new SyslogBuilderImpl();

        // public methods -----------------------------------------------------

        public void sendSyslog(LogEvent e, String tag)
        {
            synchronized (SyslogManagerImpl.this) {
                e.appendSyslog(sb);
                logger.log(e.getSyslogPriority().getLevel(), tag + sb.getString());
            }
        }
    }
}
