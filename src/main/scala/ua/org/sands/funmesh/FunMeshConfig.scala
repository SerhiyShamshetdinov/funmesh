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

import org.slf4j.{Logger, LoggerFactory}
import ua.org.sands.funmesh.server.MicroserviceRole.{Num, maxRoleId, roleById}
import ua.org.sands.funmesh.server.MicroserviceRole

/*
 * Created by Serhiy Shamshetdinov
 * at 27.03.2024 20:11
 */

case class FunMeshConfig private (basePort: Int,
                                  allRoles: Boolean,
                                  roleId: Int,
                                  msHost: String,
                                  serverHost: String,
                                  headless: Boolean) {
  private lazy val mode = if (allRoles) "Combined Mode (with primary and all primitive functions)" else "Separate Mode (with primary function only)"
  val serverDescription: String = if (allRoles || roleId == 0) s"Primary Server in $mode" else s"Microservice Server with ${roleById(roleId).description} primitive function"

  def setAllRoles(): FunMeshConfig = copy(allRoles = true, roleId = 0)
  def setRoleId(id: Int): FunMeshConfig = copy(allRoles = false, roleId = id)
  def serverPort: Int = basePort + roleId

  override def toString: String =
    s"basePort=$basePort, allRoles=$allRoles, roleId=$roleId, msHost=$msHost, serverHost=$serverHost, headless=$headless"
}

object FunMeshConfig {
  def apply(basePort: Int = 8080,
            allRoles: Boolean = true,
            roleId: Int = 0,
            msHost: String = "localhost",
            serverHost: String = "0.0.0.0",
            headless: Boolean = false
           ): FunMeshConfig = {
    val nonRestricted = new FunMeshConfig(basePort, allRoles, roleId, msHost.toLowerCase(), serverHost, headless)
    if (allRoles) nonRestricted.setAllRoles()
    else nonRestricted
  }

  private val configLogger: Logger = LoggerFactory.getLogger(getClass.getName)

  private val numClass: String = classOf[Num].getSimpleName

  def startDescription(config: FunMeshConfig): String =
    s"FunMesh ${config.serverDescription} is started with $config"

  def help(config: FunMeshConfig): String =
    s"${startDescription(config)}\n${usage(config)}"

  def usage(config: FunMeshConfig): String =
    s"""
       |Function Mesh usage:
       |
       |funmesh [roleId=all|R] [basePort=NNNN] [msHost=DNorIP] [serverHost=DNorIP] [headless=true|false] [?|help]
       |
       |Depending on 'roleId' parameter the Function Mesh app may be run implementing 3 types of behavior for 2 general modes:
       |- Single server with complex function & all predefined primitive functions: 'roleId=all' (combined mode, default)
       |- Primary server implementing only complex function that calls primitive functions of microservices: 'roleId=0' (separate mode)
       |- Microservice server that implements one primitive function from the predefined set: 'roleId=(1 to ${MicroserviceRole.maxRoleId})' (separate mode)
       |
       |Primary server (in separate or combined mode) always listens 'basePort' (default is ${FunMeshConfig().basePort}) and
       |provides /eval?f=string_with_complex_function&x=$numClass endpoint to evaluate f(x).
       |
       |Primitive function microservice in separate mode listens 'basePort+roleId' port providing endpoint with the name of the primitive function
       |/<fun_name>?x=$numClass to evaluate primitive unary function or /<fun_name>?x=$numClass&y=$numClass to evaluate binary one.
       |For example, /abs?x=$numClass and /add?x=$numClass&y=$numClass.
       |The primitive function of microservice is specified by the 'roleId' parameter.
       |In separate mode all servers of the Function Mesh should be run with the same basePort for proper function calls.
       |
       |In combined mode (single server) the evaluation of the complex function calls above endpoints of itself to evaluate primitive
       |functions (that also gives evaluation of HTTP overhead).
       |
       |'msHost' parameter defines the common host of all microservice servers and is used by primary server to call microservices
       |in separate mode. Default is 'localhost'.
       |
       |'serverHost' parameter defines the host on which server accepts connections. Default value is "0.0.0.0" that means
       |server "listens" for and accepts connections from any IP address. Pass "localhost" if you want to limit server visibility
       |to host where server is started (in this case external connection to Docker container where funmesh is started will fail).
       |
       |In 'headless' mode server does not use StdIn to wait for exit command (Enter). Default is 'false' and the started
       |funmesh will block terminal. Passing 'true' to 'headless' starts funmesh in service mode.
       |In any mode you may use /shutdown endpoint on primary server to stop server and all microservices.
       |
       |Current microservice roles (and its string function symbols) are:
       |${MicroserviceRole.allRoles.map(role => s"${role.id} - ${role.path} '${role.symbol}'").mkString("", "\n", "")}
       |
       |Complex string function is case-insensitive and accepts 2 standard constants: 'PI' & 'E'.
       |Binary operations are evaluated left to right. Power `^` has the highest priority between binary operations.
       |Unary operations (including all unary functions) are evaluated first.
       |
       |Go to http://localhost:${config.serverPort}/docs to open SwaggerUI.
       |Use http://localhost:${config.serverPort}/metrics to access Prometheus Metrics.
       |Use http://localhost:${config.serverPort}/help to access the used config and this help description and may be used as health check.
       |Use http://localhost:${config.basePort}/shutdown to terminate all microservices & primary server itself.
       |""".stripMargin

  def parse(args: Array[String])(implicit logger: Logger = configLogger): Either[String, FunMeshConfig] = { // logger here is for testing purposes
    val (errors, config) = args.map(_.split("[=:]")).foldLeft((List.empty[String], FunMeshConfig())) {

      case ((errors, config), Array(k, v)) if k.equalsIgnoreCase("basePort") =>
        v.toIntOption.filter(iv => 0 <= iv && iv <= 65535)
          .fold((errors :+ s"'basePort' parameter value should be an integer between 0 & 65535 but got '$v'", config))(iv => (errors, config.copy(basePort = iv)))

      case ((errors, config), Array(k, v)) if k.equalsIgnoreCase("roleId") =>
        if (v.equalsIgnoreCase("all")) (errors, config.setAllRoles())
        else
          v.toIntOption.filter(i => 0 <= i && i <= maxRoleId)
            .fold((errors :+ s"'roleId' parameter value should be 'all' or integer from 0 to $maxRoleId but got '$v'", config))(iv => (errors, config.setRoleId(iv)))

      case ((errors, config), a) if a.head.equalsIgnoreCase("msHost") =>
        a.lift(1).filterNot(_.isEmpty)
          .fold((errors :+ s"'msHost' parameter value should not be empty", config))(iv => (errors, config.copy(msHost = iv.toLowerCase())))

      case ((errors, config), a) if a.head.equalsIgnoreCase("serverHost") =>
        a.lift(1).filterNot(_.isEmpty)
          .fold((errors :+ s"'serverHost' parameter value should not be empty", config))(iv => (errors, config.copy(serverHost = iv.toLowerCase())))

      case ((errors, config), Array(k, v)) if k.equalsIgnoreCase("headless") =>
        v.toBooleanOption
          .fold((errors :+ s"'headless' parameter value should be 'true' or 'false' but got '$v'", config))(iv => (errors, config.copy(headless = iv)))

      case ((errors, config), Array(k)) if Seq("?", "help").exists(k.toLowerCase.contains) =>
        (errors :+ "", config)

      case ((errors, config), a) =>
        (errors :+ s"unknown parameter or absent value - '${a(0)}'", config)
    }
    if (errors.nonEmpty) {
      val errorText = errors.filterNot(_.isEmpty).mkString("; ")
      if (errorText.nonEmpty) logger.error(errorText)
      Left(usage(config))
    } else
      Right(config)
  }
}
