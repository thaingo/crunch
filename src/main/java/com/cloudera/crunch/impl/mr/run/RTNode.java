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
package com.cloudera.crunch.impl.mr.run;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.cloudera.crunch.CombineFn;
import com.cloudera.crunch.DoFn;
import com.cloudera.crunch.Emitter;
import com.cloudera.crunch.fn.IdentityFn;
import com.cloudera.crunch.impl.mr.emit.CombineFnEmitter;
import com.cloudera.crunch.impl.mr.emit.IntermediateEmitter;
import com.cloudera.crunch.impl.mr.emit.MultipleOutputEmitter;
import com.cloudera.crunch.impl.mr.emit.OutputEmitter;
import com.cloudera.crunch.type.Converter;

public class RTNode implements Serializable {
  
  private static final Log LOG = LogFactory.getLog(RTNode.class);
  
  private final String nodeName;
  private DoFn<Object, Object> fn;
  private final List<RTNode> children;
  private final Converter<Object, Object, Object> inputConverter;
  private final Converter<Object, Object, Object> outputConverter;
  private final String outputName;

  private transient Emitter<Object> emitter;

  public RTNode(DoFn<Object, Object> fn, String name, List<RTNode> children,
      Converter<Object, Object, Object> inputConverter,
      Converter<Object, Object, Object> outputConverter, String outputName) {
    this.fn = fn;
    this.nodeName = name;
    this.children = children;
    this.inputConverter = inputConverter;
    this.outputConverter = outputConverter;
    this.outputName = outputName;
  }

  public void initialize(CrunchTaskContext ctxt) {
    if (emitter != null) {
      // Already initialized
      return;
    }
    
    fn.setContext(ctxt.getContext());
    for (RTNode child : children) {
      child.initialize(ctxt);
    }

    if (outputConverter != null) {
      if (outputName != null) {
        this.emitter = new MultipleOutputEmitter<Object, Object, Object>(
            outputConverter, ctxt.getMultipleOutputs(), outputName);
      } else {
        this.emitter = new OutputEmitter<Object, Object, Object>(
            outputConverter, ctxt.getContext());
      }
    } else if (!children.isEmpty()) {
      if (children.size() == 1 && children.get(0).isLeafNode()
          && fn instanceof CombineFn
          && ctxt.getNodeContext() == NodeContext.MAP) {
        this.emitter = new CombineFnEmitter((CombineFn) fn, children.get(0),
            ctxt.getContext().getConfiguration().getInt(
                RuntimeParameters.AGGREGATOR_BUCKETS, 1000));
        this.fn = IdentityFn.getInstance();
      } else {
        this.emitter = new IntermediateEmitter(children);
      }
    } else {
      throw new CrunchRuntimeException("Invalid RTNode config: no emitter for: " + nodeName);
    }
  }

  public boolean isLeafNode() {
    return outputConverter != null && children.isEmpty();
  }

  public void process(Object input) {
    try {
      fn.process(input, emitter);
    } catch (CrunchRuntimeException e) {
      if (!e.wasLogged()) {
        LOG.info(String.format("Crunch exception in '%s' for input: %s",
            nodeName, input.toString()), e);
        e.markLogged();
      }
      throw e;
    }
  }

  public void process(Object key, Object value) {
    process(inputConverter.convertInput(key, value));
  }

  public void cleanup() {
    fn.cleanup();
    emitter.flush();
    for (RTNode child : children) {
      child.cleanup();
    }
  }
}
