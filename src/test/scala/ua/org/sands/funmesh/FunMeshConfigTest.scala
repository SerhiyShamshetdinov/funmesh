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

import org.mockito.Mockito.{verify, verifyNoMoreInteractions}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.slf4j.Logger

/*
 * Created by Serhiy Shamshetdinov
 * at 12.04.2024 17:17
 */

class FunMeshConfigTest extends TestBase {

  private val defaultConfig: FunMeshConfig = FunMeshConfig()
  private val defaultBasePort: Int = defaultConfig.basePort

  "FunMeshConfig" should "reset roleId to 0 when setting allRoles=true" in {
    FunMeshConfig(roleId = 5, allRoles = true).roleId shouldBe 0
    FunMeshConfig(roleId = 5, allRoles = false).setAllRoles().roleId shouldBe 0
  }

  it should "reset allRoles to false when setting roleId" in {
    FunMeshConfig(allRoles = true).setRoleId(5).allRoles shouldBe false
  }

  "parse" should "correctly parse parameters" in {
    forEvery(Seq(
      "" ->
        Right(FunMeshConfig()),

      "mshost=testHost BASEport:99 ROLEid=5 serverHost=LOCALHOST HeadLess=TRUE" ->
        Right(FunMeshConfig(basePort = 99, allRoles = false, roleId = 5, msHost = "testHOST", serverHost = "localhost", headless = true)),

      "roleId=5 roleId=all" -> // correctly accept last
        Right(FunMeshConfig(allRoles = true, roleId = 0, serverHost = "0.0.0.0", headless = false)),

      "roleId=ALL roleId=6 headless:False" -> // correctly accept last
        Right(FunMeshConfig(allRoles = false, roleId = 6, headless = false)),

      "mshost=testHostFirst mshost:testHostLast" -> // accept last
        Right(FunMeshConfig(msHost = "testhostlast")),

      "basePort:90 basePort:100" -> // accept last
        Right(FunMeshConfig(basePort = 100)),

      "-HELP" ->
        Left(FunMeshConfig.usage(defaultConfig)),

      "-HELP basePort:100 roleId=5" ->
        Left(FunMeshConfig.usage(FunMeshConfig(basePort = 100, allRoles = false, roleId = 5))),

      "--?" ->
        Left(FunMeshConfig.usage(defaultConfig)),

      "/help" ->
        Left(FunMeshConfig.usage(defaultConfig)),

      "help" ->
        Left(FunMeshConfig.usage(defaultConfig)),

      "?" ->
        Left(FunMeshConfig.usage(defaultConfig)),
    )) {
      case (str, result) =>
        implicit val loggerMock: Logger = mock[Logger]
        FunMeshConfig.parse(str.split(" ").filterNot(_.isEmpty)) shouldBe result
        verifyNoMoreInteractions(loggerMock)
    }
  }

  it should "log an error and return help" in {
    forEvery(Seq(
      "basePort=" -> "unknown parameter or absent value - 'basePort'",
      "basePort=-1" -> "'basePort' parameter value should be an integer between 0 & 65535 but got '-1'",
      "basePort=65536" -> "'basePort' parameter value should be an integer between 0 & 65535 but got '65536'",
      "roleId=" -> "unknown parameter or absent value - 'roleId'",
      "roleId=every" -> "'roleId' parameter value should be 'all' or integer from 0 to 16 but got 'every'",
      "roleId=-1" -> "'roleId' parameter value should be 'all' or integer from 0 to 16 but got '-1'",
      "roleId=100" -> "'roleId' parameter value should be 'all' or integer from 0 to 16 but got '100'",
      "mshost=testHost BASEport:8080 ROLEid=0 UnknownParameter" -> "unknown parameter or absent value - 'UnknownParameter'",
      "msHost:" -> "'msHost' parameter value should not be empty",
      "msHost=" -> "'msHost' parameter value should not be empty",
      "msHost" -> "'msHost' parameter value should not be empty",
      "serverHost:" -> "'serverHost' parameter value should not be empty",
      "serverHost=" -> "'serverHost' parameter value should not be empty",
      "serverHost" -> "'serverHost' parameter value should not be empty",
      "headless=" -> "unknown parameter or absent value - 'headless'",
      "headless=-1" -> "'headless' parameter value should be 'true' or 'false' but got '-1'",
      "headless=YES" -> "'headless' parameter value should be 'true' or 'false' but got 'YES'",
    )) {
      case (str, error) =>
        implicit val loggerMock: Logger = mock[Logger]
        FunMeshConfig.parse(str.split(" ").filterNot(_.isEmpty)) shouldBe Left(FunMeshConfig.usage(defaultConfig))
        verify(loggerMock).error(error)
    }
  }
}
