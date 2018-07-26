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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.lucene.queryParser.ParseException;
import org.onebusaway.collections.Min;
import org.onebusaway.exceptions.InvalidArgumentServiceException;
import org.onebusaway.exceptions.NoSuchAgencyServiceException;
import org.onebusaway.exceptions.ServiceException;
import org.onebusaway.geospatial.model.CoordinateBounds;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.geospatial.services.SphericalGeometryLibrary;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.SearchQueryBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.StopsBean;
import org.onebusaway.transit_data_federation.model.SearchResult;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.AgencyService;
import org.onebusaway.transit_data_federation.services.StopSearchService;
import org.onebusaway.transit_data_federation.services.beans.GeospatialBeanService;
import org.onebusaway.transit_data_federation.services.beans.StopBeanService;
import org.onebusaway.transit_data_federation.services.beans.StopsBeanService;
import org.onebusaway.transit_data_federation.services.transit_graph.AgencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.StopEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class StopsBeanServiceImpl implements StopsBeanService {

	private static Logger _log = LoggerFactory.getLogger(StopsBeanServiceImpl.class);

	private static final double MIN_SCORE = 1.0;

	@Autowired
	private StopSearchService _searchService;

	@Autowired
	private StopBeanService _stopBeanService;

	@Autowired
	private GeospatialBeanService _geospatialBeanService;

	@Autowired
	private TransitGraphDao _transitGraphDao;

	@Autowired
	private AgencyService _agencyService;

	@Override
	public StopsBean getStops(SearchQueryBean queryBean) throws ServiceException {
		String query = queryBean.getQuery();
		String name = queryBean.getName();
		List<String> stopIds = queryBean.getStopIds();

		StopsBean result = null;
		if (query != null) {
			result = getStopsByBoundsAndQuery(queryBean);
		} else if (name != null) {
			result = getStopsByName(queryBean);
		} else {
			result = getStopsByBounds(queryBean);

			if (stopIds != null) {

				List<String> idsToSearch = new ArrayList<>();
				for (String id : stopIds) {

					boolean alreadyRetrieved = false;
					for (StopBean stop : result.getStops()) {
						if (stop.getId().equals(id)) {
							alreadyRetrieved = true;
							break;
						}
					}
					if (!alreadyRetrieved) {
						idsToSearch.add(id);
					}
				}

				queryBean.setStopIds(idsToSearch);

				StopsBean stopsByIds = getStopsByIds(queryBean);
				stopsByIds.getStops().addAll(result.getStops());
				result.setStops(stopsByIds.getStops());
			}
		}

		return result;

	}

	private StopsBean getStopsByBounds(SearchQueryBean queryBean) throws ServiceException {

		CoordinateBounds bounds = queryBean.getBounds();
		Date date = queryBean.getDate();

		List<AgencyAndId> stopIds = _geospatialBeanService.getStopsByBounds(bounds);

		boolean limitExceeded = BeanServiceSupport.checkLimitExceeded(stopIds, queryBean.getMaxCount());
		List<StopBean> stopBeans = new ArrayList<StopBean>();

		for (AgencyAndId stopId : stopIds) {
			StopBean stopBean = (date == null) ? _stopBeanService.getStopForId(stopId)
					: _stopBeanService.getStopForIdAndDate(stopId, date);
			if (stopBean == null)
				throw new ServiceException();

			/**
			 * If the stop doesn't have any routes actively serving it, don't include it in
			 * the results
			 */
			if (stopBean.getRoutes().isEmpty())
				continue;

			stopBeans.add(stopBean);
		}

		return constructResult(stopBeans, limitExceeded);
	}

	private StopsBean getStopsByBoundsAndQuery(SearchQueryBean queryBean) throws ServiceException {

		CoordinateBounds bounds = queryBean.getBounds();
		String query = queryBean.getQuery();
		int maxCount = queryBean.getMaxCount();

		CoordinatePoint center = SphericalGeometryLibrary.getCenterOfBounds(bounds);

		SearchResult<AgencyAndId> stops;
		try {
			stops = _searchService.searchForStopsByCode(query, 10, MIN_SCORE);
		} catch (ParseException e) {
			throw new InvalidArgumentServiceException("query", "queryParseError");
		} catch (IOException e) {
			_log.error("error executing stop search: query=" + query, e);
			e.printStackTrace();
			throw new ServiceException();
		}

		Min<StopBean> closest = new Min<StopBean>();
		List<StopBean> stopBeans = new ArrayList<StopBean>();

		for (AgencyAndId aid : stops.getResults()) {
			StopBean stopBean = _stopBeanService.getStopForId(aid);
			if (bounds.contains(stopBean.getLat(), stopBean.getLon()))
				stopBeans.add(stopBean);
			double distance = SphericalGeometryLibrary.distance(center.getLat(), center.getLon(), stopBean.getLat(),
					stopBean.getLon());
			closest.add(distance, stopBean);
		}

		boolean limitExceeded = BeanServiceSupport.checkLimitExceeded(stopBeans, maxCount);

		// If nothing was found in range, add the closest result
		if (stopBeans.isEmpty() && !closest.isEmpty())
			stopBeans.add(closest.getMinElement());

		return constructResult(stopBeans, limitExceeded);
	}

	private StopsBean getStopsByIds(SearchQueryBean queryBean) throws ServiceException {

		int maxCount = queryBean.getMaxCount();
		Date date = queryBean.getDate();
		double minScoreToKeep = queryBean.getMinScoreToKeep();

		List<String> stopIds = queryBean.getStopIds();

		List<AgencyAndId> stops = new ArrayList<>();

		for (String id : stopIds) {
			AgencyAndId agencyAndId = AgencyAndId.convertFromString(id);
			stops.add(agencyAndId);
		}

		// SearchResult<AgencyAndId> stops = new SearchResult<>();
		// try {
		// if (stopIds != null) {
		// stops = _searchService.searchForStopsById(stopIds, maxCount, minScoreToKeep);
		// }
		// } catch (ParseException e) {
		// throw new InvalidArgumentServiceException("ids", "queryParseError");
		// } catch (IOException e) {
		// _log.error("error executing stop search: ids=" + stopIds, e);
		// e.printStackTrace();
		// throw new ServiceException();
		// }

		List<StopBean> stopBeans = new ArrayList<StopBean>();

		// for (AgencyAndId aid : stops.getResults()) {
		for (AgencyAndId aid : stops) {
			StopBean stopBean = (date == null) ? _stopBeanService.getStopForId(aid)
					: _stopBeanService.getStopForIdAndDate(aid, date);

			stopBeans.add(stopBean);

		}

		boolean limitExceeded = BeanServiceSupport.checkLimitExceeded(stopBeans, maxCount);

		return constructResult(stopBeans, limitExceeded);
	}

	private StopsBean getStopsByName(SearchQueryBean queryBean) throws ServiceException {

		String name = queryBean.getName();
		int maxCount = queryBean.getMaxCount();
		Date date = queryBean.getDate();

		List<StopBean> stopBeans = new ArrayList<StopBean>();

		List<String> allAgencyIds = _agencyService.getAllAgencyIds();
		for (String agencyId : allAgencyIds) {

			AgencyEntry agency = _transitGraphDao.getAgencyForId(agencyId);
			if (agency == null)
				throw new NoSuchAgencyServiceException(agencyId);

			for (StopEntry stop : agency.getStops()) {
				AgencyAndId agencyAndId = stop.getId();
				StopBean stopBean = (date == null) ? _stopBeanService.getStopForId(agencyAndId)
						: _stopBeanService.getStopForIdAndDate(agencyAndId, date);
				String stopName = stopBean.getName();
				if (stopName.toUpperCase().contains(name.toUpperCase())) {
					stopBeans.add(stopBean);
				}
			}

		}

		boolean limitExceeded = BeanServiceSupport.checkLimitExceeded(stopBeans, maxCount);

		Collections.sort(stopBeans, new Comparator<StopBean>() {

			@Override
			public int compare(StopBean o1, StopBean o2) {
				return o1.getName().toUpperCase().compareTo(o2.getName().toUpperCase());
			}

		});

		StopsBean stopsBean = new StopsBean();
		stopsBean.setStops(stopBeans);
		stopsBean.setLimitExceeded(limitExceeded);
		return stopsBean;
	}

	@Override
	public ListBean<String> getStopsIdsForAgencyId(String agencyId) {
		AgencyEntry agency = _transitGraphDao.getAgencyForId(agencyId);
		if (agency == null)
			throw new NoSuchAgencyServiceException(agencyId);
		List<String> ids = new ArrayList<String>();
		for (StopEntry stop : agency.getStops()) {
			AgencyAndId id = stop.getId();
			ids.add(AgencyAndIdLibrary.convertToString(id));
		}
		return new ListBean<String>(ids, false);
	}

	private StopsBean constructResult(List<StopBean> stopBeans, boolean limitExceeded) {

		Collections.sort(stopBeans, new StopBeanIdComparator());

		StopsBean result = new StopsBean();
		result.setStops(stopBeans);
		result.setLimitExceeded(limitExceeded);
		return result;
	}

}
