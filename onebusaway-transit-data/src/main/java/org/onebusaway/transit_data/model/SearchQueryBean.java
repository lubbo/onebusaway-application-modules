/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
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
package org.onebusaway.transit_data.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import org.onebusaway.geospatial.model.CoordinateBounds;

@QueryBean
public class SearchQueryBean implements Serializable {

  private static final long serialVersionUID = 1L;

  public enum EQueryType implements Serializable {
    BOUNDS, CLOSEST, BOUNDS_OR_CLOSEST
  }

  private EQueryType type;

  private CoordinateBounds bounds;

  private String query;
  
  private String name;
  
  private List<String> stopIds;
  
  private Date date;

  private int maxCount;
  
  // default minimum lucene result score to keep
  private double minScoreToKeep = 1.0;

  public EQueryType getType() {
    return type;
  }

  public void setType(EQueryType type) {
    this.type = type;
  }

  public CoordinateBounds getBounds() {
    return bounds;
  }

  public void setBounds(CoordinateBounds bounds) {
    this.bounds = bounds;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public int getMaxCount() {
    return maxCount;
  }

  public void setMaxCount(int maxCount) {
    this.maxCount = maxCount;
  }

  public double getMinScoreToKeep() {
    return minScoreToKeep;
  }

  public void setMinScoreToKeep(double minScoreToKeep) {
    this.minScoreToKeep = minScoreToKeep;
  }

	public List<String> getStopIds() {
		return stopIds;
	}

	public void setStopIds(List<String> stopIds) {
		this.stopIds = stopIds;
	}
}