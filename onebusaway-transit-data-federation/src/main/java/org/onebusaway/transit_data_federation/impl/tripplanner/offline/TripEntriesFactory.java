package org.onebusaway.transit_data_federation.impl.tripplanner.offline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.transit_data_federation.model.RouteCollection;
import org.onebusaway.transit_data_federation.model.ShapePoints;
import org.onebusaway.transit_data_federation.model.ShapePointsFactory;
import org.onebusaway.transit_data_federation.services.TransitDataFederationDao;
import org.onebusaway.transit_data_federation.services.offline.UniqueService;
import org.onebusaway.transit_data_federation.services.tripplanner.StopTimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TripEntriesFactory {

  private Logger _log = LoggerFactory.getLogger(TripEntriesFactory.class);

  private UniqueService _uniqueService;

  private TransitDataFederationDao _whereDao;

  private GtfsRelationalDao _gtfsDao;

  private StopTimeEntriesFactory _stopTimeEntriesFactory;

  private Map<AgencyAndId, ShapePoints> _shapePointsCache = new HashMap<AgencyAndId, ShapePoints>();

  @Autowired
  public void setUniqueService(UniqueService uniqueService) {
    _uniqueService = uniqueService;
  }

  @Autowired
  public void setWhereDao(TransitDataFederationDao whereDao) {
    _whereDao = whereDao;
  }

  @Autowired
  public void setGtfsDao(GtfsRelationalDao gtfsDao) {
    _gtfsDao = gtfsDao;
  }

  @Autowired
  public void setStopTimeEntriesFactory(
      StopTimeEntriesFactory stopTimeEntriesFactory) {
    _stopTimeEntriesFactory = stopTimeEntriesFactory;
  }

  public void processTrips(TripPlannerGraphImpl graph) {

    Collection<Route> routes = _gtfsDao.getAllRoutes();
    int routeIndex = 0;

    for (Route route : routes) {

      _log.info("route processed: " + routeIndex + "/" + routes.size());
      routeIndex++;

      List<Trip> tripsForRoute = _gtfsDao.getTripsForRoute(route);

      _log.info("trips to process: " + tripsForRoute.size());
      int tripIndex = 0;
      RouteCollection routeCollection = _whereDao.getRouteCollectionForRoute(route);

      for (Trip trip : tripsForRoute) {
        if (tripIndex % 50 == 0)
          _log.info("trips processed: " + tripIndex + "/"
              + tripsForRoute.size());
        tripIndex++;
        TripEntryImpl tripEntry = processTrip(graph, trip);
        tripEntry.setRouteCollectionId(unique(routeCollection.getId()));
      }

      // Clear the shape cache between routes, since there is less likelihood of
      // overlap
      _shapePointsCache.clear();
    }

    graph.refreshTripMapping();
  }

  private TripEntryImpl processTrip(TripPlannerGraphImpl graph, Trip trip) {

    List<StopTime> stopTimes = _gtfsDao.getStopTimesForTrip(trip);
    ShapePoints shapePoints = getShapePoints(trip);

    List<StopTimeEntryImpl> stopTimesForTrip = _stopTimeEntriesFactory.processStopTimes(
        graph, stopTimes, shapePoints);

    Agency agency = trip.getRoute().getAgency();
    TimeZone tz = TimeZone.getTimeZone(agency.getTimezone());
    LocalizedServiceId lsid = new LocalizedServiceId(trip.getServiceId(), tz);

    double tripDistance = getTripDistance(stopTimesForTrip, shapePoints);

    TripEntryImpl tripEntry = new TripEntryImpl();

    tripEntry.setId(trip.getId());
    tripEntry.setRouteId(unique(trip.getRoute().getId()));

    tripEntry.setServiceId(unique(lsid));
    tripEntry.setShapeId(unique(trip.getShapeId()));
    tripEntry.setTotalTripDistance(tripDistance);

    for (StopTimeEntryImpl stopTime : stopTimesForTrip)
      stopTime.setTrip(tripEntry);

    tripEntry.setStopTimes(cast(stopTimesForTrip));

    graph.putTripEntry(tripEntry);

    return tripEntry;
  }

  private List<StopTimeEntry> cast(List<StopTimeEntryImpl> stopTimesForTrip) {
    List<StopTimeEntry> stopTimes = new ArrayList<StopTimeEntry>(
        stopTimesForTrip.size());
    for (StopTimeEntryImpl stopTime : stopTimesForTrip)
      stopTimes.add(stopTime);
    return stopTimes;
  }

  private ShapePoints getShapePoints(Trip trip) {

    // Scenarios
    AgencyAndId shapeId = trip.getShapeId();

    if (shapeId == null)
      return null;

    if (_shapePointsCache.containsKey(shapeId))
      return _shapePointsCache.get(shapeId);

    ShapePointsFactory factory = new ShapePointsFactory();
    List<ShapePoint> rawPoints = _gtfsDao.getShapePointsForShapeId(shapeId);
    rawPoints = new ArrayList<ShapePoint>(rawPoints);
    Collections.sort(rawPoints);
    for (ShapePoint rawPoint : rawPoints)
      factory.addPoint(rawPoint.getLat(), rawPoint.getLon());
    ShapePoints shapePoints = factory.create();
    if (shapePoints.isEmpty())
      shapePoints = null;
    _shapePointsCache.put(shapeId, shapePoints);

    return shapePoints;
  }

  private double getTripDistance(List<StopTimeEntryImpl> stopTimes,
      ShapePoints shapePoints) {

    StopTimeEntryImpl lastStopTime = stopTimes.get(stopTimes.size() - 1);

    if (shapePoints != null) {
      double[] distances = shapePoints.getDistTraveled();
      double distance = distances[shapePoints.getSize() - 1];
      return Math.max(lastStopTime.getShapeDistTraveled(), distance);
    }

    return lastStopTime.getShapeDistTraveled();
  }

  private <T> T unique(T value) {
    return _uniqueService.unique(value);
  }
}
