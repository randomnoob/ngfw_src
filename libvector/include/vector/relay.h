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
/* @author: Dirk Morris <dmorris@untangle.com> */
#ifndef __RELAY_H
#define __RELAY_H

#include <mvutil/list.h>
#include <mvutil/hash.h>
#include <mvutil/mvpoll.h>
#include "source.h"
#include "sink.h"
#include "event.h"

struct relay {

    /**
     * The Source
     */
    source_t* src;
    /**
     * The Sink
     */
    sink_t*   snk;

    /**
     * The Queue
     */
    list_t event_q;

    /**
     * The Maximum Queue Size
     * Note: that this can be exceeded when a close happens, and the queue is full
     */
    int    event_q_max_len;

    /**
     * The vectoring machine handling this relay
     * NULL if not being vectored
     */
    struct vector* my_vec;

    /**
     * This is a local flag used by the vectoring machine to track the state of the source
     * Note: the source has no concept of being enabled/disabled, so this is here
     */
    int    src_enabled;
    /**
     * This is a local flag used by the vectoring machine to track the state of the sink
     * Note: the sink has no concept of being enabled/disabled, so this is here
     */
    int    snk_enabled;

    /**
     * This is a local flag used by the vectoring machine to track the state of the source
     */
    int    src_shutdown;
    /**
     * This is a local flag used by the vectoring machine to track the state of the sink
     */
    int    snk_shutdown;
     
    /**
     * This event hook will be called when events enter the queue (if non null)
     */
    void   (*event_hook) (struct vector* vec, struct relay* relay, struct event* evt, void* arg);
    void*   event_hook_arg;
};


typedef void (*relay_event_hook_t)  (struct vector* vec, struct relay* relay, struct event* evt, void* arg);

typedef struct relay relay_t;

relay_t* relay_create ( void );
void     relay_free ( relay_t* relay );

void     relay_set_src ( relay_t* relay, source_t* src );
void     relay_set_snk ( relay_t* relay, sink_t* snk );

void     relay_set_event_hook ( relay_t* relay, relay_event_hook_t hook );
void     relay_set_event_hook_arg  ( relay_t* relay, void* arg );

int      relay_debug_print ( int level, char* prefix, relay_t* relay );

#endif
