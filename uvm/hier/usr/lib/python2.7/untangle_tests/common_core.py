import os
import sys
import subprocess
import time
import datetime
from untangle_tests import ClientControl
from untangle_tests import SystemProperties

radiusServer = "10.111.56.71"
clientControl = ClientControl()
systemProperties = SystemProperties()

class MGEMControl:
    # http://www.nrl.navy.mil/itd/ncs/products/mgen
    
    def verifyMgen(self):
        mGenPresent = False
        externalClientResult = subprocess.call(["ping","-c","1",radiusServer],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        # print "externalClientResult <%s>" % externalClientResult
        if (externalClientResult == 0):
            # RADIUS server, the mgen endpoint is reachable, check other requirements
            mgenResult = clientControl.runCommand("test -x /usr/bin/mgen")
            # print "mgenResult <%s>" % mgenResult
            if (mgenResult == 0):
                mgnFileResult = clientControl.runCommand("ls /home/testshell/udp-load-ats.mgn")
                if (mgnFileResult != 0):
                    # mgen is present but the test control file is not, try to copy it over.
                    os.system("scp -3 -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + ":./udp-load-ats.mgn testshell@" + clientControl.hostIP + ":./ >/dev/null 2>&1")
                    mgnFileResult = clientControl.runCommand("ls /home/testshell/udp-load-ats.mgn")
                # print "mgnFileResult <%s>" % mgnFileResult
                if (mgnFileResult == 0):
                    mGenPresent = True
        return mGenPresent

    def getUDPSpeed(self):
        # Use mgen to get UDP speed.  Returns number of packets received.
        # start mgen receiver on radius server.
        os.system("rm -f mgen_recv.dat")
        os.system("ssh -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + " \"rm -f mgen_recv.dat\" >/dev/null 2>&1")
        os.system("ssh -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + " \"/home/fnsadmin/MGEN/mgen output mgen_recv.dat port 5000 >/dev/null 2>&1 &\"")
        # start the UDP generator on the client behind the Untangle.
        clientControl.runCommand("mgen input /home/testshell/udp-load-ats.mgn txlog log mgen_snd.log")
        # wait for UDP to finish
        time.sleep(70)
        # kill mgen receiver    
        os.system("ssh -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + " \"pkill mgen\"  >/dev/null 2>&1")
        os.system("scp -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + ":mgen_recv.dat ./ >/dev/null 2>&1")
        wcResults = subprocess.Popen(["wc","-l","mgen_recv.dat"], stdout=subprocess.PIPE).communicate()[0]
        # print "wcResults " + str(wcResults)
        numOfPackets = wcResults.split(' ')[0]
        return numOfPackets

    def sendUDPPackets(self):
        # Use mgen to send UDP packets.  Returns number of packets received.
        # start mgen receiver on client.
        os.system("rm -f mgen_recv.dat")
        clientControl.runCommand("rm -f mgen_recv.dat")
        clientControl.runCommand("mgen output mgen_recv.dat port 5000 &")
        # start the UDP generator on the radius server.
        os.system("ssh -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + radiusServer + " \"input /home/testshell/udp-load-ats.mgn txlog log mgen_snd.log >/dev/null 2>&1\"")
        # wait for UDP to finish
        time.sleep(70)
        # kill mgen receiver    
        clientControl.runCommand("pkill mgen")
        os.system("scp -o 'StrictHostKeyChecking=no' -i " + systemProperties.getPrefix() + "/usr/lib/python2.7/untangle_tests/testShell.key testshell@" + ClientControl.hostIP + ":mgen_recv.dat ./ >/dev/null 2>&1")
        wcResults = subprocess.Popen(["wc","-l","mgen_recv.dat"], stdout=subprocess.PIPE).communicate()[0]
        # print "wcResults " + str(wcResults)
        numOfPackets = wcResults.split(' ')[0]
        return numOfPackets
    