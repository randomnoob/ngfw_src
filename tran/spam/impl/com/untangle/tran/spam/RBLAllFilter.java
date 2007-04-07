/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.spam;

import com.untangle.mvvm.logging.RepositoryDesc;
import com.untangle.mvvm.logging.SimpleEventFilter;

public class RBLAllFilter implements SimpleEventFilter<SpamSMTPRBLEvent>
{
    private static final RepositoryDesc REPO_DESC = new RepositoryDesc("All Events");

    private final String rblQuery;

    // constructors -----------------------------------------------------------

    public RBLAllFilter()
    {
        rblQuery = "FROM SpamSMTPRBLEvent evt WHERE evt.pipelineEndpoints.policy = :policy ORDER BY evt.timeStamp DESC";
    }

    // SimpleEventFilter methods ----------------------------------------------

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public String[] getQueries()
    {
        return new String[] { rblQuery };
    }

    public boolean accept(SpamSMTPRBLEvent e)
    {
        return true;
    }
}
