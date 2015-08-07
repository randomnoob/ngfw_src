/*
 * $Id$
 */
package com.untangle.uvm.reports.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.untangle.node.reporting.ReportingNodeImpl;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.servlet.DownloadHandler;

/**
 * Gets CSV for a detail report.
 */
@SuppressWarnings("serial")
public class CsvServlet extends HttpServlet
{
    private final Logger logger = Logger.getLogger(getClass());

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        String downloadType = req.getParameter("type");
        if(!ReportingNodeImpl.REPORTS_EVENT_LOG_DOWNLOAD_HANDLER.equals(downloadType) && !"eventLogExport".equals(downloadType)  && !"imageDownload".equals(downloadType)) {
            logger.error("Invalid download type: " + downloadType );
            return;
        }
        DownloadHandler handler = UvmContextFactory.context().servletFileManager().getDownloadHandler( downloadType );
        if (handler == null) {
            logger.error("Unable to handle an download of type: " + downloadType);
            return;
        }

        logger.info("Serving Download: " + downloadType);
        handler.serveDownload(req, resp);
    }

}
