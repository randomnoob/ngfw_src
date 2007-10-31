/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.uvm.snmp;

import java.io.File;
import java.io.FileOutputStream;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.util.TransactionWork;
import com.untangle.node.util.IOUtil;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

//TODO bscott The template for the snmpd.conf file should
//            be a Velocity template

/**
 * Implementation of the SnmpManager
 *
 */
public class SnmpManagerImpl
    implements SnmpManager {

    private static final String EOL = "\n";
    private static final String BLANK_LINE = EOL + EOL;
    private static final String TWO_LINES = BLANK_LINE + EOL;

    private static final String DEFAULT_FILE_NAME =
        "/etc/default/snmpd";
    private static final String CONF_FILE_NAME =
        "/etc/snmp/snmpd.conf";


    private static final SnmpManagerImpl s_instance =
        new SnmpManagerImpl();
    private final Logger m_logger =
        Logger.getLogger(SnmpManagerImpl.class);
    private SnmpSettings m_settings;

    private SnmpManagerImpl() {

        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    Query q = s.createQuery("from SnmpSettings");
                    m_settings = (SnmpSettings)q.uniqueResult();

                    if(m_settings == null) {
                        m_settings = new SnmpSettings();

                        m_settings.setEnabled(false);
                        m_settings.setPort(SnmpSettings.STANDARD_MSG_PORT);
                        m_settings.setCommunityString("CHANGE_ME");
                        m_settings.setSysContact("MY_CONTACT_INFO");
                        m_settings.setSysLocation("MY_LOCATION");
                        m_settings.setSendTraps(false);
                        m_settings.setTrapHost("MY_TRAP_HOST");
                        m_settings.setTrapCommunity("MY_TRAP_COMMUNITY");
                        m_settings.setTrapPort(SnmpSettings.STANDARD_TRAP_PORT);

                        s.save(m_settings);
                    }
                    return true;
                }

                public Object getResult() { return null; }
            };
        LocalUvmContextFactory.context().runTransaction(tw);

        m_logger.info("Initialized SnmpManager");
        if(!isSnmpInstalled()) {
            m_logger.warn("Snmpd is not installed");
        }
    }

    public static SnmpManagerImpl snmpManager() {
        return s_instance;
    }

    public SnmpSettings getSnmpSettings() {
        return m_settings;
    }

    public void setSnmpSettings(final SnmpSettings settings) {
        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    s.merge(settings);
                    return true;
                }

                public Object getResult() { return null; }
            };
        LocalUvmContextFactory.context().runTransaction(tw);
        m_settings = settings;

        if(!isSnmpInstalled()) {
            m_logger.warn("Snmpd is not installed");
        }
        else {
            writeDefaultCtlFile(settings);
            writeSnmpdConfFile(settings);
            restartDaemon();
        }
    }


    private void writeDefaultCtlFile(SnmpSettings settings) {

        //This is a total hack - and we should use templates


        StringBuilder snmpdCtl = new StringBuilder();
        snmpdCtl.append("export MIBDIRS=/usr/share/snmp/mibs").append(EOL);
        snmpdCtl.append("SNMPDRUN=").
            append(settings.isEnabled()?"yes":"no").
            append(EOL);
        //Note the line below also specifies the listening port
        snmpdCtl.append("SNMPDOPTS='-Lsd -Lf /dev/null -p /var/run/snmpd.pid UDP:").
            append(Integer.toString(settings.getPort())).append("'").append(EOL);
        snmpdCtl.append("TRAPDRUN=no").append(EOL);
        snmpdCtl.append("TRAPDOPTS='-Lsd -p /var/run/snmptrapd.pid'").append(EOL);

        strToFile(snmpdCtl.toString(), DEFAULT_FILE_NAME);
    }


    private void writeSnmpdConfFile(SnmpSettings settings) {

        StringBuilder snmpd_config = new StringBuilder();
        snmpd_config.append("# Turn off SMUX - recommended way from the net-snmp folks").append(EOL);
        snmpd_config.append("# is to bind to a goofy IP").append(EOL);
        snmpd_config.append("smuxsocket 1.0.0.0").append(TWO_LINES);

        if(settings.isSendTraps() &&
           isNotNullOrBlank(settings.getTrapHost()) &&
           isNotNullOrBlank(settings.getTrapCommunity())) {

            snmpd_config.append("# Enable default SNMP traps to be sent").append(EOL);
            snmpd_config.append("trapsink ").
                append(settings.getTrapHost()).append(" ").
                append(settings.getTrapCommunity()).append(" ").
                append(Integer.toString(settings.getTrapPort())).
                append(BLANK_LINE);
            snmpd_config.append("# Enable traps for failed auth (this is a security appliance)").append(EOL);
            snmpd_config.append("authtrapenable  1").append(TWO_LINES);
        }
        else {
            snmpd_config.append("# Not sending traps").append(TWO_LINES);
        }

        snmpd_config.append("# Physical system location").append(EOL);
        snmpd_config.append("syslocation").append(" ").
            append(qqOrNullToDefault(settings.getSysLocation(), "location")).append(BLANK_LINE);
        snmpd_config.append("# System contact info").append(EOL);
        snmpd_config.append("syscontact").append(" ").
            append(qqOrNullToDefault(settings.getSysContact(), "contact")).append(TWO_LINES);

        snmpd_config.append("sysservices 78").append(TWO_LINES);

	// Inject pass commands to handle UVM SNMP stats.
        snmpd_config.append("pass .1.3.6.1.4.1.2021.6971.1 /bin/sh /usr/share/untangle/bin/uvmsnmp.sh webfilter").append(EOL);
        snmpd_config.append("pass .1.3.6.1.4.1.2021.6971.2 /bin/sh /usr/share/untangle/bin/uvmsnmp.sh firewall").append(EOL);
        snmpd_config.append("pass .1.3.6.1.4.1.2021.6971.3 /bin/sh /usr/share/untangle/bin/uvmsnmp.sh attackblocker").append(EOL);
        snmpd_config.append("pass .1.3.6.1.4.1.2021.6971.4 /bin/sh /usr/share/untangle/bin/uvmsnmp.sh protofilter").append(EOL);
        snmpd_config.append(TWO_LINES);

        if(isNotNullOrBlank(settings.getCommunityString())) {
            snmpd_config.append("# Simple access rules, so there is only one read").append(EOL);
            snmpd_config.append("# only connumity.").append(EOL);
            snmpd_config.append("com2sec local default ").append(settings.getCommunityString()).append(EOL);
            snmpd_config.append("group MyROGroup v1 local").append(EOL);
            snmpd_config.append("group MyROGroup v2c local").append(EOL);
            snmpd_config.append("group MyROGroup usm local").append(EOL);
            snmpd_config.append("view mib2 included  .iso.org.dod.internet.mgmt.mib-2").append(EOL);
	    // ***TODO: need to externalize or patch on install the UVM mib root: ...1.2021.6971
	    snmpd_config.append("view mib2 included  .iso.org.dod.internet.private.1.2021.6971").append(EOL);
            snmpd_config.append("access MyROGroup \"\" any noauth exact mib2 none none").append(EOL);
        }
        else {
            snmpd_config.append("# No one has access (no community string)").append(EOL);
        }

        strToFile(snmpd_config.toString(), CONF_FILE_NAME);
    }

    private boolean strToFile(String s, String fileName) {
        FileOutputStream fos = null;
        File tmp = null;
        try {

            tmp = File.createTempFile("snmpcf", ".tmp");
            fos = new FileOutputStream(tmp);
            fos.write(s.getBytes());
            fos.flush();
            fos.close();
            IOUtil.copyFile(tmp, new File(fileName));
            tmp.delete();
            return true;
        }
        catch(Exception ex) {
            IOUtil.close(fos);
            tmp.delete();
            m_logger.error("Unable to create SNMP control file \"" +
                           fileName + "\"", ex);
            return false;
        }
    }


    /**
     * Note that if we've disabled SNMP support (and it was enabled)
     * forcing this "restart" actualy causes it to stop.  Doesn't sound
     * intuitive - but trust me.  The "etc/default/snmpd" file which we
     * write controls this.
     */
    private void restartDaemon() {
        try {
            m_logger.debug("Restarting the snmpd...");
            Process p = LocalUvmContextFactory.context().exec(new String[] {
                "/etc/init.d/snmpd", "restart"});
            p.waitFor();
            m_logger.debug("Restart of SNMPD exited with " +
                           p.exitValue());
        }
        catch(Exception ex) {
            m_logger.error("Error restarting snmpd", ex);
        }
    }

    private boolean isNotNullOrBlank(String s) {
        return s != null && !"".equals(s.trim());
    }

    private String qqOrNullToDefault(String str, String def) {
        return isNotNullOrBlank(str)?
            str:def;
    }

    private boolean isSnmpInstalled() {
        return new File("/etc/snmp").exists();
    }
}


/*
  #This turns off SMUX silliness

  smuxsocket 1.0.0.0

*/


/*

This snippit controls sending SNMP traps (v1).  Better make
sure the port is correct

###########################################################################
#
# snmpd.conf
#
#   - created by the snmpconf configuration program
#
###########################################################################
# SECTION: Trap Destinations
#
#   Here we define who the agent will send traps to.

# trapsink: A SNMPv1 trap receiver
#   arguments: host [community] [portnum]

trapsink  myV1TrapReceiveHost myV1TrapCommunity 162

# authtrapenable: Should we send traps when authentication failures occur
#   arguments: 1 | 2   (1 = yes, 2 = no)

authtrapenable  1

*/




/*

To set-up a v1 user with read-only permission who can only
view the vanilla MIB

####
# First, map the community name (COMMUNITY) into a security name
# (local and mynetwork, depending on where the request is coming
# from):

#       sec.name  source          community
com2sec local     default        MY_COMMUNITY


####
# Second, map the security names into group names:

#               sec.model  sec.name
group MyROGroup v1         local
group MyROGroup v2c        local
group MyROGroup usm        local

####
# Third, create a view for us to let the groups have rights to:

#                   incl/excl subtree                          mask
#view all            included  .1
#view snipNetSnmp    excluded  .1.3.6.1.4.8072
view mib2   included  .iso.org.dod.internet.mgmt.mib-2

####
# Finally, grant the 2 groups access to the 1 view with different
# write permissions:

#                context sec.model sec.level match  read           write  notif
access MyROGroup ""      any       noauth    exact  mib2            none   none
#access MyROGroup ""      any       noauth    exact  snipNetSnmp    none   none

*/



/*

*********** To force listening on only one of 'n' interfaces ****************

Normally, the agent will bind to the specified port on all interfaces
on the system, and accept request received from any of them.  With
version 4.2, the '-p' option can be used to listen on individual
interfaces.  For example,

snmpd -p 161@127.0.0.1

will listen (on the standard port) on the loopback interface only, and

snmpd -p 6161@10.0.0.1

will listen on port 6161, on the (internal network) interface with address
10.0.0.1.   If you want to listen on multiple interfaces (but not all),
then simply repeat this option for each one:

snmpd -p 161@127.0.0.1 -p 6161@10.0.0.1

The v5 Net-SNMP agent has a similar facility, but does not use the '-p'
command line option flag.  Instead, the ports and/or interfaces to listen
on are simply listed on the command line, following any other options.  Also,
the syntax of port and interface is slightly different (interface:port).
So the three examples above would be

snmpd 127.0.0.1:161
snmpd 127.0.0.1:6161
snmpd 127.0.0.1:161 127.0.0.1:6161

The AgentX port option ('-x') works in much the same way, using the
"host:port" syntax (in both 4.2 and 5.0 lines - and yes, this *is* an
inconsistency in 4.2!)





*/
