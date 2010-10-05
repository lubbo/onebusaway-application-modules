package org.onebusaway.transit_data_federation.impl.time;

import java.util.List;

/**
 * Generic binary search that can accepts lists of Java objects that can be
 * adapted to a double value for searching.
 * 
 * @author bdferris
 * 
 */
public class GenericBinarySearch {

  /**
   * Return an index into the element list such that if a new element with the
   * specified target value was inserted into the list at the specified index,
   * the list would remain in sorted order with respect to the
   * {@link ValueAdapter}
   * 
   * @param elements a list of objects, sorted in the order appropriate to the
   *          {@link ValueAdapter}
   * @param targetValue target value to search for
   * @param valueAdapter adapter to convert the input element type into a double
   *          value
   * @return
   */
  public static <T> int search(List<T> elements, double targetValue,
      ValueAdapter<T> valueAdapter) {
    return search(elements, targetValue, valueAdapter, 0, elements.size());
  }

  public interface ValueAdapter<T> {
    public double getValue(T value);
  }

  /****
   * Private Methods
   ****/

  private static <T> int search(List<T> elements, double target,
      ValueAdapter<T> comparator, int fromIndex, int toIndex) {

    if (fromIndex == toIndex)
      return fromIndex;

    int midIndex = (fromIndex + toIndex) / 2;
    T element = elements.get(midIndex);
    double v = comparator.getValue(element);

    if (target < v) {
      return search(elements, target, comparator, fromIndex, midIndex);
    } else if (target > v) {
      return search(elements, target, comparator, midIndex + 1, toIndex);
    } else {
      return midIndex;
    }
  }
}
