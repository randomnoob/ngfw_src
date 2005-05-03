/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.ftp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.metavize.tran.token.ParseException;
import com.metavize.tran.token.Token;
import com.metavize.tran.util.AsciiCharBuffer;

/**
 * FTP server reply to a command.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 * @see "RFC 959, Section 4.2"
 */
public class FtpReply implements Token
{
    private final int replyCode;
    private final String message;

    public FtpReply(int replyCode, String message)
    {
        this.replyCode = replyCode;
        this.message = message;
    }

    // static factories ------------------------------------------------------

    public static FtpReply pasvReply(InetSocketAddress socketAddress)
    {
        String msg = "Entering Passive Mode ("
            + FtpUtil.unparsePort(socketAddress) + ").";

        return new FtpReply(227, msg);
    }

    // business methods ------------------------------------------------------

    public InetSocketAddress getSocketAddress() throws ParseException
    {
        if (227 == replyCode) {
            int b = message.indexOf('(');
            int e = message.indexOf(')', b);
            String addrStr = message.substring(b + 1, e);
            return FtpUtil.parsePort(addrStr);
        } else {
            return null;
        }
    }

    // bean methods -----------------------------------------------------------

    public int getReplyCode()
    {
        return replyCode;
    }

    public String getMessage()
    {
        return message;
    }

    // Token methods ---------------------------------------------------------

    /**
     * Includes final CRLF.
     *
     * @return the ftp reply in a ByteBuffer.
     */
    public ByteBuffer getBytes()
    {
        return ByteBuffer.wrap(message.getBytes());
    }

    // Object methods ---------------------------------------------------------

    public String toString()
    {
        return AsciiCharBuffer.wrap(getBytes()).toString();
    }
}
