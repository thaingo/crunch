/**
 * Copyright (c) 2011, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.crunch.type.avro;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;

import com.cloudera.crunch.MapFn;
import com.cloudera.crunch.Pair;
import com.cloudera.crunch.Tuple;
import com.cloudera.crunch.Tuple3;
import com.cloudera.crunch.Tuple4;
import com.cloudera.crunch.TupleN;
import com.cloudera.crunch.type.PTableType;
import com.cloudera.crunch.type.PType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Defines static methods that are analogous to the methods defined in
 * {@link AvroTypeFamily} for convenient static importing.
 *
 */
public class Avros {

  public static MapFn<Utf8, String> UTF8_TO_STRING = new MapFn<Utf8, String>() {
    @Override
    public String map(Utf8 input) {
      return input.toString();
    }
  };
  
  public static MapFn<String, Utf8> STRING_TO_UTF8 = new MapFn<String, Utf8>() {
    @Override
    public Utf8 map(String input) {
      return new Utf8(input);
    }
  };

  private static final AvroType<String> strings = new AvroType<String>(
      String.class, Schema.create(Schema.Type.STRING), UTF8_TO_STRING, STRING_TO_UTF8);
  private static final AvroType<Long> longs = create(Long.class, Schema.Type.LONG);
  private static final AvroType<Integer> ints = create(Integer.class, Schema.Type.INT);
  private static final AvroType<Float> floats = create(Float.class, Schema.Type.FLOAT);
  private static final AvroType<Double> doubles = create(Double.class, Schema.Type.DOUBLE);
  private static final AvroType<Boolean> booleans = create(Boolean.class, Schema.Type.BOOLEAN);
  private static final AvroType<ByteBuffer> bytes = create(ByteBuffer.class, Schema.Type.BYTES);
  
  private static final Map<Class, PType> PRIMITIVES = ImmutableMap.<Class, PType>builder()
      .put(String.class, strings)
      .put(Long.class, longs)
      .put(Integer.class, ints)
      .put(Float.class, floats)
      .put(Double.class, doubles)
      .put(Boolean.class, booleans)
      .put(ByteBuffer.class, bytes)
      .build();
  
  private static final Map<Class, AvroType> EXTENSIONS = Maps.newHashMap();
  
  public static <T> void register(Class<T> clazz, AvroType<T> ptype) {
    EXTENSIONS.put(clazz, ptype);
  }
  
  public static <T> PType<T> getPrimitiveType(Class<T> clazz) {
    return PRIMITIVES.get(clazz);
  }
  
  private static <T> AvroType<T> create(Class<T> clazz, Schema.Type schemaType) {
    return new AvroType<T>(clazz, Schema.create(schemaType));
  }

  public static final AvroType<String> strings() {
    return strings;
  }

  public static final AvroType<Long> longs() {
    return longs;
  }

  public static final AvroType<Integer> ints() {
    return ints;
  }

  public static final AvroType<Float> floats() {
    return floats;
  }

  public static final AvroType<Double> doubles() {
    return doubles;
  }

  public static final AvroType<Boolean> booleans() {
    return booleans;
  }
  
  public static final AvroType<ByteBuffer> bytes() {
    return bytes;
  }
  
  public static final <T> AvroType<T> records(Class<T> clazz) {
    if (EXTENSIONS.containsKey(clazz)) {
      return (AvroType<T>) EXTENSIONS.get(clazz);
    }
    return new AvroType<T>(clazz, SpecificData.get().getSchema(clazz));
  }

  public static final <T> AvroType<T> containers(Class<T> clazz, Schema schema) {
    return new AvroType<T>(clazz, schema);
  }
  
  private static class AvroCollectionMapFn extends MapFn<Collection, Collection> {
    
    private final MapFn mapFn;
    
    public AvroCollectionMapFn(MapFn mapFn) {
      this.mapFn = mapFn;
    }
    
    @Override
    public void initialize() {
      this.mapFn.initialize();
    }
    
    @Override
    public Collection map(Collection input) {
      Collection ret = Lists.newArrayList();
      for (Object in : input) {
        ret.add(mapFn.map(in));
      }
      return ret;
    }
  }
  
  public static final <T> AvroType<Collection<T>> collections(PType<T> ptype) {
    AvroType<T> avroType = (AvroType<T>) ptype;
    Schema collectionSchema = Schema.createArray(avroType.getSchema());
    return new AvroType(Collection.class, collectionSchema, 
        new AvroCollectionMapFn(avroType.getBaseInputMapFn()),
        new AvroCollectionMapFn(avroType.getBaseOutputMapFn()), ptype);
  }

  private static class GenericRecordToTuple extends MapFn<GenericRecord, Tuple> {
    private final List<MapFn> fns;
    
    private transient Object[] values;
    
    public GenericRecordToTuple(PType... ptypes) {
      this.fns = Lists.newArrayList();
      for (PType ptype : ptypes) {
        AvroType atype = (AvroType) ptype;
        fns.add(atype.getBaseInputMapFn());
      }
    }
    
    @Override
    public void initialize() {
      for (MapFn fn : fns) {
        fn.initialize();
      }
      this.values = new Object[fns.size()];
    }

    @Override
    public Tuple map(GenericRecord input) {
      for (int i = 0; i < values.length; i++) {
        Object v = input.get(i);
        if (v == null) {
          values[i] = null;
        } else {
          values[i] = fns.get(i).map(v);
        }
      }
      return Tuple.tuplify(values);
    }
  }
  
  private static class TupleToGenericRecord extends MapFn<Tuple, GenericRecord> {
    private final List<MapFn> fns;
    private final String jsonSchema;
    
    private transient GenericRecord record;
    
    public TupleToGenericRecord(Schema schema, PType... ptypes) {
      this.fns = Lists.newArrayList();
      this.jsonSchema = schema.toString();
      for (PType ptype : ptypes) {
        AvroType atype = (AvroType) ptype;
        fns.add(atype.getBaseOutputMapFn());
      }
    }
    
    @Override
    public void initialize() {
      this.record = new GenericData.Record(new Schema.Parser().parse(jsonSchema));
      for (MapFn fn : fns) {
        fn.initialize();
      }
    }

    @Override
    public GenericRecord map(Tuple input) {
      for (int i = 0; i < input.size(); i++) {
        Object v = input.get(i);
        if (v == null) {
          record.put(i, null);
        } else {
          record.put(i, fns.get(i).map(v));
        }
      }
      return record;
    }
  }
  
  public static final <V1, V2> PType<Pair<V1, V2>> pairs(PType<V1> p1, PType<V2> p2) {
    Schema schema = createTupleSchema(p1, p2);
    return new AvroType(Pair.class, schema,
        new GenericRecordToTuple(p1, p2), new TupleToGenericRecord(schema, p1, p2),
        p1, p2);
  }

  public static final <V1, V2, V3> PType<Tuple3<V1, V2, V3>> triples(PType<V1> p1,
      PType<V2> p2, PType<V3> p3) {
    Schema schema = createTupleSchema(p1, p2, p3);
    return new AvroType(Tuple3.class, schema,
        new GenericRecordToTuple(p1, p2, p3), new TupleToGenericRecord(schema, p1, p2, p3),
        p1, p2, p3);
  }

  public static final <V1, V2, V3, V4> PType<Tuple4<V1, V2, V3, V4>> quads(PType<V1> p1,
      PType<V2> p2, PType<V3> p3, PType<V4> p4) {
    Schema schema = createTupleSchema(p1, p2, p3, p4);
    return new AvroType(Tuple4.class, schema,
        new GenericRecordToTuple(p1, p2, p3, p4), new TupleToGenericRecord(schema, p1, p2, p3, p4),
        p1, p2, p3, p4);
  }

  public static final PType<TupleN> tuples(PType... ptypes) {
    Schema schema = createTupleSchema(ptypes);
    return new AvroType(TupleN.class, schema,
        new GenericRecordToTuple(ptypes), new TupleToGenericRecord(schema, ptypes),
        ptypes);
  }

  private static int tupleIndex = 0;
  
  private static Schema createTupleSchema(PType... ptypes) {
    Schema schema = Schema.createRecord("tuple" + tupleIndex++, "", "crunch", false);
    List<Schema.Field> fields = Lists.newArrayList();
    for (int i = 0; i < ptypes.length; i++) {
      AvroType atype = (AvroType) ptypes[i];
      Schema fieldSchema = Schema.createUnion(
          ImmutableList.of(atype.getSchema(), Schema.create(Type.NULL)));
      fields.add(new Schema.Field("v" + i, fieldSchema, "", null));
    }
    schema.setFields(fields);
    return schema;
  }
  
  public static final <K, V> PTableType<K, V> tableOf(PType<K> key, PType<V> value) {
    AvroType<K> avroKey = (AvroType<K>) key;
    AvroType<V> avroValue = (AvroType<V>) value;    
    return new AvroTableType(avroKey, avroValue, Pair.class);
  }

  private Avros() {}
}
