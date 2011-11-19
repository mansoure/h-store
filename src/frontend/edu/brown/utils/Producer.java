package edu.brown.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;
import org.voltdb.utils.Pair;

import edu.brown.utils.LoggerUtil.LoggerBoolean;

/**
 * 
 * @author pavlo
 *
 * @param <T> The input type from the given Iterable
 * @param <U> The type of object that will generated by transform and queued at the Consumers
 */
public abstract class Producer<T, U> implements Runnable {
    private static final Logger LOG = Logger.getLogger(Producer.class);
    private static final LoggerBoolean debug = new LoggerBoolean(LOG.isDebugEnabled());
    private static final LoggerBoolean trace = new LoggerBoolean(LOG.isTraceEnabled());
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }

    private final Collection<Consumer<U>> consumers = new HashSet<Consumer<U>>();
    private Iterable<T> it;
    
    public Producer(Iterable<T> it) {
        this.it = it;
    }
    
    public Producer(Iterable<T> it, Collection<Consumer<U>> consumers) {
        this(it);
        this.consumers.addAll(consumers);
    }
    
    public final void addConsumer(Consumer<U> consumer) {
        this.consumers.add(consumer);
    }
    
    @Override
    public final void run() {
        for (Consumer<U> c : this.consumers) {
             c.start();
        } // FOR
        
        int ctr = 0;
        for (T t : this.it) {
            Pair<Consumer<U>, U> p = this.transform(t);
            assert(p != null);
            assert(p.getFirst() != null) : "Null Consumer - " + p;
            assert(p.getSecond() != null) : "Null Object - " + p;
            p.getFirst().queue(p.getSecond());
            if (++ctr % 100 == 0 && debug.get()) 
                LOG.debug(String.format("Queued %d %s objects", ctr, t.getClass().getSimpleName()));
        } // FOR
        
        // Poke all our threads to let them know that they should stop when their
        // queue is empty
        for (Consumer<U> c : this.consumers) c.stopWhenEmpty();
    }
    
    public abstract Pair<Consumer<U>, U> transform(T t);
    
    /**
     * Default transform implementation.
     * Returns a pair that contains a random Consumer and the input parameter t
     * case to type U
     * @param t
     * @return
     */
    @SuppressWarnings("unchecked")
    public final Pair<Consumer<U>, U> defaultTransform(T t) {
        return Pair.of(CollectionUtil.random(this.consumers), (U)t);
    }
    
    /**
     * Get the total number of items processed by all of the Consumers
     * attached to this Producer
     * @return
     */
    public final int getTotalProcessed() {
        int total = 0;
        for (Consumer<?> c : this.consumers) {
            total += c.getProcessedCounter();
        } // FOR
        return (total);
    }

    public final Collection<Consumer<U>> getConsumers() {
        return (Collections.unmodifiableCollection(this.consumers));
    }
    
    public final List<Runnable> getRunnablesList() {
        ArrayList<Runnable> runnables = new ArrayList<Runnable>();
        runnables.add(this);
        for (Consumer<U> c : this.consumers) {
            runnables.add(c);
        } // FOR
        return (runnables);
    }
    
    /**
     * Return a new instance of a Producer. This producer will simply assign
     * each item in the iterable to a random Consumer
     * @param <T>
     * @param <U>
     * @param it
     * @return
     */
    public static final <T, U> Producer<T, U> defaultProducer(Iterable<T> it) {
        return new Producer<T, U>(it) {
            public Pair<edu.brown.utils.Consumer<U>,U> transform(T t) {
                return this.defaultTransform(t);
            };
        };
    }
}
