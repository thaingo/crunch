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

package com.cloudera.crunch;

/**
 * A {@link Tuple} instance for an arbitrary number of values.
 */
public class TupleN extends Tuple {

  private final Object values[];

  public TupleN(Object... values) {
    this.values = new Object[values.length];
    System.arraycopy(values, 0, this.values, 0, values.length);
  }

  public Object get(int index) {
    return values[index];
  }

  public int size() {
    return values.length;
  }
}
