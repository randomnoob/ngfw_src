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

package com.metavize.tran.clamphish;

import com.metavize.mvvm.tapi.*;
import com.metavize.tran.token.TokenHandler;
import com.metavize.tran.token.TokenHandlerFactory;
import org.apache.log4j.Logger;

import com.metavize.tran.mail.papi.*;
import com.metavize.tran.mail.papi.smtp.*;
import com.metavize.tran.mail.papi.smtp.sapi.Session;
import com.metavize.tran.mail.papi.smtp.sapi.SimpleSessionHandler;
import com.metavize.tran.mail.*;
import com.metavize.tran.spam.SpamSMTPConfig;
import com.metavize.tran.util.Template;
import static com.metavize.tran.util.Ascii.*;
import com.metavize.mvvm.MailSender;
import com.metavize.mvvm.MvvmContextFactory;


public class PhishSmtpFactory
  implements TokenHandlerFactory {

  //==================================
  // Note that there are a lot
  // of literals burned-into the
  // code.  In a later version,
  // these should be moved to
  // user-controled params in the
  // database
  //==================================

  //TODO bscott These should be paramaterized in the Config objects

    public static final String OUT_MOD_SUB_TEMPLATE =
      "[PHISH] $MIMEMessage:SUBJECT$";
    public static final String OUT_MOD_BODY_TEMPLATE =
      "The attached message from $MIMEMessage:FROM$ was determined\r\n " +
      "to be PHISH.  The details of the report are as follows:\r\n\r\n" +
      "$SPAMReport:FULL$";
    public static final String OUT_MOD_BODY_SMTP_TEMPLATE =
      "The attached message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$) was determined\r\n " +
      "to be PHISH.  The details of the report are as follows:\r\n\r\n" +
      "$SPAMReport:FULL$";

    public static final String IN_MOD_SUB_TEMPLATE = OUT_MOD_SUB_TEMPLATE;
    public static final String IN_MOD_BODY_TEMPLATE = OUT_MOD_BODY_TEMPLATE;
    public static final String IN_MOD_BODY_SMTP_TEMPLATE = OUT_MOD_BODY_SMTP_TEMPLATE;

  private static final String OUT_NOTIFY_SUB_TEMPLATE =
    "[PHISH NOTIFICATION] re: $MIMEMessage:SUBJECT$";
    
  private static final String OUT_NOTIFY_BODY_TEMPLATE =
    "On $MIMEHeader:DATE$ a message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$) was received " + CRLF +
    "and determined to be PHISH.  The details of the report are as follows:" + CRLF + CRLF +
    "$SPAMReport:FULL$";

  private static final String IN_NOTIFY_SUB_TEMPLATE = OUT_NOTIFY_SUB_TEMPLATE;
  private static final String IN_NOTIFY_BODY_TEMPLATE = OUT_NOTIFY_BODY_TEMPLATE;
  
  private MailExport m_mailExport;
  private ClamPhishTransform m_phishImpl;
  private static final Logger m_logger =
    Logger.getLogger(PhishSmtpFactory.class);

  private WrappedMessageGenerator m_inWrapper =
    new WrappedMessageGenerator(IN_MOD_SUB_TEMPLATE, IN_MOD_BODY_SMTP_TEMPLATE);

  private WrappedMessageGenerator m_outWrapper =
    new WrappedMessageGenerator(OUT_MOD_SUB_TEMPLATE, OUT_MOD_BODY_SMTP_TEMPLATE);

  private SmtpNotifyMessageGenerator m_inNotifier;
  private SmtpNotifyMessageGenerator m_outNotifier;

  public PhishSmtpFactory(ClamPhishTransform impl) {
    m_mailExport = MailExportFactory.getExport();
    m_phishImpl = impl;
    MailSender mailSender = MvvmContextFactory.context().mailSender();
    
    m_inNotifier = new SmtpNotifyMessageGenerator(
      IN_NOTIFY_SUB_TEMPLATE,
      IN_NOTIFY_BODY_TEMPLATE,
      false,
      mailSender);

    m_outNotifier = new SmtpNotifyMessageGenerator(
      OUT_NOTIFY_SUB_TEMPLATE,
      OUT_NOTIFY_BODY_TEMPLATE,
      false,
      mailSender);      
  }


  public TokenHandler tokenHandler(TCPSession session) {

    boolean inbound = session.direction() == IPSessionDesc.INBOUND;

    SpamSMTPConfig spamConfig = inbound?
      m_phishImpl.getSpamSettings().getSMTPInbound():
      m_phishImpl.getSpamSettings().getSMTPOutbound();

    if(!spamConfig.getScan()) {
      m_logger.debug("Scanning disabled.  Return passthrough token handler");
      return Session.createPassthruSession(session);
    }

    WrappedMessageGenerator msgWrapper =
      inbound?m_inWrapper:m_outWrapper;

    SmtpNotifyMessageGenerator notifier =
      inbound?m_inNotifier:m_outNotifier;

    MailTransformSettings casingSettings = m_mailExport.getExportSettings();
    return new Session(session,
      new PhishSmtpHandler(session,
        inbound?casingSettings.getSmtpInboundTimeout():casingSettings.getSmtpOutboundTimeout(),
        inbound?casingSettings.getSmtpInboundTimeout():casingSettings.getSmtpOutboundTimeout(),
        m_phishImpl,
        spamConfig,
        msgWrapper,
        notifier));

  }
}
