package org.onebusaway.transit_data_federation.impl.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.aid;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.block;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.linkBlockTrips;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.stop;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.stopTime;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.time;
import static org.onebusaway.transit_data_federation.testing.UnitTestingSupport.trip;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.realtime.api.VehicleLocationRecord;
import org.onebusaway.transit_data_federation.impl.tripplanner.offline.BlockEntryImpl;
import org.onebusaway.transit_data_federation.impl.tripplanner.offline.StopEntryImpl;
import org.onebusaway.transit_data_federation.impl.tripplanner.offline.TripEntryImpl;
import org.onebusaway.transit_data_federation.services.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.blocks.BlockCalendarService;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocationService;
import org.onebusaway.transit_data_federation.services.realtime.BlockLocation;
import org.onebusaway.transit_data_federation.services.tripplanner.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.tripplanner.BlockStopTimeEntry;
import org.onebusaway.transit_data_federation.services.tripplanner.StopTimeInstanceProxy;
import org.onebusaway.utility.DateLibrary;

public class BlockLocationServiceImplTest {

  private BlockLocationServiceImpl _service;

  private TransitGraphDao _transitGraphDao;

  private ScheduledBlockLocationService _blockLocationService;

  private BlockCalendarService _blockCalendarService;

  @Before
  public void setup() {

    _service = new BlockLocationServiceImpl();

    _transitGraphDao = Mockito.mock(TransitGraphDao.class);
    _service.setTransitGraphDao(_transitGraphDao);

    _blockLocationService = Mockito.mock(ScheduledBlockLocationService.class);
    _service.setScheduledBlockLocationService(_blockLocationService);

    _service.setBlockLocationRecordCache(new BlockLocationRecordCacheImpl());

    _blockCalendarService = Mockito.mock(BlockCalendarService.class);
    _service.setBlockCalendarService(_blockCalendarService);
  }

  @Test
  public void testApplyRealtimeData() {

    StopEntryImpl stopA = stop("a", 47.5, -122.5);
    StopEntryImpl stopB = stop("b", 47.6, -122.6);
    StopEntryImpl stopC = stop("c", 47.7, -122.7);
    StopEntryImpl stopD = stop("d", 47.8, -122.8);

    BlockEntryImpl block = block("block");

    TripEntryImpl trip = trip("trip", "serviceId");

    stopTime(0, stopA, trip, time(9, 10), time(9, 11), -1);
    stopTime(1, stopB, trip, time(9, 20), time(9, 22), -1);
    stopTime(2, stopC, trip, time(9, 30), time(9, 30), -1);
    stopTime(3, stopD, trip, time(9, 40), time(9, 45), -1);

    BlockConfigurationEntry blockConfig = linkBlockTrips(block, trip);
    List<BlockStopTimeEntry> stopTimes = blockConfig.getStopTimes();

    Mockito.when(_transitGraphDao.getTripEntryForId(aid("trip"))).thenReturn(
        trip);
    Mockito.when(_transitGraphDao.getBlockEntryForId(aid("block"))).thenReturn(
        block);

    Date date = DateLibrary.getTimeAsDay(new Date());
    long serviceDate = date.getTime();

    List<BlockInstance> instances = Arrays.asList(new BlockInstance(
        blockConfig, serviceDate));
    Mockito.when(
        _blockCalendarService.getActiveBlocks(Mockito.eq(block.getId()),
            Mockito.anyLong(), Mockito.anyLong())).thenReturn(instances);

    VehicleLocationRecord vprA = new VehicleLocationRecord();
    vprA.setTripId(trip.getId());
    vprA.setScheduleDeviation(120);
    vprA.setTimeOfRecord(t(serviceDate, 9, 0));
    vprA.setServiceDate(serviceDate);
    vprA.setVehicleId(aid("vehicleA"));

    VehicleLocationRecord vprB = new VehicleLocationRecord();
    vprB.setTripId(trip.getId());
    vprB.setScheduleDeviation(130);
    vprB.setTimeOfRecord(t(serviceDate, 9, 8));
    vprB.setServiceDate(serviceDate);
    vprB.setVehicleId(aid("vehicleA"));

    _service.handleVehicleLocationRecords(Arrays.asList(vprA, vprB));

    StopTimeInstanceProxy stiA = new StopTimeInstanceProxy(stopTimes.get(0),
        serviceDate);
    StopTimeInstanceProxy stiB = new StopTimeInstanceProxy(stopTimes.get(1),
        serviceDate);
    StopTimeInstanceProxy stiC = new StopTimeInstanceProxy(stopTimes.get(2),
        serviceDate);
    StopTimeInstanceProxy stiD = new StopTimeInstanceProxy(stopTimes.get(3),
        serviceDate);

    List<StopTimeInstanceProxy> stis = Arrays.asList(stiA, stiB, stiC, stiD);

    _service.applyRealtimeData(stis, t(serviceDate, 9, 0));

    assertEquals(120, stiA.getPredictedArrivalOffset());
    assertEquals(60, stiA.getPredictedDepartureOffset());
    assertEquals(60, stiB.getPredictedArrivalOffset());
    assertEquals(0, stiB.getPredictedDepartureOffset());
    assertEquals(0, stiC.getPredictedArrivalOffset());
    assertEquals(0, stiC.getPredictedDepartureOffset());
    assertEquals(0, stiD.getPredictedArrivalOffset());
    assertEquals(0, stiD.getPredictedDepartureOffset());

    _service.applyRealtimeData(stis, t(serviceDate, 9, 9));

    assertEquals(130, stiA.getPredictedArrivalOffset());
    assertEquals(70, stiA.getPredictedDepartureOffset());
    assertEquals(70, stiB.getPredictedArrivalOffset());
    assertEquals(0, stiB.getPredictedDepartureOffset());
    assertEquals(0, stiC.getPredictedArrivalOffset());
    assertEquals(0, stiC.getPredictedDepartureOffset());
    assertEquals(0, stiD.getPredictedArrivalOffset());
    assertEquals(0, stiD.getPredictedDepartureOffset());

    VehicleLocationRecord vpr = new VehicleLocationRecord();
    vpr.setTripId(trip.getId());
    vpr.setScheduleDeviation(240);
    vpr.setTimeOfRecord(t(serviceDate, 9, 10.5));
    vpr.setServiceDate(serviceDate);
    vpr.setVehicleId(aid("vehicleA"));

    _service.handleVehicleLocationRecords(Arrays.asList(vpr));

    _service.applyRealtimeData(stis, t(serviceDate, 9, 11));

    assertEquals(240, stiA.getPredictedArrivalOffset());
    assertEquals(180, stiA.getPredictedDepartureOffset());
    assertEquals(180, stiB.getPredictedArrivalOffset());
    assertEquals(60, stiB.getPredictedDepartureOffset());
    assertEquals(60, stiC.getPredictedArrivalOffset());
    assertEquals(60, stiC.getPredictedDepartureOffset());
    assertEquals(60, stiD.getPredictedArrivalOffset());
    assertEquals(0, stiD.getPredictedDepartureOffset());

    vpr = new VehicleLocationRecord();
    vpr.setTripId(trip.getId());
    vpr.setScheduleDeviation(90);
    vpr.setTimeOfRecord(t(serviceDate, 9, 12));
    vpr.setServiceDate(serviceDate);
    vpr.setVehicleId(aid("vehicleA"));

    _service.handleVehicleLocationRecords(Arrays.asList(vpr));

    _service.applyRealtimeData(stis, t(serviceDate, 9, 12.5));

    assertEquals(109, stiA.getPredictedArrivalOffset());
    assertEquals(90, stiA.getPredictedDepartureOffset());
    assertEquals(90, stiB.getPredictedArrivalOffset());
    assertEquals(0, stiB.getPredictedDepartureOffset());
    assertEquals(0, stiC.getPredictedArrivalOffset());
    assertEquals(0, stiC.getPredictedDepartureOffset());
    assertEquals(0, stiD.getPredictedArrivalOffset());
    assertEquals(0, stiD.getPredictedDepartureOffset());
  }

  @Test
  public void testWithShapeInfo() {

    StopEntryImpl stopA = stop("a", 47.5, -122.5);
    StopEntryImpl stopB = stop("b", 47.6, -122.4);
    StopEntryImpl stopC = stop("c", 47.5, -122.3);

    BlockEntryImpl block = block("block");

    TripEntryImpl tripA = trip("tripA", "serviceId");
    TripEntryImpl tripB = trip("tripB", "serviceId");

    stopTime(0, stopA, tripA, 30, 90, 0);
    stopTime(1, stopB, tripA, 120, 120, 100);
    stopTime(2, stopC, tripA, 180, 210, 200);

    stopTime(3, stopC, tripB, 240, 240, 300);
    stopTime(4, stopB, tripB, 270, 270, 400);
    stopTime(5, stopA, tripB, 300, 300, 500);

    BlockConfigurationEntry blockConfig = linkBlockTrips(block, tripA, tripB);

    long serviceDate = 1000 * 1000;

    double epsilon = 0.001;

    BlockInstance blockInstance = new BlockInstance(blockConfig, serviceDate);
    BlockLocation location = _service.getLocationForBlockInstance(
        blockInstance, t(serviceDate, 0, 0));

    assertFalse(location.isInService());
    assertNull(location.getClosestStop());
    assertEquals(0, location.getClosestStopTimeOffset());
    assertFalse(location.hasScheduleDeviation());
    assertTrue(Double.isNaN(location.getScheduleDeviation()));
    assertFalse(location.hasDistanceAlongBlock());
    assertTrue(Double.isNaN(location.getDistanceAlongBlock()));
    assertNull(location.getLocation());
    assertEquals(blockInstance, location.getBlockInstance());
    assertEquals(0, location.getLastUpdateTime());
    assertNull(location.getActiveTrip());
    assertNull(location.getVehicleId());

    ScheduledBlockLocation p = new ScheduledBlockLocation();
    p.setActiveTrip(blockConfig.getTrips().get(0));
    p.setClosestStop(blockConfig.getStopTimes().get(0));
    p.setClosestStopTimeOffset(0);
    p.setDistanceAlongBlock(0);
    p.setLocation(new CoordinatePoint(stopA.getStopLat(), stopA.getStopLon()));

    Mockito.when(
        _blockLocationService.getScheduledBlockLocationFromScheduledTime(
            blockConfig.getStopTimes(), 1800)).thenReturn(p);

    location = _service.getLocationForBlockInstance(blockInstance,
        t(serviceDate, 0, 30));

    assertTrue(location.isInService());
    assertEquals(blockConfig.getStopTimes().get(0), location.getClosestStop());
    assertEquals(0, location.getClosestStopTimeOffset());

    assertEquals(stopA.getStopLocation(), location.getLocation());

    assertFalse(location.hasScheduleDeviation());
    assertTrue(Double.isNaN(location.getScheduleDeviation()));

    assertFalse(location.hasDistanceAlongBlock());
    assertTrue(Double.isNaN(location.getDistanceAlongBlock()));

    assertEquals(blockInstance, location.getBlockInstance());
    assertEquals(0, location.getLastUpdateTime());
    assertEquals(blockConfig.getTrips().get(0), location.getActiveTrip());
    assertNull(location.getVehicleId());

    assertEquals(47.5, location.getLocation().getLat(), epsilon);
    assertEquals(-122.5, location.getLocation().getLon(), epsilon);
    assertEquals(blockConfig.getStopTimes().get(0), location.getClosestStop());
    assertEquals(0, location.getClosestStopTimeOffset());
  }

  private long t(long serviceDate, int hours, double minutes) {
    return (long) (serviceDate + (((hours * 60) + minutes) * 60) * 1000);
  }
}
