/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.transit_data_federation.impl.beans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.onebusaway.container.cache.Cacheable;
import org.onebusaway.exceptions.NoSuchStopServiceException;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.StopRouteDirectionScheduleBean;
import org.onebusaway.transit_data.model.StopRouteScheduleBean;
import org.onebusaway.transit_data.model.StopTimeInstanceBean;
import org.onebusaway.transit_data_federation.model.narrative.StopNarrative;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.RouteService;
import org.onebusaway.transit_data_federation.services.beans.RouteBeanService;
import org.onebusaway.transit_data_federation.services.beans.StopBeanService;
import org.onebusaway.transit_data_federation.services.beans.StopScheduleBeanService;
import org.onebusaway.transit_data_federation.services.narrative.NarrativeService;
import org.onebusaway.transit_data_federation.services.transit_graph.StopEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.utility.text.NaturalStringOrder;
import org.onebusaway.utility.text.StringLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class StopBeanServiceImpl implements StopBeanService {

  private static RouteBeanComparator _routeBeanComparator = new RouteBeanComparator();

  private TransitGraphDao _transitGraphDao;

  private RouteService _routeService;

  private RouteBeanService _routeBeanService;

  private NarrativeService _narrativeService;
  
  private StopScheduleBeanService _stopScheduleBeanService;

  @Autowired
  public void setTranstiGraphDao(TransitGraphDao transitGraphDao) {
    _transitGraphDao = transitGraphDao;
  }

  @Autowired
  public void setRouteService(RouteService routeService) {
    _routeService = routeService;
  }

  @Autowired
  public void setRouteBeanService(RouteBeanService routeBeanService) {
    _routeBeanService = routeBeanService;
  }

  @Autowired
  public void setNarrativeService(NarrativeService narrativeService) {
    _narrativeService = narrativeService;
  }
  
  @Autowired
  public void setStopScheduleBeanService(StopScheduleBeanService stopScheduleBeanService) {
  		_stopScheduleBeanService = stopScheduleBeanService;
  }

  @Cacheable
  public StopBean getStopForId(AgencyAndId id) {

    StopEntry stop = _transitGraphDao.getStopEntryForId(id);
    StopNarrative narrative = _narrativeService.getStopForId(id);

    if (stop == null)
      throw new NoSuchStopServiceException(
          AgencyAndIdLibrary.convertToString(id));

    StopBean sb = new StopBean();
    fillStopBean(stop, narrative, sb);
    fillRoutesForStopBean(stop, sb);
    return sb;
  }
  
  @Cacheable
  public StopBean getStopForIdAndDate(AgencyAndId id, Date date) {

    StopEntry stop = _transitGraphDao.getStopEntryForId(id);
    StopNarrative narrative = _narrativeService.getStopForId(id);

    if (stop == null)
      throw new NoSuchStopServiceException(
          AgencyAndIdLibrary.convertToString(id));

    StopBean sb = new StopBean();
    fillStopBean(stop, narrative, sb);
    fillRoutesForStopBeanAndDate(stop, sb, date);
    return sb;
  }
  
  private boolean isScheduleServingStop(StopEntry stop, StopRouteScheduleBean schedule) {
		List<StopRouteDirectionScheduleBean> directions = schedule.getDirections();
		for (StopRouteDirectionScheduleBean direction : directions) {
			List<StopTimeInstanceBean> stopTimes = direction.getStopTimes();
			for (StopTimeInstanceBean stopTime : stopTimes) {
				if (stopTime.isDepartureEnabled()) {
					return true;
				}
			}
		}
		return false;
  }
  
  private void fillRoutesForStopBeanAndDate(StopEntry stop, StopBean sb, Date date) {

//    Set<AgencyAndId> routeCollectionIds = _routeService.getRouteCollectionIdsForStop(stop.getId());
//    List<RouteBean> routeBeansOld = new ArrayList<RouteBean>(routeCollectionIds.size());
//		for (AgencyAndId routeCollectionId : routeCollectionIds) {
//			RouteBean bean = _routeBeanService.getRouteForId(routeCollectionId);
//			routeBeansOld.add(bean);
//		}
//		Collections.sort(routeBeansOld, _routeBeanComparator);
    
    Set<RouteBean> routeBeans = new HashSet<RouteBean>();

    //Filter routes by time in stop and check pickuptype != 1
    ServiceDate serviceDate = new ServiceDate(date);
    List<StopRouteScheduleBean> scheduledArrivalsForStopAndDate = _stopScheduleBeanService.getScheduledArrivalsForStopAndDate(stop.getId(), serviceDate);
    
    for(StopRouteScheduleBean schedule : scheduledArrivalsForStopAndDate) {
    		if(isScheduleServingStop(stop, schedule)) {
    			routeBeans.add(schedule.getRoute());
    		}
    }

    List<RouteBean> routeBeansList = new ArrayList<>(routeBeans);
    Collections.sort(routeBeansList, _routeBeanComparator);

    sb.setRoutes(routeBeansList);
  }

  private void fillRoutesForStopBean(StopEntry stop, StopBean sb) {

    Set<AgencyAndId> routeCollectionIds = _routeService.getRouteCollectionIdsForStop(stop.getId());

    List<RouteBean> routeBeans = new ArrayList<RouteBean>(
        routeCollectionIds.size());

    for (AgencyAndId routeCollectionId : routeCollectionIds) {
      RouteBean bean = _routeBeanService.getRouteForId(routeCollectionId);
      routeBeans.add(bean);
    }
    
    Collections.sort(routeBeans, _routeBeanComparator);

    sb.setRoutes(routeBeans);
  }

  private void fillStopBean(StopEntry stop, StopNarrative narrative,
      StopBean bean) {

    bean.setId(ApplicationBeanLibrary.getId(stop.getId()));
    bean.setLat(stop.getStopLat());
    bean.setLon(stop.getStopLon());
    bean.setName(narrative.getName());
    bean.setCode(StringLibrary.getBestName(narrative.getCode(),
        stop.getId().getId()));
    bean.setLocationType(narrative.getLocationType());
    bean.setDirection(narrative.getDirection());
    bean.setWheelchairBoarding(stop.getWheelchairBoarding());
  }

  private static String getRouteBeanName(RouteBean bean) {
    return bean.getShortName() == null ? bean.getLongName()
        : bean.getShortName();
  }

  private static class RouteBeanComparator implements Comparator<RouteBean> {
    public int compare(RouteBean o1, RouteBean o2) {
      String name1 = getRouteBeanName(o1);
      String name2 = getRouteBeanName(o2);
      return NaturalStringOrder.compareNatural(name1, name2);
    }
  }
}
