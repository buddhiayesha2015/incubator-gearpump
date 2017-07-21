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

package org.apache.gearpump.streaming.refactor.source

import java.time.Instant

import org.apache.gearpump._
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.streaming.Constants._
import org.apache.gearpump.streaming.refactor.dsl.task.TaskUtil
import org.apache.gearpump.streaming.refactor.dsl.window.impl.{TimestampedValue, WindowRunner}
import org.apache.gearpump.streaming.refactor.state.{RuntimeContext, StatefulTask}
import org.apache.gearpump.streaming.source.{DataSource, DataSourceConfig, Watermark}
import org.apache.gearpump.streaming.task.{Task, TaskContext}

/**
 * Default Task container for [[org.apache.gearpump.streaming.source.DataSource]] that
 * reads from DataSource in batch
 * See [[org.apache.gearpump.streaming.source.DataSourceProcessor]] for its usage
 *
 * DataSourceTask calls:
 *  - `DataSource.open()` in `onStart` and pass in
 *  [[org.apache.gearpump.streaming.task.TaskContext]]
 * and application start time
 *  - `DataSource.read()` in each `onNext`, which reads a batch of messages
 *  - `DataSource.close()` in `onStop`
 */
class DataSourceTask[IN, OUT] private[source](
    source: DataSource,
    windowRunner: WindowRunner[IN, OUT],
    context: TaskContext,
    conf: UserConfig)
  extends StatefulTask(context, conf) {

  private var runtimeContext: RuntimeContext = null

  def this(context: TaskContext, conf: UserConfig) = {
    this(
      conf.getValue[DataSource](GEARPUMP_STREAMING_SOURCE)(context.system).get,
      conf.getValue[WindowRunner[IN, OUT]](GEARPUMP_STREAMING_OPERATOR)(context.system).get,
      context, conf
    )
  }

  private val batchSize = conf.getInt(DataSourceConfig.SOURCE_READ_BATCH_SIZE).getOrElse(1000)

  override def open(runtimeContext: RuntimeContext): Unit = {
    this.runtimeContext = runtimeContext
    LOG.info(s"opening data source at ${runtimeContext.getStartTime.toEpochMilli}")
    source.open(context, runtimeContext.getStartTime)
    self ! Watermark(source.getWatermark)
    super.open(runtimeContext)
  }

  override def invoke(m: Message): Unit = {
    0.until(batchSize).foreach { _ =>
      Option(source.read()).foreach(
        msg => windowRunner.process(
          TimestampedValue(msg.value.asInstanceOf[IN], msg.timestamp)))
    }

    self ! Watermark(source.getWatermark)
  }

  override def onWatermarkProgress(watermark: Instant): Unit = {
    TaskUtil.trigger(watermark, windowRunner, context, this.runtimeContext)
  }


  override def close(stateContext: RuntimeContext): Unit = {
    LOG.info("closing data source...")
    source.close()
  }

}