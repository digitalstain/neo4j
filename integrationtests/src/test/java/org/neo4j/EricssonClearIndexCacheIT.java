/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EricssonClearIndexCacheIT
{
    @Rule
    public final TestDirectory directory = TestDirectory.testDirectory();
    private GraphDatabaseService db;
    private AssertableLogProvider logProvider;

    @Before
    public void setUp()
    {
        logProvider = new AssertableLogProvider();
        db = new TestGraphDatabaseFactory()
                .setInternalLogProvider( logProvider )
                .newEmbeddedDatabase( directory.graphDbDir() );
    }

    @After
    public void tearDown()
    {
        db.shutdown();
    }

    @Test
    public void clearIndexCaches()
    {
        // Since everything is created lazily we need this dance to actually initialize all the required objects
        db.execute( "CREATE INDEX ON :Person(firstname)" );
        try ( Transaction tx = db.beginTx() )
        {
            Node person = db.createNode( Label.label( "Person" ) );
            person.setProperty( "firstname", "Anton" );
            tx.success();
        }

        try ( Transaction tx = db.beginTx() )
        {
            db.schema().awaitIndexesOnline( 10, TimeUnit.SECONDS );
            tx.success();
        }

        try ( Transaction tx = db.beginTx() )
        {
            Result result = db.execute( "MATCH (p:Person) WHERE p.firstname = 'Anton' RETURN p" );
            while ( result.hasNext() )
            {
                Map<String,Object> map = result.next();
                Node p = (Node) map.get( "p" );
                assertEquals( "Anton", p.getProperty( "firstname" ) );
                assertFalse( result.hasNext() );
            }
            tx.success();
        }

        // Try to clear caches, should already be empty though
        db.execute( "CALL db.ericsson.clearIndexCaches" );
        logProvider.assertContainsMessageContaining( "Removed 0 cached readers" );
    }
}
