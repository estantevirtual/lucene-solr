package org.apache.solr.core;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.util.StrUtils;
import org.noggit.CharArr;
import org.noggit.JSONParser;
import org.noggit.JSONWriter;
import org.noggit.ObjectBuilder;

public class ConfigOverlay {
  private final int znodeVersion ;
  private Map<String, Object> data;
  private Map<String,Object> props;
  private Map<String,Object> userProps;

  public ConfigOverlay(Map<String,Object> jsonObj, int znodeVersion){
    if(jsonObj == null) jsonObj= Collections.EMPTY_MAP;
    this.znodeVersion = znodeVersion;
    data = Collections.unmodifiableMap(jsonObj);
    props = (Map<String, Object>) data.get("props");
    if(props == null) props= Collections.EMPTY_MAP;
    userProps = (Map<String, Object>) data.get("userProps");
    if(userProps == null) userProps= Collections.EMPTY_MAP;

  }
  public Object getXPathProperty(String xpath){
    return getXPathProperty(xpath,true);
  }

  public Object getXPathProperty(String xpath, boolean onlyPrimitive) {
    List<String> hierarchy = checkEditable(xpath, true, false);
    if(hierarchy == null) return null;
    return getObjectByPath(props, onlyPrimitive, hierarchy);
  }

  public static Object getObjectByPath(Map root, boolean onlyPrimitive, List<String> hierarchy) {
    Map obj = root;
    for (int i = 0; i < hierarchy.size(); i++) {
      String s = hierarchy.get(i);
      if(i < hierarchy.size()-1){
        obj = (Map) obj.get(s);
        if(obj == null) return null;
      } else {
        Object val = obj.get(s);
        if (onlyPrimitive && val instanceof Map) {
          return null;
        }
        return val;
      }
    }

    return false;
  }

  public ConfigOverlay setUserProperty(String key, Object val){
    Map copy = new LinkedHashMap(userProps);
    copy.put(key,val);
    Map<String, Object> jsonObj = new LinkedHashMap<>(this.data);
    jsonObj.put("userProps", copy);
    return new ConfigOverlay(jsonObj, znodeVersion);
  }
  public ConfigOverlay unsetUserProperty(String key){
    if(!userProps.containsKey(key)) return this;
    Map copy = new LinkedHashMap(userProps);
    copy.remove(key);
    Map<String, Object> jsonObj = new LinkedHashMap<>(this.data);
    jsonObj.put("userProps", copy);
    return new ConfigOverlay(jsonObj, znodeVersion);
  }

  public ConfigOverlay setProperty(String name, Object val) {
    List<String> hierarchy  = checkEditable(name,false, true);
    Map deepCopy = getDeepCopy(props);
    Map obj = deepCopy;
    for (int i = 0; i < hierarchy.size(); i++) {
      String s = hierarchy.get(i);
      if (i < hierarchy.size()-1) {
        if(obj.get(s) == null || (!(obj.get(s) instanceof Map))) {
          obj.put(s, new LinkedHashMap<>());
        }
        obj = (Map) obj.get(s);
      } else {
        obj.put(s,val);
      }
    }

    Map<String, Object> jsonObj = new LinkedHashMap<>(this.data);
    jsonObj.put("props", deepCopy);

    return new ConfigOverlay(jsonObj, znodeVersion);
  }



  private Map getDeepCopy(Map map) {
    return (Map) ZkStateReader.fromJSON(ZkStateReader.toJSON(map));
  }

  public static final String NOT_EDITABLE = "''{0}'' is not an editable property";

  private List<String> checkEditable(String propName, boolean isXPath, boolean failOnError) {
    LinkedList<String> hierarchy = new LinkedList<>();
    if(!isEditableProp(propName, isXPath,hierarchy)) {
      if(failOnError) throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, MessageFormat.format( NOT_EDITABLE,propName));
      else return null;
    }
    return hierarchy;

  }

  public ConfigOverlay unsetProperty(String name) {
    List<String> hierarchy  = checkEditable(name,false, true);
    Map deepCopy = getDeepCopy(props);
    Map obj = deepCopy;
    for (int i = 0; i < hierarchy.size(); i++) {
      String s = hierarchy.get(i);
      if (i < hierarchy.size()-1) {
        if(obj.get(s) == null || (!(obj.get(s) instanceof Map))) {
          return this;
        }
        obj = (Map) obj.get(s);
      } else {
        obj.remove(s);
      }
    }

    Map<String, Object> jsonObj = new LinkedHashMap<>(this.data);
    jsonObj.put("props", deepCopy);

    return new ConfigOverlay(jsonObj, znodeVersion);
  }

  public byte[] toByteArray() {
    return ZkStateReader.toJSON(data);
  }


  public int getZnodeVersion(){
    return znodeVersion;
  }

  @Override
  public String toString() {
    CharArr out = new CharArr();
    try {
      new JSONWriter(out, 2).write(data);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out.toString();
  }

  public  Map toOutputFormat() {
    Map result = new LinkedHashMap();
    result.put("version",znodeVersion);
    result.putAll(data);
    return result;
  }


  public static final String RESOURCE_NAME = "configoverlay.json";

  private static final Long XML_ATTR = 0L;
  private static final Long XML_NODE = 1L;

  private static Map editable_prop_map ;
  public static final String MAPPING = "{ updateHandler : {" +
      "                 autoCommit : { maxDocs:1, maxTime:1, openSearcher:1 }," +
      "                 autoSoftCommit : { maxDocs:1, maxTime :1}," +
      "                 commitWithin : {softCommit:1}," +
      "                 commitIntervalLowerBound:1," +
      "                 indexWriter : {closeWaitsForMerges:1}" +
      "                 }," +
      " query : {" +
      "          filterCache : {class:0, size:0, initialSize:0 , autowarmCount:0 , regenerator:0}," +
      "          queryResultCache :{class:0, size:0, initialSize:0,autowarmCount:0,regenerator:0}," +
      "          documentCache :{class:0, size:0, initialSize:0 ,autowarmCount:0,regenerator:0}," +
      "          fieldValueCache :{class:0, size:0, initialSize:0 ,autowarmCount:0,regenerator:0}" +
      "}}";
  static{
    try {
      editable_prop_map =  (Map)new ObjectBuilder(new JSONParser(new StringReader(
          MAPPING))).getObject();
    } catch (IOException e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "error parsing mapping ", e);
    }
  }


  public static boolean isEditableProp(String path, boolean isXpath, List<String> hierarchy) {
    List<String> parts = StrUtils.splitSmart(path, isXpath? '/':'.');
    Object obj = editable_prop_map;
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      boolean isAttr = isXpath && part.startsWith("@");
      if(isAttr){
        part = part.substring(1);
      }
      if(hierarchy != null) hierarchy.add(part);
      if(obj ==null) return false;
      if(i == parts.size()-1) {
        if (obj instanceof Map) {
          Map map = (Map) obj;
          if(isXpath && isAttr){
            return XML_ATTR.equals(map.get(part));
          } else {
             return XML_ATTR.equals( map.get(part)) || XML_NODE.equals(map.get(part));
          }
        }
        return false;
      }
      obj = ((Map) obj).get(part);
    }
    return false;
  }


  public Map<String, String> getEditableSubProperties(String xpath) {
    Object o = getObjectByPath(props,false,StrUtils.splitSmart(xpath,'/'));
    if (o instanceof Map) {
      return  (Map) o;
    } else {
      return null;
    }
  }

  public Map<String, Object> getUserProps() {
    return userProps;
  }
}