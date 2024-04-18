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

package ua.org.sands.funmesh

/*
 * Created by Serhiy Shamshetdinov
 * at 27.03.2024 20:11
 */

import org.slf4j.LoggerFactory
import sttp.tapir.server.netty.NettyFutureServerBinding
import ua.org.sands.funmesh.server.FunMeshServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

object FunMesh {
  private[funmesh] val logger = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {
    FunMeshConfig.parse(args) match {
      case Left(msg) => println(msg)
      case Right(config) =>
        val (bindingFuture, shutdownFuture) = new FunMeshServer()(config).startNettyFutureServer()
        val program = for {
          binding <- bindingFuture
          _ = printStarted(config, binding)
          _ <- if (config.headless) shutdownFuture else Future.firstCompletedOf(Seq(blockUntilEnter, shutdownFuture))
          _ <- binding.stop()
        } yield
          printStopped(shutdownFuture)

        if (!config.headless) Await.result(program, Duration.Inf)
    }
  }

  private def blockUntilEnter: Future[String] =
    Future(StdIn.readLine())

  private def printStarted(config: FunMeshConfig, binding: NettyFutureServerBinding): Unit = {
    println(FunMeshConfig.startDescription(config))
    println(s"Go to http://localhost:${binding.port}/docs to open SwaggerUI.")
    println(s"Use http://localhost:${binding.port}/metrics to access Prometheus Metrics.")
    println(s"Use http://localhost:${binding.port}/help to access the used config and help description.")
    println(s"Use http://localhost:${config.basePort}/shutdown to terminate all microservices & primary server itself.")
    if (!config.headless) println("Press Enter key to exit.")
  }

  private def printStopped(shutdownFuture: Future[Seq[String]]): Unit = {
    val stopMessage = shutdownFuture.value.fold("is stopped by reading Enter from StdIn") {
      case Failure(e) =>
        logger.error(s"Exception while server stopping by /shutdown endpoint: ${e.getMessage}", e)
        s"failed to stop by /shutdown endpoint due to an exception: ${e.getMessage}"
      case Success(list) =>
        list.mkString("is stopped by /shutdown endpoint:\n", "\n", "")
    }
    println(s"\nServer $stopMessage\n")
  }
}
