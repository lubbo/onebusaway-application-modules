package org.onebusaway.transit_data_federation.model.tripplanner;

import org.onebusaway.transit_data_federation.services.tripplanner.StopEntry;

public interface StopEntriesWithValues {

  public int size();

  public boolean isEmpty();

  public StopEntry getStopEntry(int index);

  public int getValue(int index);
}