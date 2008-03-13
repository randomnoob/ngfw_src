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
package com.untangle.node.protofilter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

import com.untangle.uvm.logging.EventLogger;
import com.untangle.uvm.logging.EventLoggerFactory;
import com.untangle.uvm.logging.EventManager;
import com.untangle.uvm.logging.SimpleEventFilter;
import com.untangle.uvm.node.NodeContext;
import com.untangle.uvm.node.NodeException;
import com.untangle.uvm.node.NodeStartException;
import com.untangle.uvm.util.QueryUtil;
import com.untangle.uvm.util.TransactionWork;
import com.untangle.uvm.vnet.AbstractNode;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipeSpec;
import com.untangle.uvm.vnet.SoloPipeSpec;

public class ProtoFilterImpl extends AbstractNode implements ProtoFilter
{
    private final EventHandler handler = new EventHandler( this );

    private final SoloPipeSpec pipeSpec = new SoloPipeSpec
        ("protofilter", this, handler, Fitting.OCTET_STREAM,
         Affinity.CLIENT, 0);
    private final PipeSpec[] pipeSpecs = new PipeSpec[] { pipeSpec };

    private final EventLogger<ProtoFilterLogEvent> eventLogger;

    private final Logger logger = Logger.getLogger(ProtoFilterImpl.class);

    private ProtoFilterSettings cachedSettings = null;

    // constructors -----------------------------------------------------------

    public ProtoFilterImpl()
    {
        NodeContext tctx = getNodeContext();
        eventLogger = EventLoggerFactory.factory().getEventLogger(tctx);

        SimpleEventFilter ef = new ProtoFilterAllFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new ProtoFilterBlockedFilter();
        eventLogger.addSimpleEventFilter(ef);
    }

    // ProtoFilter methods ----------------------------------------------------

    public ProtoFilterSettings getProtoFilterSettings()
    {
        if( this.cachedSettings == null )
            logger.error("Settings not yet initialized. State: " + getNodeContext().getRunState() );
        return this.cachedSettings;
    }

    public void setProtoFilterSettings(final ProtoFilterSettings settings)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    s.merge(settings);
                    ProtoFilterImpl.this.cachedSettings = settings;
                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);

        try {
            reconfigure();
        }
        catch (NodeException exn) {
            logger.error("Could not save ProtoFilter settings", exn);
        }
    }
    
    public ProtoFilterBaseSettings getBaseSettings() {
        return cachedSettings.getBaseSettings();
    }

    public void setBaseSettings(ProtoFilterBaseSettings baseSettings) {
        cachedSettings.setBaseSettings(baseSettings);
    }
    
    public List<ProtoFilterPattern> getPatterns(final int start,
			final int limit, final String... sortColumns) {
		return getPatterns(
				"select hbs.patterns from ProtoFilterSettings hbs where hbs.tid = :tid ",
				start, limit, sortColumns);
	}

	public void updatePatterns(List<ProtoFilterPattern> added,
			List<Long> deleted, List<ProtoFilterPattern> modified) {

		updatePatterns(getProtoFilterSettings().getPatterns(), added, deleted,
				modified);
	}

    // wrapper method to update all settings once
    // TODO can we find a better place for this ugly method?
    public void updateAll(List[] patternsChanges) {
    	if (patternsChanges != null && patternsChanges.length >= 3) {
    		updatePatterns(patternsChanges[0], patternsChanges[1], patternsChanges[2]);
    	}
		
        try {
            reconfigure();
        }
        catch (NodeException exn) {
            logger.error("Could not update ProtoFilter changes", exn);
        }
	}
    
    public EventManager<ProtoFilterLogEvent> getEventManager()
    {
        return eventLogger;
    }

    // AbstractNode methods ----------------------------------------------

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    // Node methods ------------------------------------------------------

    /*
     * First time initialization
     */
    public void initializeSettings()
    {
        ProtoFilterSettings settings = new ProtoFilterSettings(this.getTid());
        logger.info("INIT: Importing patterns...");
        TreeMap factoryPatterns = LoadPatterns.getPatterns(); /* Global List of Patterns */
        // Turn on the Instant Messenger ones so it does something by default:
        Set pats = new HashSet(factoryPatterns.values());
        for (Object pat : pats) {
            ProtoFilterPattern pfp = (ProtoFilterPattern)pat;
            if (pfp.getCategory().equalsIgnoreCase("Instant Messenger"))
                pfp.setLog(true);
        }
        settings.setPatterns(pats);
        setProtoFilterSettings(settings);
    }

    protected void postInit(String[] args)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    Query q = s.createQuery("from ProtoFilterSettings hbs where hbs.tid = :tid");
                    q.setParameter("tid", getTid());
                    ProtoFilterImpl.this.cachedSettings = (ProtoFilterSettings)q.uniqueResult();

                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);

        updateToCurrent();
    }

    protected void preStart() throws NodeStartException
    {
        try {
            reconfigure();
        } catch (Exception e) {
            throw new NodeStartException(e);
        }
    }

    private void reconfigure() throws NodeException
    {
        Set enabledPatternsSet = new HashSet();

        logger.info("Reconfigure()");

        if (cachedSettings == null) {
            throw new NodeException("Failed to get ProtoFilter settings: " + cachedSettings);
        }

        Set curPatterns = cachedSettings.getPatterns();
        if (curPatterns == null)
            logger.error("NULL pattern list. Continuing anyway...");
        else {
            for (Iterator i=curPatterns.iterator() ; i.hasNext() ; ) {
                ProtoFilterPattern pat = (ProtoFilterPattern)i.next();

                if ( pat.getLog() || pat.getAlert() || pat.isBlocked() ) {
                    logger.info("Matching on pattern \"" + pat.getProtocol() + "\"");
                    enabledPatternsSet.add(pat);
                }
            }
        }

        handler.patternSet(enabledPatternsSet);
        handler.byteLimit(cachedSettings.getByteLimit());
        handler.chunkLimit(cachedSettings.getChunkLimit());
        handler.unknownString(cachedSettings.getUnknownString());
        handler.stripZeros(cachedSettings.isStripZeros());
    }


    private   void updateToCurrent()
    {
        if (cachedSettings == null) {
            logger.error("NULL ProtoFilter Settings");
            return;
        }

        boolean    madeChange = false;
        TreeMap    factoryPatterns = LoadPatterns.getPatterns(); /* Global List of Patterns */
        Set        curPatterns = cachedSettings.getPatterns(); /* Current list of Patterns */

        /*
         * Look for updates
         */
        for (Iterator i = curPatterns.iterator() ; i.hasNext() ; ) {
            ProtoFilterPattern curPat = (ProtoFilterPattern) i.next();
            int mvid = curPat.getMetavizeId();
            ProtoFilterPattern newPat = (ProtoFilterPattern) factoryPatterns.get(mvid);

            // logger.info("INFO: Found existing pattern " + mvid + " Pattern (" + curPat.getProtocol() + ")");
            if (mvid == ProtoFilterPattern.NEEDS_CONVERSION_METAVIZE_ID) {
                // Special one-time handling for conversion from 3.1.3 to 3.2
                // Find it by name
                madeChange = true;
                String curName = curPat.getProtocol();
                boolean found = false;
                for (Iterator j = factoryPatterns.values().iterator() ; j.hasNext() ; ) {
                    newPat = (ProtoFilterPattern) j.next();
                    if (newPat.getProtocol().equals(curName)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    logger.info("CONVERT: Updating MVID for Pattern (" + curName + ")");
                    mvid = newPat.getMetavizeId();
                    curPat.setMetavizeId(mvid);
                } else {
                    // A name mismatch, a user pattern, or the pattern has been deleted.
                    newPat = null;
                }
                // In either case, fall through to below
            }
            if (newPat != null) {
                /*
                 * Pattern is present in current config
                 * Update it if needed
                 */
                if (!newPat.getProtocol().equals(curPat.getProtocol())) {
                    logger.info("UPDATE: Updating Protocol for Pattern (" + mvid + ")");
                    madeChange = true;
                    curPat.setProtocol(newPat.getProtocol());
                }
                if (!newPat.getCategory().equals(curPat.getCategory())) {
                    logger.info("UPDATE: Updating Category for Pattern (" + mvid + ")");
                    madeChange = true;
                    curPat.setCategory(newPat.getCategory());
                }
                if (!newPat.getDescription().equals(curPat.getDescription())) {
                    logger.info("UPDATE: Updating Description for Pattern (" + mvid + ")");
                    madeChange = true;
                    curPat.setDescription(newPat.getDescription());
                }
                if (!newPat.getDefinition().equals(curPat.getDefinition())) {
                    logger.info("UPDATE: Updating Definition  for Pattern (" + mvid + ")");
                    madeChange = true;
                    curPat.setDefinition(newPat.getDefinition());
                }
                if (!newPat.getQuality().equals(curPat.getQuality())) {
                    logger.info("UPDATE: Updating Quality  for Pattern (" + mvid + ")");
                    madeChange = true;
                    curPat.setQuality(newPat.getQuality());
                }

                // Remove it, its been accounted for
                factoryPatterns.remove(mvid);
            } else if (mvid != ProtoFilterPattern.USER_CREATED_METAVIZE_ID) {
                // MV Pattern has been deleted.
                i.remove();
                madeChange = true;
                logger.info("UPDATE: Removing old Pattern " + mvid + " (" + curPat.getProtocol() + ")");
            }
        }

        /*
         * At this point, curPatterns is correct except for the newly added factory patterns.
         * Go ahead and add them now.  To put them in the right place, we do a linear
         * insertion, which isn't bad since the list is never very long.
         */
        if (factoryPatterns.size() > 0) {
            madeChange = true;
            LinkedList allPatterns = new LinkedList(curPatterns);
            for (Iterator i = factoryPatterns.values().iterator() ; i.hasNext() ; ) {
                ProtoFilterPattern factoryPat = (ProtoFilterPattern) i.next();
                logger.info("UPDATE: Adding New Pattern (" + factoryPat.getProtocol() + ")");
                boolean added = false;
                int index = 0;
                for (Iterator j = allPatterns.iterator() ; j.hasNext() ; index++) {
                    ProtoFilterPattern curPat = (ProtoFilterPattern) j.next();
                    if (factoryPat.getMetavizeId() < curPat.getMetavizeId()) {
                        allPatterns.add(index, factoryPat);
                        added = true;
                        break;
                    }
                }
                if (!added)
                    allPatterns.add(factoryPat);
            }
            curPatterns = new HashSet(allPatterns);
        }

        if (madeChange) {
            logger.info("UPDATE: Saving new patterns list, size " + curPatterns.size());
            cachedSettings.setPatterns(curPatterns);
            setProtoFilterSettings(cachedSettings);
        }

        logger.info("UPDATE: Complete");
    }

    void log(ProtoFilterLogEvent se)
    {
        eventLogger.log(se);
    }

    private List getPatterns(final String queryString, final int start,
                          final int limit, final String... sortColumns) {
        TransactionWork<List> tw = new TransactionWork<List>() {
            private List result;

            public boolean doWork(Session s) {
                Query q = s.createQuery(queryString
                                        + QueryUtil.toOrderByClause(sortColumns));
                q.setParameter("tid", getTid());
                q.setFirstResult(start);
                q.setMaxResults(limit);
                result = q.list();

                return true;
            }

            public List getResult() {
                return result;
            }
        };
        getNodeContext().runTransaction(tw);

        return tw.getResult();
    }

    private void updatePatterns(final Set<ProtoFilterPattern> patterns, final List<ProtoFilterPattern> added,
                             final List<Long> deleted, final List<ProtoFilterPattern> modified)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    for (Iterator<ProtoFilterPattern> i = patterns.iterator(); i.hasNext();) {
                    	ProtoFilterPattern pattern = i.next();
                    	ProtoFilterPattern mPattern = null;
                        if (deleted != null && deleted.contains(pattern.getId())) {
                            i.remove();
                        } else if (modified != null && (mPattern = modified(pattern, modified)) != null ) {
                        	pattern.updateRule(mPattern);
                        }
                    }
                    
                    if (added != null) {
                    	patterns.addAll(added);
                    }

                    s.merge(cachedSettings);

                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);
    }

    private ProtoFilterPattern modified(ProtoFilterPattern pattern, List modified) {
        for (Iterator<ProtoFilterPattern> iterator = modified.iterator(); iterator.hasNext();) {
            ProtoFilterPattern currentPattern = iterator.next();
            if(currentPattern.getId().equals(pattern.getId())){
                return currentPattern;
            }
        }
        return null;
    }
}
