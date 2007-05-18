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

package com.untangle.mvvm.tran.firewall.intf;

import java.util.Map;
import java.util.HashMap;
import java.util.BitSet;

import com.untangle.mvvm.IntfConstants;

import com.untangle.mvvm.tran.ParseException;
import com.untangle.mvvm.tran.firewall.Parser;
import com.untangle.mvvm.tran.firewall.ParsingConstants;

/**
 * An IntfMatcher that matches the inverse of a bitset.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
public final class IntfInverseMatcher extends IntfDBMatcher
{
    /* Cache of the created inverse interface matchers */
    static Map<ImmutableBitSet,IntfInverseMatcher> CACHE = new HashMap<ImmutableBitSet,IntfInverseMatcher>();

    /* Set of bits that shouldn't match */
    private final ImmutableBitSet intfSet;

    /* String representation for the database */
    private final String databaseRepresentation;

    /* String representation for the user */
    private final String userRepresentation;
    
    private IntfInverseMatcher( ImmutableBitSet intfSet, String userRepresentation, 
                                String databaseRepresentation )
    {
        this.intfSet = intfSet;
        this.databaseRepresentation = databaseRepresentation;
        this.userRepresentation = userRepresentation;
    }

    /**
     * Test if <param>intf<param> matches this matcher.
     *
     * @param intf Interface to test.
     */
    public boolean isMatch( byte intf )
    {
        /* This always matches true */
        if ( IntfConstants.UNKNOWN_INTF == intf || IntfConstants.LOOPBACK_INTF == intf ) return true;

        if ( intf >= IntfConstants.MAX_INTF ) return false;

        /* It is an inverse matcher */
        return !intfSet.get( intf );
    }

    public String toDatabaseString()
    {
        return this.databaseRepresentation;
    }
    
    public String toString()
    {
        return this.userRepresentation;
    }

    /**
     * Create an Inverse Matcher.
     * 
     * @param intfArray array of interfaces that shouldn't match.
     */
    public static IntfDBMatcher makeInstance( byte ... intfArray ) throws ParseException
    {
        /** !!! Why throw an exception, why not just ignore the ones that are not needed */
        BitSet intfSet = new BitSet( IntfConstants.MAX_INTF );
        
        IntfMatcherUtil imu = IntfMatcherUtil.getInstance();
        
        /* The first pass is to just fill the bitset */
        for ( byte intf : intfArray ) intfSet.set( intf );

        String databaseRepresentation = "";
        String userRepresentation = "";

        /* Second pass, build up the user and database string */
        for ( int intf = intfSet.nextSetBit( 0 ) ; intf >= 0 ; intf = intfSet.nextSetBit( intf + 1 )) {
            if ( databaseRepresentation.length() > 0 ) {
                databaseRepresentation += ",";
                userRepresentation += " & ";
            }
            databaseRepresentation += imu.intfToDatabase((byte)intf );
            userRepresentation += imu.intfToUser((byte)intf );
        }
        
        /* Prepend the inverse mark */
        databaseRepresentation = ParsingConstants.MARKER_INVERSE + databaseRepresentation;
        userRepresentation = ParsingConstants.MARKER_INVERSE + userRepresentation;
        
        return makeInstance( intfSet, userRepresentation, databaseRepresentation );
    }

    /**
     * Parsing class for an inverse matcher */
    static final Parser<IntfDBMatcher> PARSER = new Parser<IntfDBMatcher>() 
    {
        public int priority()
        {
            return 2;
        }
        
        public boolean isParseable( String value )
        {
            return ( value.startsWith( ParsingConstants.MARKER_INVERSE ) && 
                     value.contains( ParsingConstants.MARKER_SEPERATOR ));
        }
        
        public IntfDBMatcher parse( String value ) throws ParseException
        {
            if ( !isParseable( value )) {
                throw new ParseException( "Invalid intf inverse matcher '" + value + "'" );
            }
            
            value = value.substring( ParsingConstants.MARKER_INVERSE.length());

            /* Just in case there is a space in front of it */
            value = value.trim();

            /* Split up the items */
            String databaseArray[] = value.split( ParsingConstants.MARKER_SEPERATOR );

            byte intfArray[] = new byte[databaseArray.length];
            
            int c = 0;
            IntfMatcherUtil imu = IntfMatcherUtil.getInstance();

            for ( String databaseString : databaseArray ) {
                intfArray[c++] = imu.databaseToIntf( databaseString );
            }
            
            return makeInstance( intfArray );
        }
    };


    /**
     * Create or retrieve the Interface matcher for this bit set.
     * Only create a new bitset if there isn't one in the cache.
     * 
     * @param intfSet BitSet to use.
     * @param user User string for this inverse interface matcher.
     * @param database Database representation for this bitset.
     */
    private static IntfDBMatcher makeInstance( BitSet intfSet, String user, String database )
    {
        ImmutableBitSet i = new ImmutableBitSet( intfSet );
        IntfInverseMatcher cache = CACHE.get( i );
        if ( cache == null ) CACHE.put( i, cache = new IntfInverseMatcher( i, user, database ));

        return cache;
    }
}
