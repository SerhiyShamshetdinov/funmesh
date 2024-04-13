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

package ua.org.sands.funmesh.tapir

import org.slf4j.LoggerFactory
import sttp.client3._
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import ua.org.sands.funmesh.FunMeshConfig
import ua.org.sands.funmesh.server.MicroserviceRole._
import ua.org.sands.funmesh.server.{BinaryFunctionRole, MicroserviceRole, UnaryFunctionRole}
import ua.org.sands.funmesh.tapir.EndpointsService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/*
 * Created by Serhiy Shamshetdinov
 * at 28.03.2024 18:30
 */

class EndpointsService(prometheusMetrics: PrometheusMetrics[Future],
                       primaryEndpointFunction: (String, Num) => Future[Num])
                      (implicit val config: FunMeshConfig) {
  private[tapir] val logger = LoggerFactory.getLogger(getClass.getName)

  private[tapir] type ErrorInfo = String

  private[tapir] def resultHandler(funName: => String, request: FunRequest, response: Try[Num]): Try[Either[ErrorInfo, Num]] =
    response match {
      case Success(v) if v.isNaN || v.isInfinity =>
        val message = s"'$funName' is not defined at $request value(s)"
        logger.error(s"Error while function evaluation: $message")
        Success(Left(message))
      case Success(v) =>
        Success(Right(v))
      case Failure(th) =>
        logger.error(s"Exception while '$funName' function evaluation at $request value(s): ${th.getMessage}", th)
        Success(Left(th.getMessage))
    }

  private[tapir] val commonEndpoint = endpoint.get.out(jsonBody[Num]).errorOut(plainBody[ErrorInfo])

  private[tapir] lazy val primaryServerEndpoint = commonEndpoint
    .in("eval").description(s"Primary call to evaluate function value using primitive function ${if (config.allRoles) "endpoints" else "microservices"}")
    .in(query[String]("f").description("Function to evaluate").and(query[Num]("x").description("'x' parameter value of the f")).mapTo[FunEvalRequest])
    .serverLogic(fer => primaryEndpointFunction(fer.f, fer.x).transform(resultHandler("Primary function", fer, _)))

  private[tapir] def withDescription[T <: MicroserviceRole[_]](role: T) = commonEndpoint.description(s"${role.description} function call")

  private[tapir] def unaryPublicEndpoint(role: UnaryFunctionRole): PublicEndpoint[UnaryRequest, ErrorInfo, Num, Any] =
    withDescription(role).in(role.path)
      .in(query[Num]("x").description("Single parameter value").mapTo[UnaryRequest])

  private[tapir] def binaryPublicEndpoint(role: BinaryFunctionRole): PublicEndpoint[BinaryRequest, ErrorInfo, Num, Any] =
    withDescription(role).in(role.path)
      .in(query[Num]("x").description("First parameter value").and(query[Num]("y").description("Second parameter value")).mapTo[BinaryRequest])

  private[tapir] lazy val unaryRoleToPublicEndpoint = unaryFunctionRoles.map(r => r -> unaryPublicEndpoint(r))
  private[tapir] lazy val binaryRoleToPublicEndpoint = binaryFunctionRoles.map(r => r -> binaryPublicEndpoint(r))

  private[tapir] def unaryServerEndpoint(role: UnaryFunctionRole, publicEndpoint: PublicEndpoint[UnaryRequest, ErrorInfo, Num, Any]): ServerEndpoint[Any, Future] =
    publicEndpoint.serverLogic(ur => Future.fromTry(resultHandler(role.description, ur, Try(role.function(ur.x)))))

  private[tapir] def binaryServerEndpoint(role: BinaryFunctionRole, publicEndpoint: PublicEndpoint[BinaryRequest, ErrorInfo, Num, Any]): ServerEndpoint[Any, Future] =
    publicEndpoint.serverLogic(br => Future.fromTry(resultHandler(role.description, br, Try(role.function((br.x, br.y))))))

  private[tapir] lazy val unaryServerEndpoints = unaryRoleToPublicEndpoint.map((unaryServerEndpoint _).tupled)
  private[tapir] lazy val binaryServerEndpoints = binaryRoleToPublicEndpoint.map((binaryServerEndpoint _).tupled)

  private[tapir] val apiEndpoints: List[ServerEndpoint[Any, Future]] =
    if (config.allRoles)
      primaryServerEndpoint :: binaryServerEndpoints ++ unaryServerEndpoints
    else if (config.roleId == 0)
      List(primaryServerEndpoint)
    else roleById(config.roleId) match {
      case role: UnaryFunctionRole => List(unaryServerEndpoint(role, unaryPublicEndpoint(role)))
      case role: BinaryFunctionRole => List(binaryServerEndpoint(role, binaryPublicEndpoint(role)))
    }

  private[tapir] val docEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter().fromServerEndpoints[Future](apiEndpoints, s"Function Mesh, ${config.serverDescription}", funMeshVersion)

  val allEndpoints: List[ServerEndpoint[Any, Future]] = apiEndpoints ++ docEndpoints ++ List(prometheusMetrics.metricsEndpoint)

  /// Client relative
  private[tapir] val clientInterpreter: SttpClientInterpreter = SttpClientInterpreter()
  private[tapir] val clientBackend = HttpClientFutureBackend()

  private[tapir] lazy val primaryServerUri = uri"http://localhost:${config.basePort}"

  private[tapir] def buildUnaryFunction(role: UnaryFunctionRole, endpoint: PublicEndpoint[UnaryRequest, ErrorInfo, Num, Any]): Num => Future[Num] = {
    val uri = if (config.allRoles) primaryServerUri else uri"http://${config.msHost}:${config.basePort + role.id}"
    val requestFunction = clientInterpreter.toRequestThrowErrors(endpoint, Some(uri))

    (x: Num) => requestFunction(UnaryRequest(x)).send(clientBackend).map(_.body)
  }

  private[tapir] def buildBinaryFunction(role: BinaryFunctionRole, endpoint: PublicEndpoint[BinaryRequest, ErrorInfo, Num, Any]): (Num, Num) => Future[Num] = {
    val uri = if (config.allRoles) primaryServerUri else uri"http://${config.msHost}:${config.basePort + role.id}"
    val requestFunction = clientInterpreter.toRequestThrowErrors(endpoint, Some(uri))

    (x: Num, y: Num) => requestFunction(BinaryRequest(x, y)).send(clientBackend).map(_.body)
  }

  lazy val unaryOperationToFunction: Map[String, Num => Future[Num]] = unaryRoleToPublicEndpoint.map {
    case (role, endpoint) => role.symbol -> buildUnaryFunction(role, endpoint)
  }.toMap

  lazy val binaryOperationToFunction: Map[String, (Num, Num) => Future[Num]] = binaryRoleToPublicEndpoint.map {
    case (role, endpoint) => role.symbol -> buildBinaryFunction(role, endpoint)
  }.toMap
}

object EndpointsService {
  private val funMeshVersion = "0.1.1"

  private[tapir] sealed trait FunRequest

  private[tapir] case class FunEvalRequest(f: String, x: Num) extends FunRequest

  private[tapir] case class UnaryRequest(x: Num) extends FunRequest // extends AnyVal works only without additional trait

  private[tapir] case class BinaryRequest(x: Num, y: Num) extends FunRequest
}
