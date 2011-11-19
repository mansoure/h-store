package org.voltdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.voltdb.catalog.Catalog;
import org.voltdb.messaging.FragmentTaskMessage;

import edu.brown.utils.PartitionEstimator;
import edu.mit.hstore.dtxn.LocalTransactionState;

/**
 * 
 * @author pavlo
 */
public class MockExecutionSite extends ExecutionSite {
    
    private static final BackendTarget BACKEND_TARGET = BackendTarget.HSQLDB_BACKEND;
    
    private final Map<Long, VoltTable> dependencies = new HashMap<Long, VoltTable>();
    private final Map<Long, CountDownLatch> latches = new HashMap<Long, CountDownLatch>();
    
    public MockExecutionSite(int partition_id, Catalog catalog, PartitionEstimator p_estimator) {
        super(partition_id, catalog, BACKEND_TARGET, p_estimator, null);
        this.initializeVoltProcedures();
    }

    @Override
    public void processClientResponse(LocalTransactionState ts, ClientResponseImpl cresponse) {
        // Nothing!
    }
    
    @Override
    public VoltTable[] waitForResponses(long txnId, List<FragmentTaskMessage> tasks, int batchSize) {
        return (new VoltTable[]{ });
    }
    
    @Override
    public synchronized void storeDependency(long txnId, int senderPartitionId, int dependencyId, VoltTable data) {
    	System.err.println("STORING TXN #" + txnId);
        this.dependencies.put(txnId, data);
        CountDownLatch latch = this.latches.get(txnId);
        if (latch != null) {
        	System.err.println("UNBLOCKING TXN #" + txnId);
        	latch.countDown();
        }
    }
    
    public synchronized VoltTable getDependency(long txnId) {
        return this.dependencies.get(txnId);
    }
    
    public synchronized VoltTable waitForDependency(long txnId) {
    	VoltTable vt = this.dependencies.get(txnId);
    	if (vt == null) {
    		CountDownLatch latch = this.latches.get(txnId);
    		if (latch == null) {
    			latch = new CountDownLatch(1);
    			this.latches.put(txnId, latch);
    		}
    		try {
    			System.err.println("WAITING FOR TXN #" + txnId);
    			latch.await(100, TimeUnit.MILLISECONDS);
    		} catch (InterruptedException ex) {
    			throw new RuntimeException(ex);
    		}
    		vt = this.dependencies.get(txnId);
    	}
    	return (vt);
    }
}
