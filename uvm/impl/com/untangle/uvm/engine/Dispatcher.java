/**
 * $Id$
 */
package com.untangle.uvm.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.argon.ArgonAgent;
import com.untangle.uvm.argon.ArgonIPSession;
import com.untangle.uvm.argon.ArgonUDPSession;
import com.untangle.uvm.argon.ArgonTCPSession;
import com.untangle.uvm.argon.ArgonUDPSessionImpl;
import com.untangle.uvm.argon.ArgonTCPSessionImpl;
import com.untangle.uvm.argon.ArgonTCPNewSessionRequest;
import com.untangle.uvm.argon.ArgonUDPNewSessionRequest;
import com.untangle.uvm.argon.ArgonIPNewSessionRequest;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeProperties;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.node.NodeMetric;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.util.MetaEnv;
import com.untangle.uvm.vnet.NodeBase;
import com.untangle.uvm.vnet.IPSession;
import com.untangle.uvm.vnet.VnetSessionDesc;
import com.untangle.uvm.vnet.SessionStats;
import com.untangle.uvm.vnet.TCPSession;
import com.untangle.uvm.vnet.UDPSession;
import com.untangle.uvm.vnet.event.IPDataResult;
import com.untangle.uvm.vnet.event.IPSessionEvent;
import com.untangle.uvm.vnet.event.SessionEventListener;
import com.untangle.uvm.vnet.event.TCPChunkEvent;
import com.untangle.uvm.vnet.event.TCPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.TCPSessionEvent;
import com.untangle.uvm.vnet.event.UDPErrorEvent;
import com.untangle.uvm.vnet.event.UDPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.UDPPacketEvent;
import com.untangle.uvm.vnet.event.UDPSessionEvent;

/**
 * One dispatcher per ArgonConnector.  This where all the new session logic
 * lives, and the event dispatching.
 */
class Dispatcher implements com.untangle.uvm.argon.NewSessionEventListener
{

    /**
     * 
     * Note we only send a heartbeat once we haven't communicated with
     * argonConnector in that long.  any command or new session (incoming
     * command) resets the timer.
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30000;
    public static final int DEFAULT_CHECKUP_RESPONSE_TIMEOUT = 10000;

    /**
     * 8 seconds is the longest interval we wait between each attempt
     * to try to connect to ArgonConnector.
     */
    public static final int MAX_CONNECT_BACKOFF_TIME = 8000;

    public static final int TCP_READ_BUFFER_SIZE = 8192;
    public static final int UDP_MAX_PACKET_SIZE  = 16384;
    
    /**
     * Set to true to disable use of the session and newSession thread
     * pools and run everything in the command/session dispatchers.
     */
    public static final boolean HANDLE_ALL_INLINE = true;

    static final String SESSION_ID_MDC_KEY = "SessionID";

    private Logger logger;

    private final ArgonConnectorImpl argonConnector;
    private final NodeBase node;

    private static final String STAT_LIVE_SESSIONS = "live-sessions";
    private static final String STAT_TCP_LIVE_SESSIONS = "tcp-live-sessions";
    private static final String STAT_UDP_LIVE_SESSIONS = "udp-live-sessions";
    private static final String STAT_SESSIONS     = "sessions";
    private static final String STAT_TCP_SESSIONS = "tcp-sessions";
    private static final String STAT_UDP_SESSIONS = "udp-sessions";
    private static final String STAT_SESSION_REQUESTS     = "session-requests";
    private static final String STAT_TCP_SESSION_REQUESTS = "tcp-session-requests";
    private static final String STAT_UDP_SESSION_REQUESTS = "udp-session-requests";
    
    /**
     * <code>mainThread</code> is the master thread started by
     * <code>start</code>.  It handles connecting to argonConnector, monitoring
     * of the command/session master threads for argonConnector death, and
     * reconnection.
     */
    private volatile Thread mainThread;

    private SessionEventListener sessionEventListener;

    /**
     * A specific logger for this session
     */
    private Logger sessionEventLogger;

    /**
     * We need a single global <code>releasedHandler</code> for all
     * sessions that have been release(); ed after session request
     * time.  We use the default abstract event handler implmentation
     * to become a transparent proxy.
     */
    private SessionEventListener releasedHandler;

    /**
     * The set of active sessions (both TCP and UDP), kept as weak
     * references to SessionState object (both TCP/UDP)
     */
    private ConcurrentHashMap<IPSession,IPSession> liveSessions;

    /**
     * This one is for the command socket.
     */
    private boolean lastCommandReadFailed = false;

    /**
     * dispatcher is created ArgonConnector.start() when user decides this
     * dispatcher should begin handling a newly connected ArgonConnector.
     *
     * Note that order of initialization is important in here, since
     * the "inner" classes access stuff from us.
     */
    public Dispatcher(ArgonConnectorImpl argonConnector) 
    {
        logger = Logger.getLogger(Dispatcher.class.getName());
        this.argonConnector = argonConnector;
        this.node = (NodeBase)argonConnector.node();
        sessionEventListener = null;
        NodeProperties td = node.getNodeProperties();

        sessionEventLogger = argonConnector.sessionEventLogger();
        releasedHandler = new ReleasedEventHandler(node);

        liveSessions = new ConcurrentHashMap<IPSession,IPSession>();

        this.node.addStat(new NodeMetric(STAT_LIVE_SESSIONS, I18nUtil.marktr("Current Sessions")));
        this.node.addStat(new NodeMetric(STAT_TCP_LIVE_SESSIONS, I18nUtil.marktr("Current TCP Sessions")));
        this.node.addStat(new NodeMetric(STAT_UDP_LIVE_SESSIONS, I18nUtil.marktr("Current UDP Sessions")));
        this.node.addStat(new NodeMetric(STAT_SESSIONS, I18nUtil.marktr("Sessions")));
        this.node.addStat(new NodeMetric(STAT_TCP_SESSIONS, I18nUtil.marktr("TCP Sessions")));
        this.node.addStat(new NodeMetric(STAT_UDP_SESSIONS, I18nUtil.marktr("UDP Sessions")));
        this.node.addStat(new NodeMetric(STAT_SESSION_REQUESTS, I18nUtil.marktr("Session Requests")));
        this.node.addStat(new NodeMetric(STAT_TCP_SESSION_REQUESTS, I18nUtil.marktr("TCP Session Requests")));
        this.node.addStat(new NodeMetric(STAT_UDP_SESSION_REQUESTS, I18nUtil.marktr("UDP Session Requests")));
    }

    // Called by the new session handler thread.
    void addSession(TCPSession sess)
        throws InterruptedException
    {
        this.node.incrementStat(STAT_LIVE_SESSIONS);
        this.node.incrementStat(STAT_TCP_LIVE_SESSIONS);

        this.node.incrementStat(STAT_SESSIONS);
        this.node.incrementStat(STAT_TCP_SESSIONS);
        
        liveSessions.put(sess, sess);
    }

    // Called by the new session handler thread.
    void addSession(UDPSession sess) throws InterruptedException
    {
        this.node.incrementStat(STAT_LIVE_SESSIONS);
        this.node.incrementStat(STAT_UDP_LIVE_SESSIONS);

        this.node.incrementStat(STAT_SESSIONS);
        this.node.incrementStat(STAT_UDP_SESSIONS);

        liveSessions.put(sess, sess);
    }

    // Called by IPSessionImpl at closeFinal (raze) time.
    void removeSession(IPSessionImpl sess)
    {
        liveSessions.remove(sess);
        ArgonAgent agent = (argonConnector).getArgonAgent();
        if (agent == null) {
            logger.warn("attempt to remove session " + sess.id() + " when already destroyed");
        } else {
            agent.removeSession(sess.argonSession);
        }

        this.node.decrementStat(STAT_LIVE_SESSIONS);
        if (sess instanceof UDPSession) {
            this.node.decrementStat(STAT_UDP_LIVE_SESSIONS);
        } else if (sess instanceof TCPSession) {
            this.node.decrementStat(STAT_TCP_LIVE_SESSIONS);
        }
    }

    public ArgonTCPSession newSession(ArgonTCPNewSessionRequest request)
    {
        try {
            UvmContextImpl.getInstance().loggingManager().setLoggingNode(node.getNodeSettings().getId());
            MDC.put(SESSION_ID_MDC_KEY, "NT" + request.id());
            return newSessionInternal(request);
        } finally {
            UvmContextImpl.getInstance().loggingManager().setLoggingUvm();
            MDC.remove(SESSION_ID_MDC_KEY);
        }
    }

    public ArgonUDPSession newSession(ArgonUDPNewSessionRequest request)
    {
        try {
            UvmContextImpl.getInstance().loggingManager().setLoggingNode(node.getNodeSettings().getId());
            MDC.put(SESSION_ID_MDC_KEY, "NU" + request.id());
            return newSessionInternal(request);
        } finally {
            UvmContextImpl.getInstance().loggingManager().setLoggingUvm();
            MDC.remove(SESSION_ID_MDC_KEY);
        }
    }


    // Here's the callback that Argon calls to notify of a new TCP session:
    public ArgonTCPSession newSessionInternal(ArgonTCPNewSessionRequest request)
    {
        long sessionId = -1L;

        try {
            long firstRequestHandleTime = 0, dispatchRequestTime = 0,
                requestHandledTime = 0, madeSessionTime = 0,
                dispatchNewTime = 0, newHandledTime = 0, finishNewTime = 0;
            if (RWSessionStats.DoDetailedTimes)
                firstRequestHandleTime = MetaEnv.currentTimeMillis();

            NodeProperties td = node.getNodeProperties();
            sessionId = request.id();

            TCPNewSessionRequestImpl treq = new TCPNewSessionRequestImpl(this, request);
            if (RWSessionStats.DoDetailedTimes)
                dispatchRequestTime = MetaEnv.currentTimeMillis();

            this.node.incrementStat(STAT_SESSION_REQUESTS);
            this.node.incrementStat(STAT_TCP_SESSION_REQUESTS);

            // Give the request event to the user, to give them a
            // chance to reject the session.
            logger.debug("sending TCP new session request event");
            TCPNewSessionRequestEvent revent = new TCPNewSessionRequestEvent(argonConnector, treq);
            dispatchTCPNewSessionRequest(revent);

            if (RWSessionStats.DoDetailedTimes)
                requestHandledTime = MetaEnv.currentTimeMillis();

            // Check the session only if it was not rejected.
            switch (treq.state()) {
            case ArgonIPNewSessionRequest.REJECTED:
            case ArgonIPNewSessionRequest.REJECTED_SILENT:
                if (treq.needsFinalization()) {
                    logger.debug("rejecting (with finalization)");
                } else {
                    logger.debug("rejecting");
                    return null;
                }

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case ArgonIPNewSessionRequest.RELEASED:
                boolean needsFinalization = treq.needsFinalization();
                boolean modified = treq.modified();
                if (needsFinalization)
                    logger.debug("releasing (with finalization)");
                else if (modified)
                    logger.debug("releasing (with modification)");
                else
                    logger.debug("releasing");
                if (!needsFinalization && !modified)
                    // Then we don't need to create a session at all.
                    return null;

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case ArgonIPNewSessionRequest.REQUESTED:
            case ArgonIPNewSessionRequest.ENDPOINTED:
            default:
                break;
            }

            // Create the session, client and server channels
            ArgonTCPSession argonSession = new ArgonTCPSessionImpl(request);
            TCPSessionImpl session = new TCPSessionImpl(this, argonSession, request.sessionEvent(), TCP_READ_BUFFER_SIZE, TCP_READ_BUFFER_SIZE);
            
            session.attach(treq.attachment());
            registerPipelineListener(argonSession, session);
            if (RWSessionStats.DoDetailedTimes)
                madeSessionTime = MetaEnv.currentTimeMillis();

            // Send the new session event.  
            if (logger.isInfoEnabled())
                logger.info("New TCP session " +
                            session.clientAddr().getHostAddress() + ":" + session.clientPort() + " -> " +
                            session.serverAddr().getHostAddress() + ":" + session.serverPort());
            if (RWSessionStats.DoDetailedTimes)
                dispatchNewTime = MetaEnv.currentTimeMillis();
            if (treq.state() == ArgonIPNewSessionRequest.RELEASED) {
                session.release(treq.needsFinalization());
            } else {
                TCPSessionEvent tevent = new TCPSessionEvent(argonConnector, session);
                dispatchTCPNewSession(tevent);
            }
            if (RWSessionStats.DoDetailedTimes)
                newHandledTime = MetaEnv.currentTimeMillis();

            // Finally it to our set of owned sessions.
            addSession(session);

            if (RWSessionStats.DoDetailedTimes) {
                finishNewTime = MetaEnv.currentTimeMillis();

                long[] times = session.stats().times();
                times[SessionStats.NEW_SESSION_RECEIVED] = firstRequestHandleTime;
                times[SessionStats.DISPATCH_REQUEST] = dispatchRequestTime;
                times[SessionStats.REQUEST_HANDLED] = requestHandledTime;
                times[SessionStats.MADE_SESSION] = madeSessionTime;
                times[SessionStats.DISPATCH_NEW] = dispatchNewTime;
                times[SessionStats.NEW_HANDLED] = newHandledTime;
                times[SessionStats.FINISH_NEW] = finishNewTime;
            }
            return argonSession;
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " building TCP session " + sessionId;
            logger.error(message, x);
            // This "kills" the session:
            return null;
        }
    }

    public ArgonUDPSession newSessionInternal(ArgonUDPNewSessionRequest request)
    {
        long sessionId = -1;

        try {
            long firstRequestHandleTime = 0, dispatchRequestTime = 0,
                requestHandledTime = 0, madeSessionTime = 0,
                dispatchNewTime = 0, newHandledTime = 0, finishNewTime = 0;
            if (RWSessionStats.DoDetailedTimes)
                firstRequestHandleTime = MetaEnv.currentTimeMillis();

            NodeProperties td = node.getNodeProperties();
            sessionId = request.id();

            UDPNewSessionRequestImpl ureq = new UDPNewSessionRequestImpl(this, request);
            if (RWSessionStats.DoDetailedTimes)
                dispatchRequestTime = MetaEnv.currentTimeMillis();

            this.node.incrementStat(STAT_SESSION_REQUESTS);
            this.node.incrementStat(STAT_UDP_SESSION_REQUESTS);

            // Give the request event to the user, to give them a chance to reject the session.
            logger.debug("sending UDP new session request event");
            UDPNewSessionRequestEvent revent = new UDPNewSessionRequestEvent(argonConnector, ureq);
            dispatchUDPNewSessionRequest(revent);

            if (RWSessionStats.DoDetailedTimes)
                requestHandledTime = MetaEnv.currentTimeMillis();

            // Check the session only if it was not rejected.
            switch (ureq.state()) {
            case ArgonIPNewSessionRequest.REJECTED:
            case ArgonIPNewSessionRequest.REJECTED_SILENT:
                if (ureq.needsFinalization()) {
                    logger.debug("rejecting (with finalization)");
                } else {
                    logger.debug("rejecting");
                    return null;
                }

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case ArgonIPNewSessionRequest.RELEASED:
                boolean needsFinalization = ureq.needsFinalization();
                boolean modified = ureq.modified();
                if (needsFinalization)
                    logger.debug("releasing (with finalization)");
                else if (modified)
                    logger.debug("releasing (with modification)");
                else
                    logger.debug("releasing");
                if (!needsFinalization && !modified)
                    // Then we don't need to create a session at all.
                    return null;

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case ArgonIPNewSessionRequest.REQUESTED:
            case ArgonIPNewSessionRequest.ENDPOINTED:
            default:
                break;
            }

            // Create the session, client and server channels
            ArgonUDPSession argonSession = new ArgonUDPSessionImpl(request);
            UDPSessionImpl session = new UDPSessionImpl(this, argonSession, request.sessionEvent(), UDP_MAX_PACKET_SIZE, UDP_MAX_PACKET_SIZE);
            
            session.attach(ureq.attachment());
            registerPipelineListener(argonSession, session);
            if (RWSessionStats.DoDetailedTimes)
                madeSessionTime = MetaEnv.currentTimeMillis();

            // Send the new session event.  Maybe this should be done on the session handler
            // thread instead?  XX
            if (logger.isInfoEnabled())
                logger.info("New UDP session " +
                            session.clientAddr().getHostAddress() + ":" + session.clientPort() + " -> " +
                            session.serverAddr().getHostAddress() + ":" + session.serverPort());
            if (RWSessionStats.DoDetailedTimes)
                dispatchNewTime = MetaEnv.currentTimeMillis();
            if (ureq.state() == ArgonIPNewSessionRequest.RELEASED) {
                session.release(ureq.needsFinalization());
            } else {
                UDPSessionEvent tevent = new UDPSessionEvent(argonConnector, session);
                dispatchUDPNewSession(tevent);
            }
            if (RWSessionStats.DoDetailedTimes)
                newHandledTime = MetaEnv.currentTimeMillis();

            // Finally add it to our set of owned sessions.
            addSession(session);

            if (RWSessionStats.DoDetailedTimes) {
                finishNewTime = MetaEnv.currentTimeMillis();

                long[] times = session.stats().times();
                times[SessionStats.NEW_SESSION_RECEIVED] = firstRequestHandleTime;
                times[SessionStats.DISPATCH_REQUEST] = dispatchRequestTime;
                times[SessionStats.REQUEST_HANDLED] = requestHandledTime;
                times[SessionStats.MADE_SESSION] = madeSessionTime;
                times[SessionStats.DISPATCH_NEW] = dispatchNewTime;
                times[SessionStats.NEW_HANDLED] = newHandledTime;
                times[SessionStats.FINISH_NEW] = finishNewTime;
            }
            return argonSession;
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " building UDP session " + sessionId;
            logger.error(message, x);
            // This "kills" the session:
            return null;
        }
    }

    void registerPipelineListener(ArgonIPSession argonSession, IPSessionImpl session)
    {
        argonSession.registerListener(session);
    }

    /**
     * Describe <code>start</code> method here.
     *
     * By the time we're called, the load request has been sent, but the response has not been read.
     *
     * @exception Exception if an error occurs
     */
    void start() 
    {
    }

    /**
     * Called from ArgonConnectorImpl.  Stop is only called to disconnect us
     * from a live ArgonConnector.  Once stopped we cannot be restarted.  This
     * function is idempotent for safety.
     *
     * When used in drain mode, this function will not return until
     * all sessions have naturally finished.  When not in drain mode,
     * all existing sessions are forcibly terminated (by closing
     * connection to ArgonConnector == close server & client sockets outside of
     * VP).  In both modes, when this function returns we guarantee:
     * No sessions are alive.  No DEM threads are alive.  No session
     * threads, new session threads, or any of the three main threads
     * are alive.
     *
     * @param drainMode a <code>boolean</code> true if we should
     * switch into drain-then-exit mode, false if we should
     * immediately exit.
     */
    void destroy(boolean drainMode) throws InterruptedException
    {
        logger.info("destroy called");
        if (mainThread != null) {
            Thread oldMain = mainThread;
            mainThread = null;
            oldMain.interrupt();

            if (drainMode)
                logger.error("Drain mode not yet supported");

            // The main thread exitting handles all the cleanup.  We just wait for it to finish.
            try {
                oldMain.join(10000); // Need constant XXX
            } catch (InterruptedException x) {
                // Can't really happen
                logger.error("Dispatcher.destroy() interrupted waiting for mainThread to die");
                // Resend it back out
                Thread.currentThread().interrupt();
            }

            if (oldMain.isAlive())
                logger.error("Dispatcher didn't die");
        }
    }

    ArgonConnectorImpl argonConnector()
    {
        return argonConnector;
    }

    boolean lastReadFailed()
    {
        // Note that we do *not* consider session reads a failure.  This is a result
        // of how we currently handle session shutdown.
        return this.lastCommandReadFailed;
    }

    /**
     * Called whenever a command socket read occurs.
     */
    public void lastCommandNumRead(int numRead)
    {
        if (numRead > 0) {
            lastCommandReadFailed = false;
        } else {
            lastCommandReadFailed = true;
        }
    }

    public void setSessionEventListener(SessionEventListener listener)
    {
        sessionEventListener = listener;
    }

    public long[] liveSessionIds()
    {
        int count = 0;
        int size = liveSessions.size();
        long[] idlist = new long[size];
        
        for (Iterator<IPSession> i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            if (count == size) /* just in case */
                break;
            IPSession sess = i.next();
            idlist[count] = sess.id();
            count++;
        }

        return idlist;
    }

    List<VnetSessionDesc> liveSessionDescs()
    {
        List<VnetSessionDesc> l = new ArrayList<VnetSessionDesc>(liveSessions.size());
        for (Iterator<IPSession> i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            IPSessionImpl sess = (IPSessionImpl)i.next();
            l.add(sess.makeDesc());
        }

        return l;
    }

    List<IPSession> liveSessions()
    {
        LinkedList<IPSession> sessions = new LinkedList<IPSession>();
        
        for (Iterator<IPSession> i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            sessions.add(i.next());
        }

        return sessions;
    }

    //////////////////////////////////////////////////////////////////

    void dispatchTCPNewSessionRequest(TCPNewSessionRequestEvent event)
        
    {
        elog(Level.DEBUG, "TCPNewSessionRequest", event.sessionRequest().id());
        if (sessionEventListener == null) {
            releasedHandler.handleTCPNewSessionRequest(event);
        } else {
            long startTime = 0;

            sessionEventListener.handleTCPNewSessionRequest(event);
        }
    }

    void dispatchUDPNewSessionRequest(UDPNewSessionRequestEvent event)
        
    {
        elog(Level.DEBUG, "UDPNewSessionRequest", event.sessionRequest().id());
        if (sessionEventListener == null) {
            releasedHandler.handleUDPNewSessionRequest(event);
        } else {
            long startTime = 0;

            sessionEventListener.handleUDPNewSessionRequest(event);
        }
    }

    void dispatchTCPNewSession(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPNewSession", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPNewSession(event);
        else
            sessionEventListener.handleTCPNewSession(event);
    }

    void dispatchUDPNewSession(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPNewSession", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPNewSession(event);
        else
            sessionEventListener.handleUDPNewSession(event);
    }

    IPDataResult dispatchTCPClientChunk(TCPChunkEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientChunk", session.id(), event.chunk().remaining());
        if (sessionEventListener == null || session.released()) {
            return releasedHandler.handleTCPClientChunk(event);
        } else {
            long startTime = 0;

            IPDataResult result = sessionEventListener.handleTCPClientChunk(event);

            return result;
        }
    }

    IPDataResult dispatchTCPServerChunk(TCPChunkEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerChunk", session.id(), event.chunk().remaining());
        if (sessionEventListener == null || session.released()) {
            return releasedHandler.handleTCPServerChunk(event);
        } else {
            long startTime = 0;

            IPDataResult result = sessionEventListener.handleTCPServerChunk(event);
            
            return result;
        }
    }

    IPDataResult dispatchTCPClientWritable(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientWritable", session.id());
        if (sessionEventListener == null || session.released()) {
            return releasedHandler.handleTCPClientWritable(event);
        } else {
            long startTime = 0;
            
            IPDataResult result = sessionEventListener.handleTCPClientWritable(event);
            
            return result;
        }
    }

    IPDataResult dispatchTCPServerWritable(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerWritable", session.id());
        if (sessionEventListener == null || session.released()) {
            return releasedHandler.handleTCPServerWritable(event);
        } else {
            long startTime = 0;
            
            IPDataResult result = sessionEventListener.handleTCPServerWritable(event);

            return result;
        }
    }

    void dispatchUDPClientPacket(UDPPacketEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientPacket", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released()) {
            releasedHandler.handleUDPClientPacket(event);
        } else {
            long startTime = 0;
            
            sessionEventListener.handleUDPClientPacket(event);

        }
    }

    void dispatchUDPServerPacket(UDPPacketEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerPacket", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released()) {
            releasedHandler.handleUDPServerPacket(event);
        } else {
            long startTime = 0;
            
            sessionEventListener.handleUDPServerPacket(event);

        }
    }

    IPDataResult dispatchTCPClientDataEnd(TCPChunkEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientDataEnd", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPClientDataEnd(event);
        else
            return sessionEventListener.handleTCPClientDataEnd(event);
    }

    void dispatchTCPClientFIN(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientFIN", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPClientFIN(event);
        else
            sessionEventListener.handleTCPClientFIN(event);
    }

    IPDataResult dispatchTCPServerDataEnd(TCPChunkEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerDataEnd", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPServerDataEnd(event);
        else
            return sessionEventListener.handleTCPServerDataEnd(event);
    }

    void dispatchTCPServerFIN(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerFIN", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPServerFIN(event);
        else
            sessionEventListener.handleTCPServerFIN(event);
    }

    void dispatchTCPClientRST(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientRST", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPClientRST(event);
        else
            sessionEventListener.handleTCPClientRST(event);
    }

    void dispatchTCPServerRST(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerRST", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPServerRST(event);
        else
            sessionEventListener.handleTCPServerRST(event);
    }

    void dispatchTCPFinalized(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPFinalized", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleTCPFinalized(event);
        else
            sessionEventListener.handleTCPFinalized(event);
    }

    void dispatchTCPComplete(TCPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPComplete", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleTCPComplete(event);
        else
            sessionEventListener.handleTCPComplete(event);
    }

    void dispatchUDPClientExpired(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientExpired", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientExpired(event);
        else
            sessionEventListener.handleUDPClientExpired(event);
    }

    void dispatchUDPServerExpired(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerExpired", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerExpired(event);
        else
            sessionEventListener.handleUDPServerExpired(event);
    }

    void dispatchUDPClientWritable(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientWritable", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientWritable(event);
        else
            sessionEventListener.handleUDPClientWritable(event);
    }

    void dispatchUDPServerWritable(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerWritable", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerWritable(event);
        else
            sessionEventListener.handleUDPServerWritable(event);
    }

    void dispatchUDPFinalized(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPFinalized", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleUDPFinalized(event);
        else
            sessionEventListener.handleUDPFinalized(event);
    }

    void dispatchUDPComplete(UDPSessionEvent event)
        
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPComplete", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleUDPComplete(event);
        else
            sessionEventListener.handleUDPComplete(event);
    }

    void dispatchTimer(IPSessionEvent event)
    {
        IPSessionImpl session = (IPSessionImpl) event.ipsession();
        elog(Level.DEBUG, "Timer", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTimer(event);
        else
            sessionEventListener.handleTimer(event);
    }

    private void elog(Level level, String eventName, long sessionId)
    {
        if (sessionEventLogger.isEnabledFor(level)) {
            StringBuilder message = new StringBuilder("EV[");
            message.append(sessionId);
            message.append(",");
            message.append(eventName);
            message.append("] T: ");
            message.append(Thread.currentThread().getName());

            sessionEventLogger.log(level, message.toString());
        }
    }

    private void elog(Level level, String eventName, long sessionId, long dataSize)
    {
        if (sessionEventLogger.isEnabledFor(level)) {
            StringBuilder message = new StringBuilder("EV[");
            message.append(sessionId);
            message.append(",");
            message.append(eventName);
            message.append("] S: ");
            message.append(dataSize);
            message.append(" T: ");
            message.append(Thread.currentThread().getName());

            sessionEventLogger.log(level, message.toString());
        }
    }
    
}
