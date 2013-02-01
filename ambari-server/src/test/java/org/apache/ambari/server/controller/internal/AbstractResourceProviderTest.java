/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ambari.server.controller.internal;

import org.apache.ambari.server.controller.*;
import org.apache.ambari.server.controller.utilities.PredicateBuilder;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.controller.RequestStatusResponse;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.ResourceProvider;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.junit.Assert;
import org.junit.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resource provider tests.
 */
public class AbstractResourceProviderTest {

  @Test
  public void testCreateClusterResources() throws Exception{
    Resource.Type type = Resource.Type.Cluster;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    RequestStatusResponse response = createNiceMock(RequestStatusResponse.class);

    managementController.createCluster(Matchers.clusterRequest(null, "Cluster100", "HDP-0.1", null));
    managementController.createCluster(Matchers.clusterRequest(99L, null, "HDP-0.1", null));

    // replay
    replay(managementController, response);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    TestObserver observer = new TestObserver();

    ((ObservableResourceProvider)provider).addObserver(observer);

    // add the property map to a set for the request.  add more maps for multiple creates
    Set<Map<String, Object>> propertySet = new LinkedHashSet<Map<String, Object>>();

    // Cluster 1: create a map of properties for the request
    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    // add the cluster name to the properties map
    properties.put(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID, "Cluster100");

    // add the version to the properties map
    properties.put(ClusterResourceProvider.CLUSTER_VERSION_PROPERTY_ID, "HDP-0.1");

    propertySet.add(properties);

    // Cluster 2: create a map of properties for the request
    properties = new LinkedHashMap<String, Object>();

    // add the cluster id to the properties map
    properties.put(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID, 99L);

    // add the version to the properties map
    properties.put(ClusterResourceProvider.CLUSTER_VERSION_PROPERTY_ID, "HDP-0.1");

    propertySet.add(properties);

    // create the request
    Request request = PropertyHelper.getCreateRequest(propertySet);

    provider.createResources(request);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    Assert.assertNotNull(lastEvent);
    Assert.assertEquals(Resource.Type.Cluster, lastEvent.getResourceType());
    Assert.assertEquals(ResourceProviderEvent.Type.Create, lastEvent.getType());
    Assert.assertEquals(request, lastEvent.getRequest());
    Assert.assertNull(lastEvent.getPredicate());

    // verify
    verify(managementController, response);
  }

  @Test
  public void testGetClusterResources() throws Exception{
    Resource.Type type = Resource.Type.Cluster;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);

    Set<ClusterResponse> allResponse = new HashSet<ClusterResponse>();
    allResponse.add(new ClusterResponse(100L, "Cluster100", null, null));
    allResponse.add(new ClusterResponse(101L, "Cluster101", null, null));
    allResponse.add(new ClusterResponse(102L, "Cluster102", null, null));
    allResponse.add(new ClusterResponse(103L, "Cluster103", null, null));
    allResponse.add(new ClusterResponse(104L, "Cluster104", null, null));

    Set<ClusterResponse> nameResponse = new HashSet<ClusterResponse>();
    nameResponse.add(new ClusterResponse(102L, "Cluster102", null, null));

    Set<ClusterResponse> idResponse = new HashSet<ClusterResponse>();
    idResponse.add(new ClusterResponse(103L, "Cluster103", null, null));

    // set expectations
    expect(managementController.getClusters(EasyMock.<Set<ClusterRequest>>anyObject())).andReturn(allResponse).once();
    expect(managementController.getClusters(EasyMock.<Set<ClusterRequest>>anyObject())).andReturn(nameResponse).once();
    expect(managementController.getClusters(EasyMock.<Set<ClusterRequest>>anyObject())).andReturn(idResponse).once();

    // replay
    replay(managementController);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    Set<String> propertyIds = new HashSet<String>();

    propertyIds.add(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID);
    propertyIds.add(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID);

    // create the request
    Request request = PropertyHelper.getReadRequest(propertyIds);

    // get all ... no predicate
    Set<Resource> resources = provider.getResources(request, null);

    Assert.assertEquals(5, resources.size());
    for (Resource resource : resources) {
      Long id = (Long) resource.getPropertyValue(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID);
      String name = (String) resource.getPropertyValue(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID);
      Assert.assertEquals(name, "Cluster" + id);
    }

    // get cluster named Cluster102
    Predicate  predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID).equals("Cluster102").toPredicate();
    resources = provider.getResources(request, predicate);

    Assert.assertEquals(1, resources.size());
    Assert.assertEquals(102L, resources.iterator().next().getPropertyValue(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID));
    Assert.assertEquals("Cluster102", resources.iterator().next().getPropertyValue(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID));

    // get cluster with id == 103
    predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID).equals(103L).toPredicate();
    resources = provider.getResources(request, predicate);

    Assert.assertEquals(1, resources.size());
    Assert.assertEquals(103L, resources.iterator().next().getPropertyValue(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID));
    Assert.assertEquals("Cluster103", resources.iterator().next().getPropertyValue(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID));

    // verify
    verify(managementController);
  }

  @Test
  public void testUpdateClusterResources() throws Exception{
    Resource.Type type = Resource.Type.Cluster;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    RequestStatusResponse response = createNiceMock(RequestStatusResponse.class);

    Set<ClusterResponse> nameResponse = new HashSet<ClusterResponse>();
    nameResponse.add(new ClusterResponse(102L, "Cluster102", null, null));

    // set expectations
    expect(managementController.getClusters(EasyMock.<Set<ClusterRequest>>anyObject())).andReturn(nameResponse).once();
    expect(managementController.updateCluster(Matchers.clusterRequest(102L, "Cluster102", "HDP-0.1", null))).andReturn(response).once();
    expect(managementController.updateCluster(Matchers.clusterRequest(103L, null, "HDP-0.1", null))).andReturn(response).once();

    // replay
    replay(managementController, response);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    TestObserver observer = new TestObserver();

    ((ObservableResourceProvider)provider).addObserver(observer);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    properties.put(ClusterResourceProvider.CLUSTER_VERSION_PROPERTY_ID, "HDP-0.1");

    // create the request
    Request request = PropertyHelper.getUpdateRequest(properties);

    // update the cluster named Cluster102
    Predicate  predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID).equals("Cluster102").toPredicate();
    provider.updateResources(request, predicate);

    // update the cluster where id == 103
    predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID).equals(103L).toPredicate();
    provider.updateResources(request, predicate);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    Assert.assertNotNull(lastEvent);
    Assert.assertEquals(Resource.Type.Cluster, lastEvent.getResourceType());
    Assert.assertEquals(ResourceProviderEvent.Type.Update, lastEvent.getType());
    Assert.assertEquals(request, lastEvent.getRequest());
    Assert.assertEquals(predicate, lastEvent.getPredicate());

    // verify
    verify(managementController, response);
  }

  @Test
  public void testDeleteClusterResources() throws Exception{
    Resource.Type type = Resource.Type.Cluster;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    RequestStatusResponse response = createNiceMock(RequestStatusResponse.class);

    // set expectations
    managementController.deleteCluster(Matchers.clusterRequest(null, "Cluster102", null, null));
    managementController.deleteCluster(Matchers.clusterRequest(103L, null, null, null));

    // replay
    replay(managementController, response);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    TestObserver observer = new TestObserver();

    ((ObservableResourceProvider)provider).addObserver(observer);

    // delete the cluster named Cluster102
    Predicate  predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_NAME_PROPERTY_ID).equals("Cluster102").toPredicate();
    provider.deleteResources(predicate);

    // delete the cluster where id == 103
    predicate = new PredicateBuilder().property(ClusterResourceProvider.CLUSTER_ID_PROPERTY_ID).equals(103L).toPredicate();
    provider.deleteResources(predicate);

    ResourceProviderEvent lastEvent = observer.getLastEvent();
    Assert.assertNotNull(lastEvent);
    Assert.assertEquals(Resource.Type.Cluster, lastEvent.getResourceType());
    Assert.assertEquals(ResourceProviderEvent.Type.Delete, lastEvent.getType());
    Assert.assertEquals(predicate, lastEvent.getPredicate());
    Assert.assertNull(lastEvent.getRequest());

    // verify
    verify(managementController, response);
  }

  @Test
  public void testCreateServiceResources() throws Exception{
    Resource.Type type = Resource.Type.Service;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    RequestStatusResponse response = createNiceMock(RequestStatusResponse.class);

    managementController.createServices(Matchers.serviceRequestSet("Cluster100", "Service100", null, "DEPLOYED"));

    // replay
    replay(managementController, response);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    // add the property map to a set for the request.  add more maps for multiple creates
    Set<Map<String, Object>> propertySet = new LinkedHashSet<Map<String, Object>>();

    // Service 1: create a map of properties for the request
    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    // add properties to the request map
    properties.put(ServiceResourceProvider.SERVICE_CLUSTER_NAME_PROPERTY_ID, "Cluster100");
    properties.put(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID, "Service100");
    properties.put(ServiceResourceProvider.SERVICE_SERVICE_STATE_PROPERTY_ID, "DEPLOYED");

    propertySet.add(properties);

    // create the request
    Request request = PropertyHelper.getCreateRequest(propertySet);

    provider.createResources(request);

    // verify
    verify(managementController, response);
  }

  @Test
  public void testGetServiceResources() throws Exception{
    Resource.Type type = Resource.Type.Service;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);

    Set<ServiceResponse> allResponse = new HashSet<ServiceResponse>();
    allResponse.add(new ServiceResponse(100L, "Cluster100", "Service100", null, "HDP-0.1", "DEPLOYED"));
    allResponse.add(new ServiceResponse(100L, "Cluster100", "Service101", null, "HDP-0.1", "DEPLOYED"));
    allResponse.add(new ServiceResponse(100L, "Cluster100", "Service102", null, "HDP-0.1", "DEPLOYED"));
    allResponse.add(new ServiceResponse(100L, "Cluster100", "Service103", null, "HDP-0.1", "DEPLOYED"));
    allResponse.add(new ServiceResponse(100L, "Cluster100", "Service104", null, "HDP-0.1", "DEPLOYED"));

    Set<ServiceResponse> nameResponse = new HashSet<ServiceResponse>();
    nameResponse.add(new ServiceResponse(100L, "Cluster100", "Service102", null, "HDP-0.1", "DEPLOYED"));

    Set<ServiceResponse> stateResponse = new HashSet<ServiceResponse>();
    stateResponse.add(new ServiceResponse(100L, "Cluster100", "Service100", null, "HDP-0.1", "DEPLOYED"));
    stateResponse.add(new ServiceResponse(100L, "Cluster100", "Service102", null, "HDP-0.1", "DEPLOYED"));
    stateResponse.add(new ServiceResponse(100L, "Cluster100", "Service104", null, "HDP-0.1", "DEPLOYED"));

    // set expectations
    expect(managementController.getServices(EasyMock.<Set<ServiceRequest>>anyObject())).andReturn(allResponse).once();
    expect(managementController.getServices(EasyMock.<Set<ServiceRequest>>anyObject())).andReturn(nameResponse).once();
    expect(managementController.getServices(EasyMock.<Set<ServiceRequest>>anyObject())).andReturn(stateResponse).once();

    // replay
    replay(managementController);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    Set<String> propertyIds = new HashSet<String>();

    propertyIds.add(ServiceResourceProvider.SERVICE_CLUSTER_NAME_PROPERTY_ID);
    propertyIds.add(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID);

    // create the request
    Request request = PropertyHelper.getReadRequest(propertyIds);
    // get all ... no predicate
    Set<Resource> resources = provider.getResources(request, null);

    Assert.assertEquals(5, resources.size());
    Set<String> names = new HashSet<String>();
    for (Resource resource : resources) {
      String clusterName = (String) resource.getPropertyValue(ServiceResourceProvider.SERVICE_CLUSTER_NAME_PROPERTY_ID);
      Assert.assertEquals("Cluster100", clusterName);
      names.add((String) resource.getPropertyValue(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID));
    }
    // Make sure that all of the response objects got moved into resources
    for (ServiceResponse serviceResponse : allResponse ) {
      Assert.assertTrue(names.contains(serviceResponse.getServiceName()));
    }

    // get service named Service102
    Predicate  predicate = new PredicateBuilder().property(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID).equals("Service102").toPredicate();
    request = PropertyHelper.getReadRequest("ServiceInfo");
    resources = provider.getResources(request, predicate);

    Assert.assertEquals(1, resources.size());
    Assert.assertEquals("Service102", resources.iterator().next().getPropertyValue(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID));

    // get services where state == "DEPLOYED"
    predicate = new PredicateBuilder().property(ServiceResourceProvider.SERVICE_SERVICE_STATE_PROPERTY_ID).equals("DEPLOYED").toPredicate();
    request = PropertyHelper.getReadRequest(propertyIds);
    resources = provider.getResources(request, predicate);

    Assert.assertEquals(3, resources.size());
    names = new HashSet<String>();
    for (Resource resource : resources) {
      String clusterName = (String) resource.getPropertyValue(ServiceResourceProvider.SERVICE_CLUSTER_NAME_PROPERTY_ID);
      Assert.assertEquals("Cluster100", clusterName);
      names.add((String) resource.getPropertyValue(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID));
    }
    // Make sure that all of the response objects got moved into resources
    for (ServiceResponse serviceResponse : stateResponse ) {
      Assert.assertTrue(names.contains(serviceResponse.getServiceName()));
    }

    // verify
    verify(managementController);
  }

  @Test
  public void testUpdateServiceResources() throws Exception{
    Resource.Type type = Resource.Type.Service;

    AmbariManagementController managementController = createMock(AmbariManagementController.class);
    RequestStatusResponse response = createNiceMock(RequestStatusResponse.class);

    // set expectations
    expect(managementController.updateServices(EasyMock.<Set<ServiceRequest>>anyObject())).andReturn(response).once();

    // replay
    replay(managementController, response);

    ResourceProvider provider = AbstractResourceProvider.getResourceProvider(
        type,
        PropertyHelper.getPropertyIds(type),
        PropertyHelper.getKeyPropertyIds(type),
        managementController);

    // add the property map to a set for the request.
    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    properties.put(ServiceResourceProvider.SERVICE_SERVICE_STATE_PROPERTY_ID, "DEPLOYED");

    // create the request
    Request request = PropertyHelper.getUpdateRequest(properties);

    // update the service named Service102
    Predicate  predicate = new PredicateBuilder().property(ServiceResourceProvider.SERVICE_CLUSTER_NAME_PROPERTY_ID).equals("Cluster100").
        and().property(ServiceResourceProvider.SERVICE_SERVICE_NAME_PROPERTY_ID).equals("Service102").toPredicate();
    provider.updateResources(request, predicate);

    // verify
    verify(managementController, response);
  }

  @Test
  public void testCheckPropertyIds() {
    Set<String> propertyIds = new HashSet<String>();
    propertyIds.add("foo");
    propertyIds.add("cat1/foo");
    propertyIds.add("cat2/bar");
    propertyIds.add("cat2/baz");
    propertyIds.add("cat3/sub1/bam");
    propertyIds.add("cat4/sub2/sub3/bat");
    propertyIds.add("cat5/subcat5/map");

    Map<Resource.Type, String> keyPropertyIds = new HashMap<Resource.Type, String>();

    AmbariManagementController managementController = createMock(AmbariManagementController.class);

    AbstractResourceProvider provider =
        (AbstractResourceProvider) AbstractResourceProvider.getResourceProvider(
            Resource.Type.Service,
            propertyIds,
            keyPropertyIds,
            managementController);

    Set<String> unsupported = provider.checkPropertyIds(Collections.singleton("foo"));
    Assert.assertTrue(unsupported.isEmpty());

    // note that key is not in the set of known property ids.  We allow it if its parent is a known property.
    // this allows for Map type properties where we want to treat the entries as individual properties
    Assert.assertTrue(provider.checkPropertyIds(Collections.singleton("cat5/subcat5/map/key")).isEmpty());

    unsupported = provider.checkPropertyIds(Collections.singleton("bar"));
    Assert.assertEquals(1, unsupported.size());
    Assert.assertTrue(unsupported.contains("bar"));

    unsupported = provider.checkPropertyIds(Collections.singleton("cat1/foo"));
    Assert.assertTrue(unsupported.isEmpty());

    unsupported = provider.checkPropertyIds(Collections.singleton("cat1"));
    Assert.assertTrue(unsupported.isEmpty());
  }

  @Test
  public void testGetPropertyIds() {
    Set<String> propertyIds = new HashSet<String>();
    propertyIds.add("p1");
    propertyIds.add("foo");
    propertyIds.add("cat1/foo");
    propertyIds.add("cat2/bar");
    propertyIds.add("cat2/baz");
    propertyIds.add("cat3/sub1/bam");
    propertyIds.add("cat4/sub2/sub3/bat");

    Map<Resource.Type, String> keyPropertyIds = new HashMap<Resource.Type, String>();

    AmbariManagementController managementController = createMock(AmbariManagementController.class);

    AbstractResourceProvider provider =
        (AbstractResourceProvider) AbstractResourceProvider.getResourceProvider(
            Resource.Type.Service,
            propertyIds,
            keyPropertyIds,
            managementController);

    Set<String> supportedPropertyIds = provider.getPropertyIds();
    Assert.assertTrue(supportedPropertyIds.containsAll(propertyIds));
  }


  // ----- helper methods ----------------------------------------------------

  public static class Matchers
  {
    public static ClusterRequest clusterRequest(Long clusterId, String clusterName, String stackVersion, Set<String> hostNames)
    {
      EasyMock.reportMatcher(new ClusterRequestMatcher(clusterId, clusterName, stackVersion, hostNames));
      return null;
    }

    public static Set<ServiceRequest> serviceRequestSet(String clusterName, String serviceName, Map<String, String> configVersions, String desiredState)
    {
      EasyMock.reportMatcher(new ServiceRequestSetMatcher(clusterName, serviceName, configVersions, desiredState));
      return null;
    }

    public static Set<ServiceComponentRequest> componentRequestSet(String clusterName, String serviceName, String componentName,
                                                           Map<String, String> configVersions, String desiredState)
    {
      EasyMock.reportMatcher(new ComponentRequestSetMatcher(clusterName, serviceName, componentName, configVersions, desiredState));
      return null;
    }

    public static ConfigurationRequest configurationRequest(String clusterName, String type, String tag, Map<String, String> configs)
    {
      EasyMock.reportMatcher(new ConfigurationRequestMatcher(clusterName, type, tag, configs));
      return null;
    }
  }

  public static boolean eq(Object left, Object right) {
    return  left == null ? right == null : right != null && left.equals(right);
  }


  // ----- inner classes -----------------------------------------------------

  public static class ClusterRequestMatcher extends ClusterRequest implements IArgumentMatcher {

    public ClusterRequestMatcher(Long clusterId, String clusterName, String stackVersion, Set<String> hostNames) {
      super(clusterId, clusterName, stackVersion, hostNames);
    }

    @Override
    public boolean matches(Object o) {
      return o instanceof ClusterRequest &&
          eq(((ClusterRequest) o).getClusterId(), getClusterId()) &&
          eq(((ClusterRequest) o).getClusterName(), getClusterName()) &&
          eq(((ClusterRequest) o).getStackVersion(), getStackVersion()) &&
          eq(((ClusterRequest) o).getHostNames(), getHostNames());
    }

    @Override
    public void appendTo(StringBuffer stringBuffer) {
      stringBuffer.append("ClusterRequestMatcher(" + "" + ")");
    }
  }

  public static class ServiceRequestSetMatcher extends HashSet<ServiceRequest> implements IArgumentMatcher {

    private final ServiceRequest serviceRequest;

    public ServiceRequestSetMatcher(String clusterName, String serviceName, Map<String, String> configVersions, String desiredState) {
      this.serviceRequest = new ServiceRequest(clusterName, serviceName, configVersions, desiredState);
      add(this.serviceRequest);
    }

    @Override
    public boolean matches(Object o) {
      if (!(o instanceof Set)) {
        return false;
      }

      Set set = (Set) o;

      if (set.size() != 1) {
        return false;
      }

      Object request = set.iterator().next();

      return request instanceof ServiceRequest &&
          eq(((ServiceRequest) request).getClusterName(), serviceRequest.getClusterName()) &&
          eq(((ServiceRequest) request).getServiceName(), serviceRequest.getServiceName()) &&
          eq(((ServiceRequest) request).getConfigVersions(), serviceRequest.getConfigVersions()) &&
          eq(((ServiceRequest) request).getDesiredState(), serviceRequest.getDesiredState());
    }

    @Override
    public void appendTo(StringBuffer stringBuffer) {
      stringBuffer.append("ServiceRequestSetMatcher(" + "" + ")");
    }
  }

  public static class ComponentRequestSetMatcher extends HashSet<ServiceComponentRequest> implements IArgumentMatcher {

    private final ServiceComponentRequest serviceComponentRequest;

    public ComponentRequestSetMatcher(String clusterName, String serviceName, String componentName,
                                   Map<String, String> configVersions, String desiredState) {
      this.serviceComponentRequest = new ServiceComponentRequest(clusterName, serviceName, componentName, configVersions, desiredState);
      add(this.serviceComponentRequest);
    }

    @Override
    public boolean matches(Object o) {

      if (!(o instanceof Set)) {
        return false;
      }

      Set set = (Set) o;

      if (set.size() != 1) {
        return false;
      }

      Object request = set.iterator().next();

      return request instanceof ServiceComponentRequest &&
          eq(((ServiceComponentRequest) request).getClusterName(), serviceComponentRequest.getClusterName()) &&
          eq(((ServiceComponentRequest) request).getServiceName(), serviceComponentRequest.getServiceName()) &&
          eq(((ServiceComponentRequest) request).getComponentName(), serviceComponentRequest.getComponentName()) &&
          eq(((ServiceComponentRequest) request).getConfigVersions(), serviceComponentRequest.getConfigVersions()) &&
          eq(((ServiceComponentRequest) request).getDesiredState(), serviceComponentRequest.getDesiredState());
    }

    @Override
    public void appendTo(StringBuffer stringBuffer) {
      stringBuffer.append("ComponentRequestMatcher(" + "" + ")");
    }
  }

  public static class ConfigurationRequestMatcher extends ConfigurationRequest implements IArgumentMatcher {

    public ConfigurationRequestMatcher(String clusterName, String type, String tag, Map<String, String> configs) {
      super(clusterName, type, tag, configs);
    }

    @Override
    public boolean matches(Object o) {
      return o instanceof ConfigurationRequest &&
          eq(((ConfigurationRequest) o).getClusterName(), getClusterName()) &&
          eq(((ConfigurationRequest) o).getType(), getType()) &&
          eq(((ConfigurationRequest) o).getVersionTag(), getVersionTag()) &&
          eq(((ConfigurationRequest) o).getConfigs(), getConfigs());

    }

    @Override
    public void appendTo(StringBuffer stringBuffer) {
      stringBuffer.append("ConfigurationRequestMatcher(" + "" + ")");
    }
  }

  public class TestObserver implements ResourceProviderObserver {

    ResourceProviderEvent lastEvent = null;

    @Override
    public void update(ResourceProviderEvent event) {
      lastEvent = event;
    }

    public ResourceProviderEvent getLastEvent() {
      return lastEvent;
    }
  }
}
