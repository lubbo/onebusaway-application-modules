package org.onebusaway.federations.impl;

import java.util.Set;

import org.onebusaway.exceptions.ServiceAreaServiceException;
import org.onebusaway.federations.FederatedService;
import org.onebusaway.federations.FederatedServiceCollection;
import org.onebusaway.geospatial.model.CoordinateBounds;

public abstract class AbstractFederatedServiceCollectionWrapperImpl implements
    FederatedServiceCollection {

  @Override
  public Set<FederatedService> getAllServices() {
    return getCollection().getAllServices();
  }

  @Override
  public FederatedService getServiceForAgencyId(String agencyId)
      throws ServiceAreaServiceException {
    return getCollection().getServiceForAgencyId(agencyId);
  }

  @Override
  public FederatedService getServiceForAgencyIds(Iterable<String> agencyIds)
      throws ServiceAreaServiceException {
    return getCollection().getServiceForAgencyIds(agencyIds);
  }

  @Override
  public FederatedService getServiceForBounds(CoordinateBounds bounds)
      throws ServiceAreaServiceException {
    return getCollection().getServiceForBounds(bounds);
  }

  @Override
  public FederatedService getServiceForBounds(double lat1, double lon1,
      double lat2, double lon2) throws ServiceAreaServiceException {
    return getCollection().getServiceForBounds(lat1, lon1, lat2, lon2);
  }

  @Override
  public FederatedService getServiceForLocation(double lat, double lon)
      throws ServiceAreaServiceException {
    return getCollection().getServiceForLocation(lat, lon);
  }

  /****
   * Protected Methods
   ****/

  protected abstract FederatedServiceCollection getCollection();
}