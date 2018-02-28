/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.transport;

import org.neo4j.bolt.BoltChannel;
import org.neo4j.bolt.runtime.BoltConnection;
import org.neo4j.bolt.runtime.BoltConnectionFactory;
import org.neo4j.bolt.v1.transport.BoltProtocolV1;
import org.neo4j.kernel.impl.logging.LogService;

public class DefaultBoltProtocolHandlerFactory
{
    private final BoltConnectionFactory connectionFactory;
    private final LogService logService;

    public DefaultBoltProtocolHandlerFactory( BoltConnectionFactory connectionFactory, LogService logService )
    {
        this.connectionFactory = connectionFactory;
        this.logService = logService;
    }

    public BoltProtocolV1 create( long protocolVersion, BoltChannel channel )
    {
        if ( protocolVersion == BoltProtocolV1.VERSION )
        {
            return newMessagingProtocolHandler( channel );
        }
        else
        {
            return null;
        }
    }

    private BoltProtocolV1 newMessagingProtocolHandler( BoltChannel channel )
    {
        return new BoltProtocolV1( channel, newBoltConnection( channel ), logService );
    }

    private BoltConnection newBoltConnection( BoltChannel channel )
    {
        return connectionFactory.newConnection( channel );
    }
}
