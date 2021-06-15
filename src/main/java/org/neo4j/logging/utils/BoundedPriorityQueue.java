package org.neo4j.logging.utils;

import java.util.Comparator;
import java.util.PriorityQueue;

public class BoundedPriorityQueue<T> extends PriorityQueue<T> {


    final private int maxSize ;

    public BoundedPriorityQueue(int maxSize) {
        super(maxSize);
        this.maxSize = maxSize;
    }
    public BoundedPriorityQueue(int maxSize, Comparator comparator) {
        super(maxSize, comparator);
        this.maxSize = maxSize;
    }

    @Override
    public boolean add (T  t) {
        boolean added = offer(t);

        if (!added) {
            return false;
        } else if (size() > maxSize) {
            this.remove(this.toArray()[this.size()-1]);
        }
        return true;
    }



}
