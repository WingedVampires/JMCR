package edu.tamu.aser.accelerate;

import java.util.Comparator;
import java.util.HashSet;

public class HashSetComparator implements Comparator {

    public int compare(Object o1, Object o2) {

        int result = ((HashSet) o1).size() - ((HashSet) o2).size();
        return result == 0 ? o1.equals(o2) ? 0 : 1 : result;
    }
}
