/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.token;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.AbstractEventHandler;
import com.metavize.mvvm.tapi.MPipeException;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.Session;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.tapi.event.IPDataResult;
import com.metavize.mvvm.tapi.event.IPSessionEvent;
import com.metavize.mvvm.tapi.event.TCPChunkEvent;
import com.metavize.mvvm.tapi.event.TCPChunkResult;
import com.metavize.mvvm.tapi.event.TCPSessionEvent;
import com.metavize.mvvm.tapi.event.TCPStreamer;
import com.metavize.mvvm.tran.MutateTStats;
import com.metavize.mvvm.tran.Transform;
import org.apache.log4j.Logger;

public class CasingAdaptor extends AbstractEventHandler
{
    static final int TOKEN_SIZE = 8;

    private final CasingFactory casingFactory;
    private final boolean clientSide;

    private final Map casings = new ConcurrentHashMap();

    private final PipelineFoundry pipeFoundry = MvvmContextFactory.context()
        .pipelineFoundry();
    private final Logger logger = Logger.getLogger(CasingAdaptor.class);

    private volatile boolean releaseParseExceptions;

    public CasingAdaptor(Transform transform, CasingFactory casingFactory,
                         boolean clientSide, boolean releaseParseExceptions)
    {
        super(transform);
        this.casingFactory = casingFactory;
        this.clientSide = clientSide;
        this.releaseParseExceptions = releaseParseExceptions;
    }

    // accessors --------------------------------------------------------------

    public boolean getReleaseParseExceptions()
    {
        return releaseParseExceptions;
    }

    public void setReleaseParseExceptions(boolean releaseParseExceptions)
    {
        this.releaseParseExceptions = releaseParseExceptions;
    }

    // SessionEventListener methods -------------------------------------------

    @Override
    public void handleTCPNewSession(TCPSessionEvent e)
    {
        TCPSession session = e.session();

        Casing casing = casingFactory.casing(session, clientSide);
        Pipeline pipeline = pipeFoundry.getPipeline(session.id());

        if (logger.isDebugEnabled()) {
            logger.debug("new session setting: " + pipeline
                         + " for: " + session.id());
        }

        addCasing(session, casing, pipeline);

        Parser parser = casing.parser();
        Unparser unparser = casing.unparser();

        if (clientSide) {
            session.serverReadLimit(TOKEN_SIZE);
        } else {
            session.clientReadLimit(TOKEN_SIZE);
        }
    }

    @Override
    public IPDataResult handleTCPClientChunk(TCPChunkEvent e)
    {
        boolean inbound = e.session().isInbound();

        if (logger.isDebugEnabled()) {
            logger.debug("handling client chunk, session: " + e.session().id()
                         + "client inbound: " + inbound);
        }

        if (clientSide) {
            return parse(e, false, false);
        } else {
            return unparse(e, false);
        }
    }

    @Override
    public IPDataResult handleTCPServerChunk(TCPChunkEvent e)
    {
        boolean inbound = e.session().isInbound();

        if (logger.isDebugEnabled()) {
            logger.debug("handling server chunk, session: " + e.session().id()
                         + "server inbound: " + inbound);
        }

        if (clientSide) {
            return unparse(e, true);
        } else {
            return parse(e, true, false);
        }
    }

    @Override
    public IPDataResult handleTCPClientDataEnd(TCPChunkEvent e)
    {
        boolean inbound = e.session().isInbound();

        if (logger.isDebugEnabled()) {
            logger.debug("handling client chunk, session: " + e.session().id()
                         + "client inbound: " + inbound);
        }

        if (clientSide) {
            return parse(e, false, true);
        } else {
            if (e.chunk().hasRemaining()) {
                logger.warn("should not happen: unparse TCPClientDataEnd");
            }
            return null;
        }
    }

    @Override
    public IPDataResult handleTCPServerDataEnd(TCPChunkEvent e)
    {
        boolean inbound = e.session().isInbound();

        if (logger.isDebugEnabled()) {
            logger.debug("handling server chunk, session: " + e.session().id()
                         + "server inbound: " + inbound);
        }

        if (clientSide) {
            if (e.chunk().hasRemaining()) {
                logger.warn("should not happen: unparse TCPClientDataEnd");
            }
            return null;
        } else {
            return parse(e, true, true);
        }
    }

    @Override
    public void handleTCPClientFIN(TCPSessionEvent e)
    {
        TCPStreamer tcpStream = null;

        TCPSession s = (TCPSession)e.ipsession();
        Casing c = getCasing(s);

        if (clientSide) {
            TokenStreamer tokSt = c.parser().endSession();
            if (null != tokSt) {
                tcpStream = new TokenStreamerAdaptor(getPipeline(s), tokSt);
            }
        } else {
            tcpStream = c.unparser().endSession();
        }

        if (null != tcpStream) {
            s.beginServerStream(tcpStream);
        } else {
            s.shutdownServer();
        }
    }

    @Override
    public void handleTCPServerFIN(TCPSessionEvent e)
    {
        TCPStreamer ts = null;

        TCPSession s = (TCPSession)e.ipsession();
        Casing c = getCasing(s);

        if (clientSide) {
            ts = c.unparser().endSession();
        } else {
            TokenStreamer tokSt = c.parser().endSession();
            if (null != tokSt) {
                ts = new TokenStreamerAdaptor(getPipeline(s), tokSt);
            }
        }

        if (null != ts) {
            s.beginClientStream(ts);
        } else {
            s.shutdownClient();
        }
    }

    @Override
    public void handleTCPFinalized(TCPSessionEvent e) throws MPipeException
    {
        if (logger.isDebugEnabled()) {
            logger.debug("finalizing " + e.session().id());
        }
        Casing c = getCasing((TCPSession)e.ipsession());
        c.parser().handleFinalized();
        c.unparser().handleFinalized();
        removeCasingDesc(e.session());
    }

    @Override
    public void handleTimer(IPSessionEvent e)
    {
        TCPSession s = (TCPSession)e.ipsession();

        Parser p = getCasing(s).parser();
        p.handleTimer();
        // XXX unparser doesnt get one, does it need it?
    }

    // CasingDesc utils -------------------------------------------------------

    private static class CasingDesc
    {
        final Casing casing;
        final Pipeline pipeline;

        CasingDesc(Casing casing, Pipeline pipeline)
        {
            this.casing = casing;
            this.pipeline = pipeline;
        }
    }

    private void addCasing(Session session, Casing casing, Pipeline pipeline)
    {
        casings.put(session, new CasingDesc(casing, pipeline));
    }

    private CasingDesc getCasingDesc(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc;
    }

    private Casing getCasing(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc.casing;
    }

    private Pipeline getPipeline(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc.pipeline;
    }

    private void removeCasingDesc(Session session)
    {
        casings.remove(session);
    }

    // private methods --------------------------------------------------------

    private IPDataResult unparse(TCPChunkEvent e, boolean s2c)
    {
        ByteBuffer b = e.chunk();

        assert b.remaining() <= TOKEN_SIZE;

        if (b.remaining() < TOKEN_SIZE) {
            // read limit 2
            b.compact();
            b.limit(TOKEN_SIZE);
            if (logger.isDebugEnabled()) {
                logger.debug("unparse returning buffer, for more: " + b);
            }
            return new TCPChunkResult(null, null, b);
        }

        TCPSession s = e.session();
        CasingDesc casingDesc = getCasingDesc(s);
        Casing casing = casingDesc.casing;
        Pipeline pipeline = casingDesc.pipeline;

        Long key = new Long(b.getLong());
        Token tok = (Token)pipeline.detach(key);

        int d = s2c ? MutateTStats.SERVER_TO_CLIENT
            : MutateTStats.CLIENT_TO_SERVER;
        try {
            MutateTStats.rereadData(d, s, tok.getEstimatedSize() - TOKEN_SIZE);
        } catch (Exception exn) {
            logger.warn("could not estimated size", exn);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("RETRIEVED object: " + tok + " with key: " + key
                         + " on pipeline: " + pipeline);
        }

        b.limit(TOKEN_SIZE);

        assert !b.hasRemaining();

        UnparseResult ur;
        try {
            ur = unparseToken(s, casing, tok);
        } catch (Exception exn) { /* not just UnparseException */
            logger.error("internal error, closing connection", exn);
            if (s2c) {
                // XXX We don't have a good handle on this
                s.resetClient();
                s.resetServer();
            } else {
                // XXX We don't have a good handle on this
                s.shutdownServer();
                s.resetClient();
            }
            logger.debug("returning DO_NOT_PASS");

            return IPDataResult.DO_NOT_PASS;
        }

        if (ur.isStreamer()) {
            TCPStreamer ts = ur.getTcpStreamer();
            if (s2c) {
                s.beginClientStream(ts);
            } else {
                s.beginServerStream(ts);
            }
            return new TCPChunkResult(null, null, null);
        } else {
            if (s2c) {
                logger.debug("unparse result to client");
                ByteBuffer[] r = ur.result();
                if (logger.isDebugEnabled()) {
                    for (int i = 0; null != null && i < r.length; i++) {
                        logger.debug("  to client: " + r[i]);
                    }
                }
                return new TCPChunkResult(r, null, null);
            } else {
                logger.debug("unparse result to server");
                ByteBuffer[] r = ur.result();
                if (logger.isDebugEnabled()) {
                    for (int i = 0; null != r && i < r.length; i++) {
                        logger.debug("  to server: " + r[i]);
                    }
                }
                return new TCPChunkResult(null, r, null);
            }
        }
    }

    private UnparseResult unparseToken(TCPSession s, Casing c, Token token)
        throws UnparseException
    {
        Unparser u = c.unparser();

        if (token instanceof Release) {
            Release release = (Release)token;

            s.release();
            UnparseResult ur = u.releaseFlush();
            if (ur.isStreamer()) {
                TCPStreamer ts = new ReleaseTcpStreamer
                    (ur.getTcpStreamer(), release);
                    return new UnparseResult(ts);
            } else {
                ByteBuffer[] orig = ur.result();
                ByteBuffer[] r = new ByteBuffer[orig.length + 1];
                System.arraycopy(orig, 0, r, 0, orig.length);
                r[r.length - 1] = release.getBytes();
                return new UnparseResult(r);
            }
        } else {
            return u.unparse(token);
        }
    }

    private IPDataResult parse(TCPChunkEvent e, boolean s2c, boolean last)
    {
        TCPSession s = e.session();
        CasingDesc casingDesc = getCasingDesc(s);
        Casing casing = casingDesc.casing;
        Pipeline pipeline = casingDesc.pipeline;

        ParseResult pr;
        ByteBuffer buf = e.chunk();
        ByteBuffer dup = buf.duplicate();
        Parser p = casing.parser();
        try {
            if (last) {
                pr = p.parseEnd(buf);
            } else {
                pr = p.parse(buf);
            }
        } catch (Exception exn) {
            if (releaseParseExceptions) {
                String sessionEndpoints = "Endpoints ["
                    + " protocol: " + s.protocol()
                    + " clientIntf: " + s.clientIntf()
                    + " clientAddr: " + s.clientAddr()
                    + " clientPort: " + s.clientPort()
                    + " serverIntf: " + s.serverIntf()
                    + " serverAddr: " + s.serverAddr()
                    + " serverPort: " + s.serverPort() + "]";

                // XXX make configurable
                logger.warn("parse exception, releasing session. "
                            + sessionEndpoints , exn);
                s.release();
                pr = new ParseResult(new Release(dup));
            } else {
                s.shutdownServer();
                s.shutdownClient();
                return IPDataResult.DO_NOT_PASS;
            }
        }

        int direction = s2c ? MutateTStats.SERVER_TO_CLIENT
            : MutateTStats.CLIENT_TO_SERVER;

        if (pr.isStreamer()) {
            TokenStreamer tokSt
                = new TokenStreamerWrapper(pr.getTokenStreamer(), s,
                                           direction);
            TCPStreamer ts = new TokenStreamerAdaptor(pipeline, tokSt);
            if (s2c) {
                s.beginClientStream(ts);
            } else {
                s.beginServerStream(ts);
            }
            return new TCPChunkResult(null, null, pr.getReadBuffer());
        } else {
            List<Token> results = pr.getResults();

            // XXX add magic:
            ByteBuffer bb = ByteBuffer.allocate(TOKEN_SIZE * results.size());

            // XXX add magic:
            for (Token t : results) {
                try {
                    MutateTStats.rewroteData(direction, s,
                                             t.getEstimatedSize() - TOKEN_SIZE);
                } catch (Exception exn) {
                    logger.error("could not estimate size", exn);
                }

                Long key = pipeline.attach(t);
                if (logger.isDebugEnabled()) {
                    logger.debug("SAVED object: " + t + " with key: " + key
                                 + " on pipeline: " + pipeline);
                }
                bb.putLong(key);
            }
            bb.flip();

            ByteBuffer[] r = new ByteBuffer[] { bb };

            if (s2c) {
                if (logger.isDebugEnabled()) {
                    logger.debug("parse result to server, read buffer: "
                                 + pr.getReadBuffer()
                                 + "  to client: " + r[0]);
                }
                return new TCPChunkResult(r, null, pr.getReadBuffer());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("parse result to client, read buffer: "
                                 + pr.getReadBuffer()
                                 + "  to server: " + r[0]);
                }
                return new TCPChunkResult(null, r, pr.getReadBuffer());
            }
        }
    }
}
