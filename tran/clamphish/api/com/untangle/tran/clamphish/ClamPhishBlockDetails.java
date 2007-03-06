/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id: ClamPhishTransform.java 8965 2007-02-23 20:54:04Z cng $
 */

package com.untangle.tran.clamphish;

import com.untangle.tran.http.BlockDetails;

public class ClamPhishBlockDetails extends BlockDetails
{
    // constructor ------------------------------------------------------------

    public ClamPhishBlockDetails(String host, String uri)
    {
        super(host, uri);
    }
}
