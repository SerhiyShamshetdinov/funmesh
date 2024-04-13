/*
 * funmesh - Function Mesh, the application to play with the service mesh
 *
 * Copyright (c) 2024 Serhiy Shamshetdinov (Kyiv, Ukraine)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * See the NOTICE.md file distributed with this work for
 * additional information regarding copyright ownership and used works.
 */

package ua.org.sands.funmesh.server

import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding, NettyFutureServerOptions}
import ua.org.sands.funmesh.FunMeshConfig
import MicroserviceRole.Num
import ua.org.sands.funmesh.tapir.EndpointsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/*
 * Created by Serhiy Shamshetdinov
 * at 28.03.2024 18:06
 */

class FunMeshServer()(implicit val config: FunMeshConfig) {
  private val prometheusMetrics: PrometheusMetrics[Future] = PrometheusMetrics.default[Future]()
  private val endpointsService = new EndpointsService(prometheusMetrics, evaluateStringFunction)
  private lazy val stringFunction = new StringFunction(endpointsService.unaryOperationToFunction, endpointsService.binaryOperationToFunction)

  private def evaluateStringFunction(f: String, num: Num): Future[Num] = stringFunction.evaluate(f, num)

  def startNettyFutureServer(): Future[NettyFutureServerBinding] = {
    val serverOptions = NettyFutureServerOptions.customiseInterceptors
      .metricsInterceptor(prometheusMetrics.metricsInterceptor())
      .options
    // sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)
    NettyFutureServer(serverOptions).port(config.serverPort).addEndpoints(endpointsService.allEndpoints).start()
  }
}
