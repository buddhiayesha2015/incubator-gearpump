/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump.sql.rel;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rex.RexNode;
import org.apache.gearpump.DefaultMessage;
import org.apache.gearpump.Message;
import org.apache.gearpump.cluster.UserConfig;
import org.apache.gearpump.sql.table.SampleString;
import org.apache.gearpump.streaming.dsl.api.functions.MapFunction;
import org.apache.gearpump.streaming.dsl.javaapi.JavaStream;
import org.apache.gearpump.streaming.dsl.javaapi.JavaStreamApp;
import org.apache.gearpump.streaming.dsl.javaapi.functions.FlatMapFunction;
import org.apache.gearpump.streaming.dsl.javaapi.functions.GroupByFunction;
import org.apache.gearpump.streaming.source.DataSource;
import org.apache.gearpump.streaming.source.Watermark;
import org.apache.gearpump.streaming.task.TaskContext;
import scala.Tuple2;

import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;


public class GearFlatMapRel extends Filter implements GearRelNode {

    public GearFlatMapRel(RelOptCluster cluster, RelTraitSet traits, RelNode child,
                          RexNode condition) {
        super(cluster, traits, child, condition);
    }

    @Override
    public Filter copy(RelTraitSet traitSet, RelNode input, RexNode condition) {
        return new GearFlatMapRel(getCluster(), traitSet, input, condition);
    }

    @Override
    public JavaStream<Tuple2<String, Integer>> buildGearPipeline(JavaStreamApp app, JavaStream<Tuple2<String, Integer>> javaStream) throws Exception {

        System.out.println("GearFlatMapRel *********************** 1");

        JavaStream<String> sentence = app.source(new StringSource("This is a good start, bingo!! bingo!!"),
                1, UserConfig.empty(), "source");
        System.out.println("GearFlatMapRel ********************** 2");
        SampleString.WORDS = sentence.flatMap(new Split(), "flatMap");
        System.out.println("GearFlatMapRel ********************** 3");

        return null;
    }

    private static class Ones extends MapFunction<String, Tuple2<String, Integer>> {

        @Override
        public Tuple2<String, Integer> map(String s) {
            return new Tuple2<>(s, 1);
        }
    }

    private static class TupleKey extends GroupByFunction<Tuple2<String, Integer>, String> {

        @Override
        public String groupBy(Tuple2<String, Integer> tuple) {
            return tuple._1();
        }
    }


    private static class StringSource implements DataSource {

        private final String str;
        private boolean hasNext = true;

        StringSource(String str) {
            this.str = str;
        }

        @Override
        public void open(TaskContext context, Instant startTime) {
        }

        @Override
        public Message read() {
            Message msg = new DefaultMessage(str, Instant.now());
            hasNext = false;
            return msg;
        }

        @Override
        public void close() {
        }

        @Override
        public Instant getWatermark() {
            if (hasNext) {
                return Instant.now();
            } else {
                return Watermark.MAX();
            }
        }
    }

    private static class Split extends FlatMapFunction<String, String> {

        @Override
        public Iterator<String> flatMap(String s) {
            return Arrays.asList(s.split("\\s+")).iterator();
        }
    }

}
