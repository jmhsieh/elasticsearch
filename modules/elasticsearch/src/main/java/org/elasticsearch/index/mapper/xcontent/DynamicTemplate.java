/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper.xcontent;

import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.index.mapper.MapperParsingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class DynamicTemplate {

    public static enum MatchType {
        SIMPLE,
        REGEX;

        public static MatchType fromString(String value) {
            if ("simple".equals(value)) {
                return SIMPLE;
            } else if ("regex".equals(value)) {
                return REGEX;
            }
            throw new ElasticSearchIllegalArgumentException("No matching pattern matched on [" + value + "]");
        }
    }

    public static DynamicTemplate parse(String name, Map<String, Object> conf) throws MapperParsingException {
        if (!conf.containsKey("match")) {
            throw new MapperParsingException("template must have match set");
        }
        String match = conf.get("match").toString();
        String unmatch = conf.containsKey("unmatch") ? conf.get("unmatch").toString() : null;
        String matchMappingType = conf.containsKey("match_mapping_type") ? conf.get("match_mapping_type").toString() : null;
        if (!conf.containsKey("mapping")) {
            throw new MapperParsingException("template must have mapping set");
        }
        Map<String, Object> mapping = (Map<String, Object>) conf.get("mapping");
        String matchType = conf.containsKey("match_pattern") ? conf.get("match_pattern").toString() : "simple";
        return new DynamicTemplate(name, conf, match, unmatch, matchMappingType, MatchType.fromString(matchType), mapping);
    }

    private final String name;

    private final Map<String, Object> conf;

    private final String match;

    private final String unmatch;

    private final MatchType matchType;

    private final String matchMappingType;

    private final Map<String, Object> mapping;

    public DynamicTemplate(String name, Map<String, Object> conf, String match, String unmatch, String matchMappingType, MatchType matchType, Map<String, Object> mapping) {
        this.name = name;
        this.conf = conf;
        this.match = match;
        this.unmatch = unmatch;
        this.matchType = matchType;
        this.matchMappingType = matchMappingType;
        this.mapping = mapping;
    }

    public String name() {
        return this.name;
    }

    public Map<String, Object> conf() {
        return this.conf;
    }

    public boolean match(String name, String dynamicType) {
        if (!patternMatch(match, name)) {
            return false;
        }
        if (patternMatch(unmatch, name)) {
            return false;
        }
        if (matchMappingType != null) {
            if (dynamicType == null) {
                return false;
            }
            if (!patternMatch(matchMappingType, dynamicType)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasType() {
        return mapping.containsKey("type");
    }

    public String mappingType(String dynamicType) {
        return mapping.containsKey("type") ? mapping.get("type").toString() : dynamicType;
    }

    private boolean patternMatch(String pattern, String str) {
        if (matchType == MatchType.SIMPLE) {
            return Regex.simpleMatch(pattern, str);
        }
        return str.matches(pattern);
    }

    public Map<String, Object> mappingForName(String name, String dynamicType) {
        return processMap(mapping, name, dynamicType);
    }

    private Map<String, Object> processMap(Map<String, Object> map, String name, String dynamicType) {
        Map<String, Object> processedMap = Maps.newHashMap();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().replace("{name}", name).replace("{dynamic_type}", dynamicType).replace("{dynamicType}", dynamicType);
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = processMap((Map<String, Object>) value, name, dynamicType);
            } else if (value instanceof List) {
                value = processList((List) value, name, dynamicType);
            } else if (value instanceof String) {
                value = value.toString().replace("{name}", name).replace("{dynamic_type}", dynamicType).replace("{dynamicType}", dynamicType);
            }
            processedMap.put(key, value);
        }
        return processedMap;
    }

    private List processList(List list, String name, String dynamicType) {
        List processedList = new ArrayList();
        for (Object value : list) {
            if (value instanceof Map) {
                value = processMap((Map<String, Object>) value, name, dynamicType);
            } else if (value instanceof List) {
                value = processList((List) value, name, dynamicType);
            } else if (value instanceof String) {
                value = value.toString().replace("{name}", name).replace("{dynamic_type}", dynamicType).replace("{dynamicType}", dynamicType);
            }
            processedList.add(value);
        }
        return processedList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicTemplate that = (DynamicTemplate) o;

        // check if same matching, if so, replace the mapping
        if (match != null ? !match.equals(that.match) : that.match != null) return false;
        if (matchMappingType != null ? !matchMappingType.equals(that.matchMappingType) : that.matchMappingType != null)
            return false;
        if (matchType != that.matchType) return false;
        if (unmatch != null ? !unmatch.equals(that.unmatch) : that.unmatch != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        // check if same matching, if so, replace the mapping
        int result = match != null ? match.hashCode() : 0;
        result = 31 * result + (unmatch != null ? unmatch.hashCode() : 0);
        result = 31 * result + (matchType != null ? matchType.hashCode() : 0);
        result = 31 * result + (matchMappingType != null ? matchMappingType.hashCode() : 0);
        return result;
    }
}
