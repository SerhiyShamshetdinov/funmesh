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

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.Logger
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{HttpClientFutureBackend, UriContext, basicRequest}
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.stub.TapirStubInterpreter
import ua.org.sands.funmesh.server.MicroserviceRole
import ua.org.sands.funmesh.server.MicroserviceRole.Num
import ua.org.sands.funmesh.tapir.EndpointsService.FunRequest
import ua.org.sands.funmesh.{FunMeshConfig, TestBase}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/*
 * Created by Serhiy Shamshetdinov
 * at 09.04.2024 18:30
 */

class EndpointsServiceTest extends TestBase {
  val prometheusMetricsEndpoint: PrometheusMetrics[Future] = mock[PrometheusMetrics[Future]]
  val funMeshDefaultConfig: FunMeshConfig = FunMeshConfig()

  val primaryFunction: (String, Num) => Future[Num] = (s: String, n: Num) => Future.successful(s.toDouble + n)
  val loggerMock: Logger = mock[Logger]

  val funRequest: FunRequest = mock[FunRequest]
  when(funRequest.toString).thenReturn("RequestToString")

  def getEndpointsService(conf: FunMeshConfig = funMeshDefaultConfig): EndpointsService = new EndpointsService(prometheusMetricsEndpoint, primaryFunction)(conf) {
    override val logger: Logger = loggerMock
  }

  val funName = "Test_fun"
  val errorMessage = "test_error"
  val exception = new Exception(errorMessage)

  "resultHandler" should "catch Success NaN & both Infinite responses, log error and return Success Left error message" in {
    val message = s"'$funName' is not defined at $funRequest value(s)"
    val endpointsService: EndpointsService = getEndpointsService()

    endpointsService.resultHandler(funName, funRequest, Success(Double.NaN)) shouldBe Success(Left(message))
    endpointsService.resultHandler(funName, funRequest, Success(Double.PositiveInfinity)) shouldBe Success(Left(message))
    endpointsService.resultHandler(funName, funRequest, Success(Double.NegativeInfinity)) shouldBe Success(Left(message))

    verify(loggerMock, times(3)).error(s"Error while function evaluation: $message")
  }

  it should "wrap other Success responses to Right" in {
    val endpointsService: EndpointsService = getEndpointsService()

    endpointsService.resultHandler(funName, funRequest, Success(1.0)) shouldBe Success(Right(1.0))
  }

  it should "catch Failure, log error with exception and return Success Left error message" in {
    val endpointsService: EndpointsService = getEndpointsService()

    endpointsService.resultHandler(funName, funRequest, Failure(exception)) shouldBe Success(Left(errorMessage))

    verify(loggerMock).error(s"Exception while '$funName' function evaluation at $funRequest value(s): $errorMessage", exception)
  }

  "primaryServerEndpoint" should "response with function evaluation result on HTTP request" in {
    val endpointsService: EndpointsService = getEndpointsService()

    val backendStub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpointRunLogic(endpointsService.primaryServerEndpoint)
      .backend()

    val f = "423"
    val x = 34
    val response = basicRequest.get(uri"http://test.com/eval?f=$f&x=$x").send(backendStub)

    response.await.body.value shouldBe primaryFunction(f, x).await.toString
  }

  "unaryServerEndpoints" should "response with function evaluation result on HTTP request" in {
    val endpointsService: EndpointsService = getEndpointsService()

    val backendStub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpointsRunLogic(endpointsService.unaryServerEndpoints)
      .backend()

    basicRequest.get(uri"http://test.com/abs?x=-5.3").send(backendStub).await.body.value shouldBe "5.3"
    basicRequest.get(uri"http://test.com/sqrt?x=64").send(backendStub).await.body.value shouldBe "8.0"
    basicRequest.get(uri"http://test.com/cbrt?x=64").send(backendStub).await.body.value shouldBe "4.0"
    basicRequest.get(uri"http://test.com/log?x=1").send(backendStub).await.body.value shouldBe "0.0"
    basicRequest.get(uri"http://test.com/log10?x=0.001").send(backendStub).await.body.value shouldBe "-3.0"
    basicRequest.get(uri"http://test.com/sin?x=0.5235987755982989").send(backendStub).await.body.value.toDouble shouldBe 0.5 +- 1e-10
    basicRequest.get(uri"http://test.com/cos?x=1.0471975511965979").send(backendStub).await.body.value.toDouble shouldBe 0.5 +- 1e-10
    basicRequest.get(uri"http://test.com/tg?x=0.785398163397448").send(backendStub).await.body.value.toDouble shouldBe 1.0 +- 1e-10
    basicRequest.get(uri"http://test.com/arcsin?x=0.5").send(backendStub).await.body.value.toDouble shouldBe 0.5235987755982989 +- 1e-10
    basicRequest.get(uri"http://test.com/arccos?x=0.5").send(backendStub).await.body.value.toDouble shouldBe 1.0471975511965979 +- 1e-10
    basicRequest.get(uri"http://test.com/arctg?x=1").send(backendStub).await.body.value.toDouble shouldBe 0.7853981633974483 +- 1e-10

    basicRequest.get(uri"http://test.com/eval?x=1").send(backendStub).await.body.leftSide shouldBe Left("")
  }

  "binaryServerEndpoints" should "response with function evaluation result on HTTP request" in {
    val endpointsService: EndpointsService = getEndpointsService()

    val backendStub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpointsRunLogic(endpointsService.binaryServerEndpoints)
      .backend()

    basicRequest.get(uri"http://test.com/add?x=6&y=3").send(backendStub).await.body.value shouldBe "9.0"
    basicRequest.get(uri"http://test.com/sub?x=6&y=3").send(backendStub).await.body.value shouldBe "3.0"
    basicRequest.get(uri"http://test.com/mul?x=6&y=3").send(backendStub).await.body.value shouldBe "18.0"
    basicRequest.get(uri"http://test.com/div?x=6&y=3").send(backendStub).await.body.value shouldBe "2.0"
    basicRequest.get(uri"http://test.com/power?x=6&y=3").send(backendStub).await.body.value shouldBe "216.0"

    basicRequest.get(uri"http://test.com/eval?x=6&y=3").send(backendStub).await.body.leftSide shouldBe Left("")
  }

  "primaryServerUri" should "contain configured port" in {
    val port = 567
    val endpointsService: EndpointsService = getEndpointsService(FunMeshConfig(basePort = port))

    endpointsService.primaryServerUri.port shouldBe Some(port)
  }

  "allEndpoints" should "contain primary server, help, shutdown and all role endpoints in Combined Mode" in {
    val endpointsService: EndpointsService = getEndpointsService(FunMeshConfig(allRoles = true))

    endpointsService.allEndpoints.size shouldBe MicroserviceRole.allRoles.size + 3 + 1 + 4 // 3 - primary, help, shutdown; 1 - metrics; 4 - Swagger
    endpointsService.allEndpoints should contain (endpointsService.primaryServerEndpoint)
    endpointsService.allEndpoints should contain (endpointsService.helpServerEndpoint)
    endpointsService.allEndpoints should contain (endpointsService.shutdownServerEndpoint)
  }

  it should "contain primary server, help, shutdown and no role endpoints in Separate Mode for primary server" in {
    val endpointsService: EndpointsService = getEndpointsService(FunMeshConfig(allRoles = false, roleId = 0))

    endpointsService.allEndpoints.size shouldBe 3 + 1 + 4 // 3 - primary, help, shutdown; 1 - metrics; 4 - Swagger
    endpointsService.allEndpoints should contain (endpointsService.primaryServerEndpoint)
    endpointsService.allEndpoints should contain (endpointsService.helpServerEndpoint)
    endpointsService.allEndpoints should contain (endpointsService.shutdownServerEndpoint)
  }

  it should "not contain primary server but 1 role endpoint, help and shutdown in Separate Mode for microservice" in {
    val endpointsService: EndpointsService = getEndpointsService(FunMeshConfig(allRoles = false, roleId = 1))

    endpointsService.allEndpoints.size shouldBe 3 + 1 + 4 // 3 - microservice, help, shutdown; 1 - metrics; 4 - Swagger
    endpointsService.allEndpoints should not contain endpointsService.primaryServerEndpoint
    endpointsService.allEndpoints should contain (endpointsService.helpServerEndpoint)
    endpointsService.allEndpoints should contain (endpointsService.shutdownServerEndpoint)
  }

  // Client stub https://sttp.softwaremill.com/en/stable/testing.html

  private def findRoleOrFail(roles: List[MicroserviceRole[_]], opSymbol: String): MicroserviceRole[_] =
    roles.find(_.symbol == opSymbol).getOrElse(fail(s"test role for '$opSymbol' is not found"))

  private val oneParamSeq = List("x" -> "1.0")

  "unaryOperationToFunction" should "call correct client endpoints while function evaluation in Combined Mode" in {
    val config = FunMeshConfig(allRoles = true, basePort = 100, msHost = "testHost")
    val endpointsService: EndpointsService = new EndpointsService(prometheusMetricsEndpoint, primaryFunction)(config) {
      override val logger: Logger = loggerMock
      private val roleToFunction = unaryOperationToFunction.map {
        case (opSymbol, function) =>
          findRoleOrFail(MicroserviceRole.unaryFunctionRoles, opSymbol) -> function
      }

      override private[tapir] val clientBackend = roleToFunction.foldLeft(HttpClientFutureBackend.stub) { // SttpBackendStub.asynchronousFuture
        case (stub, (role, _)) => stub
          .whenRequestMatches(r =>
            r.uri.port.contains(config.basePort) &&
              r.uri.host.contains("localhost") &&
              r.uri.path.head == role.path &&
              r.uri.paramsSeq == oneParamSeq)
          .thenRespond(role.id.toDouble)
      }
    }

    forEvery(endpointsService.unaryOperationToFunction) {
      case (opSymbol, function) =>
        val role = findRoleOrFail(MicroserviceRole.unaryFunctionRoles, opSymbol)

        function(1).await shouldBe role.id
    }
  }

  it should "call correct client endpoints while function evaluation in Separate Mode" in {
    val config = FunMeshConfig(allRoles = false, basePort = 100, msHost = "testHost")
    val endpointsService: EndpointsService = new EndpointsService(prometheusMetricsEndpoint, primaryFunction)(config) {
      override val logger: Logger = loggerMock
      private val roleToFunction = unaryOperationToFunction.map {
        case (opSymbol, function) =>
          findRoleOrFail(MicroserviceRole.unaryFunctionRoles, opSymbol) -> function
      }

      override private[tapir] val clientBackend = roleToFunction.foldLeft(HttpClientFutureBackend.stub) { // SttpBackendStub.asynchronousFuture
        case (stub, (role, _)) => stub
          .whenRequestMatches(r =>
            r.uri.port.contains(config.basePort + role.id) &&
              r.uri.host.contains(config.msHost) &&
              r.uri.path.head == role.path &&
              r.uri.paramsSeq == oneParamSeq)
          .thenRespond(role.id.toDouble)
      }
    }

    forEvery(endpointsService.unaryOperationToFunction) {
      case (opSymbol, function) =>
        val role = findRoleOrFail(MicroserviceRole.unaryFunctionRoles, opSymbol)

        function(1).await shouldBe role.id
    }
  }

  private val twoParamSeq = List("x" -> "1.0", "y" -> "2.0")

  "binaryOperationToFunction" should "call correct client endpoints while function evaluation in Combined Mode" in {
    val config = FunMeshConfig(allRoles = true, basePort = 100, msHost = "testHost")

    val endpointsService: EndpointsService = new EndpointsService(prometheusMetricsEndpoint, primaryFunction)(config) {
      override val logger: Logger = loggerMock
      private val roleToFunction = binaryOperationToFunction.map {
        case (opSymbol, function) =>
          findRoleOrFail(MicroserviceRole.binaryFunctionRoles, opSymbol) -> function
      }

      override private[tapir] val clientBackend = roleToFunction.foldLeft(HttpClientFutureBackend.stub) { // SttpBackendStub.asynchronousFuture
        case (stub, (role, _)) => stub
          .whenRequestMatches(r =>
            r.uri.port.contains(config.basePort) &&
              r.uri.host.contains("localhost") &&
              r.uri.path.head == role.path &&
              r.uri.paramsSeq == twoParamSeq)
          .thenRespond(role.id.toDouble)
      }
    }

    forEvery(endpointsService.binaryOperationToFunction) {
      case (opSymbol, function) =>
        val role = findRoleOrFail(MicroserviceRole.binaryFunctionRoles, opSymbol)

        function(1, 2).await shouldBe role.id
    }
  }

  it should "call correct client endpoints while function evaluation in Separate Mode" in {
    val config = FunMeshConfig(allRoles = false, basePort = 100, msHost = "testHost")

    val endpointsService: EndpointsService = new EndpointsService(prometheusMetricsEndpoint, primaryFunction)(config) {
      override val logger: Logger = loggerMock
      private val roleToFunction = binaryOperationToFunction.map {
        case (opSymbol, function) =>
          findRoleOrFail(MicroserviceRole.binaryFunctionRoles, opSymbol) -> function
      }

      override private[tapir] val clientBackend = roleToFunction.foldLeft(HttpClientFutureBackend.stub) { // SttpBackendStub.asynchronousFuture
        case (stub, (role, _)) => stub
          .whenRequestMatches(r =>
            r.uri.port.contains(config.basePort + role.id) &&
              r.uri.host.contains(config.msHost) &&
              r.uri.path.head == role.path &&
              r.uri.paramsSeq == twoParamSeq)
          .thenRespond(role.id.toDouble)
      }
    }

    forEvery(endpointsService.binaryOperationToFunction) {
      case (opSymbol, function) =>
        val role = findRoleOrFail(MicroserviceRole.binaryFunctionRoles, opSymbol)

        function(1, 2).await shouldBe role.id
    }
  }
}
