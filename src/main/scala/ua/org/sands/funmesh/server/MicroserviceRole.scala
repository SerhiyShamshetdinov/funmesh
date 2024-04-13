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

import ua.org.sands.funmesh.server.MicroserviceRole.Num

import java.security.InvalidParameterException

/*
 * Created by Serhiy Shamshetdinov
 * at 27.03.2024 14:10
 */

sealed trait MicroserviceRole[Arg] {
  val id: Int
  val path: String
  val symbol: String
  val description: String
  val function: Arg => Num

  override def toString: String = s" function MicroService Role '$path' (symbol '$symbol') with id=$id${if (description.nonEmpty) s": $description" else ""}"
}

case class UnaryFunctionRole(id: Int, path: String, symbol: String, description: String = "", function: Num => Num) extends MicroserviceRole[Num] {
  override def toString: String = "Unary " + super.toString
}

case class BinaryFunctionRole(id: Int, path: String, symbol: String, description: String = "", function: ((Num, Num)) => Num) extends MicroserviceRole[(Num, Num)] {
  override def toString: String = "Binary " + super.toString
}

object MicroserviceRole {
  type Num = Double

  val allRoles: List[MicroserviceRole[_]] = List(
    BinaryFunctionRole(1, "add",    "+",      "Addition", p => p._1 + p._2),
    BinaryFunctionRole(2, "sub",    "-",      "Subtraction", p => p._1 - p._2),
    BinaryFunctionRole(3, "mul",    "*",      "Multiplication", p => p._1 * p._2),
    BinaryFunctionRole(4, "div",    "/",      "Division", p => p._1 / p._2),
    BinaryFunctionRole(5, "power",  "^",      "Power", p => Math.pow(p._1, p._2)),
    UnaryFunctionRole( 6, "abs",    "abs",    "Absolute value",             Math.abs),
    UnaryFunctionRole( 7, "sqrt",   "sqrt",   "Square root",                Math.sqrt),
    UnaryFunctionRole( 8, "cbrt",   "cbrt",   "Cube root",                  Math.cbrt),
    UnaryFunctionRole( 9, "log",    "log",    "Natural logarithm (base e)", Math.log),
    UnaryFunctionRole(10, "log10",  "log10",  "Base 10 logarithm",          Math.log10),
    UnaryFunctionRole(11, "sin",    "sin",    "Sine",                       Math.sin),
    UnaryFunctionRole(12, "cos",    "cos",    "Cosine",                     Math.cos),
    UnaryFunctionRole(13, "tg",     "tg",     "Tangent",                    Math.tan),
    UnaryFunctionRole(14, "arcsin", "arcsin", "Arc sine",                   Math.asin),
    UnaryFunctionRole(15, "arccos", "arccos", "Arc cosine",                 Math.acos),
    UnaryFunctionRole(16, "arctg",  "arctg",  "Arc tangent",                Math.atan)
  )
  private val roleIds = allRoles.map(_.id)

  val maxRoleId: Int = roleIds.max

  require(roleIds.forall(_ > 0) && roleIds.distinct.size == roleIds.size, "Primitive Role Id should be positive & be unique")
  require(allRoles.map(_.symbol).distinct.size == roleIds.size, "Primitive Roles should have unique symbols")

  val roleById: Map[Int, MicroserviceRole[_]] = allRoles.map(r => r.id -> r).toMap.withDefault(id => throw new InvalidParameterException(s"Unknown roleId=$id"))

  lazy val unaryFunctionRoles: List[UnaryFunctionRole] = allRoles.collect { case r: UnaryFunctionRole => r }
  lazy val binaryFunctionRoles: List[BinaryFunctionRole] = allRoles.collect { case r: BinaryFunctionRole => r }
}
