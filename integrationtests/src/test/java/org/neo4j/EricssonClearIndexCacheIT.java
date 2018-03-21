package org.neo4j;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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
