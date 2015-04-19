package org.neo4j.shell.extension.util;

import org.neo4j.cypher.javacompat.QueryStatistics;
import org.neo4j.shell.Output;
import java.rmi.RemoteException;

/**
* Created by mh on 10.07.13.
*/
public class ProgressReporter implements Reporter {
//    private final SizeCounter sizeCounter;
    private Output out;
    long time;
    int counter;
    long start=System.currentTimeMillis();
    private final ElementCounter elementCounter = new ElementCounter();

    public ProgressReporter(Output out) {
//        this.sizeCounter = sizeCounter;
        this.out = out;
        this.time = start;
    }

    @Override
    public void progress(String msg) {
        long now = System.currentTimeMillis();
        println(String.format(msg+" %d. %d%%: %s time %d ms total %d ms", counter++, now - time, now - start));
        time = now;
    }

    private void println(String message) {
        try {
            out.println(message);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

//    private long percent() {
//        return sizeCounter == null ? 100 : sizeCounter.getPercent();
//    }

    public void update(long nodes, long relationships, long properties) {
        elementCounter.update(nodes,relationships,properties);
    }

    public static void update(QueryStatistics queryStatistics, Reporter reporter) {
        if (queryStatistics.containsUpdates()) {
            reporter.update(
                    queryStatistics.getNodesCreated() - queryStatistics.getDeletedNodes(),
                    queryStatistics.getRelationshipsCreated() - queryStatistics.getDeletedRelationships(),
                    queryStatistics.getPropertiesSet());
        }
    }

    public ElementCounter getTotal() {
        return elementCounter;
    }
}
