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
 * at ${DATE} ${TIME}
 */

import ua.org.sands.funmesh.server.FunMeshServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object FunMesh {

  def main(args: Array[String]): Unit = {
    FunMeshConfig.parse(args) match {
      case Left(msg) =>
        println(msg)
      case Right(config) =>
        val program = for {
          binding <- new FunMeshServer()(config).startNettyFutureServer()
          _ <- Future {
            println(s"\nFunMesh ${config.serverDescription} is started with $config")
            println(s"Go to http://localhost:${binding.port}/docs to open SwaggerUI.")
            println(s"Use http://localhost:${binding.port}/metrics to access Prometheus Metrics.\nPress Enter key to exit.\n")
            StdIn.readLine()
          }
          stop <- binding.stop()
        } yield stop

        Await.result(program, Duration.Inf)
    }
  }
}
