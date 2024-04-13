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

import ua.org.sands.funmesh.TestBase
import ua.org.sands.funmesh.server.MicroserviceRole._

import scala.concurrent.Future

/*
 * Created by Serhiy Shamshetdinov
 * at 10.04.2024 16:13
 */

class StringFunctionTest extends TestBase {
  private val unaryToFunction: Map[String, Num => Future[Num]] = unaryFunctionRoles.map(r => (r.symbol, (x: Num) => Future.successful(r.function(x)))).toMap
  private val binaryToFunction = binaryFunctionRoles.map(r => (r.symbol, (x: Num, y: Num) => Future.successful(r.function((x, y))))).toMap

  val stringFunction: StringFunction = new StringFunction(unaryToFunction, binaryToFunction)

  "stringFunction" should "evaluate expressions" in {
    val x = 2.0 // test value
    forAll(Seq(
      " + 1 " -> 1.0,
      ".1e1 " -> 1.0,
      "1e-308" -> 1e-308,
      "1e+308" -> 1e308,
      "++1" -> 1.0,
      "-1" -> -1.0,
      "--1" -> 1.0,
      "+ - +-+-+1" -> -1.0,
      "(-+1)" -> -1.0,
      "(pi)" -> Math.PI,
      "(PI)" -> Math.PI,
      "{e}" -> Math.E,
      "{E}" -> Math.E,
      "[+-1]" -> -1.0,
      "({[+-1]})" -> -1.0,
      "1E10" -> 1e10,
      "1+2*3" -> 7.0,
      "6/2+2*3" -> 9.0,
      "1+2+3" -> 6.0,
      "1-2-3" -> -4.0,
      "2--3" -> 5.0,
      "2+-3" -> -1.0,
      "2-(-[-3])" -> -1.0,
      "2+---3" -> -1.0,
      "2++++3" -> 5.0,
      "1*2*3" -> 6.0,
      "6/2/3" -> 1.0,
      "2^3^4" -> 4096.0,
      "(2^3)^4" -> 4096.0,
      "2^(3^4)" -> Math.pow(2, 81),
      "1+2*3^4" -> 163.0,
      "(1+2)*3^4" -> 243.0,
      "((1+2)*3)^4" -> 81.0 * 81.0,
      "1^2*3+4" -> 7.0,
      "1+2*3*4^5" -> 6145.0,
      "log10 1000" -> 3.0,
      "log e" -> 1.0,
      "log(e^6)" -> 6.0,
      "log e^6" -> 1.0,
      "(log e)^6" -> 1.0,
      "x" -> x,
      "x-x" -> 0.0,
      "x^x" -> x*x,
      "(sin x)^2+(cos x)^2" -> 1.0,
      "sin x ^ 2 + cos x ^ 2" -> 1.0, // unary sin & cos have highest priority
      "sin(x^2) + cos(x^2)" -> {
        val x2 = x * x
        Math.sin(x2) + Math.cos(x2)
      },
      "arcsin sin (pi/6)" -> Math.PI / 6,
      "arccos cos (pi/6)" -> Math.PI / 6,
      "arctg tg (pi/6)" -> Math.PI / 6,
      "sin arcsin 1.0" -> 1.0,
      "cos arccos 1.0" -> 1.0,
      "tg arctg 1.0" -> 1.0,
      "sin (pi/6)" -> 0.5,
      "cos (pi/3)" -> 0.5,
      "tg (pi/4)" -> 1.0,
      "x^3+x^2+x^1+x^0" -> 15.0,
      "sqrt(x^2)" -> x,
      "sqrt x^2" -> x,
      "sqrt 16" -> 4.0,
      "cbrt(x^3)" -> x,
      "cbrt x^3" -> x,
      "cbrt 64" -> 4.0,
      "abs -5" -> 5.0,
      "abs 5" -> 5.0,
      "1/0" -> Double.PositiveInfinity,
      "1e+310" -> Double.PositiveInfinity,
      "-1e310" -> Double.NegativeInfinity,
      "1e-350" -> 0.0,
      "sqrt -1" -> Double.NaN,
      "arccos 2" -> Double.NaN,
      "arcsin 2" -> Double.NaN,
    )) {
      case (sf, result) if result.isNaN =>
        stringFunction.evaluate(sf, x).await.isNaN shouldBe true

      case (sf, result) =>
        stringFunction.evaluate(sf, x).await shouldBe result +- 1e-10
    }
  }

  it should "fail on wrong input" in {
    forAll(Seq(
      " " -> "Error while ' ' compilation: (right to left parsing) any value or the expression in brackets is expected but start of the function encountered",
      "()" -> "Error while '()' compilation: (right to left parsing) any value or the expression in brackets is expected but symbol OpenedBracket(() encountered",
      "sin10" -> "Error while 'sin10' compilation: could not parse the input starting 'sin10'",
      "x5" -> "Error while 'x5' compilation: could not parse the input starting 'x5'",
      "5x" -> "Error while '5x' compilation: could not parse the input starting '5x'",
      "10sin 10" -> "Error while '10sin 10' compilation: could not parse the input starting '10sin 10'",
      "1.e10" -> "Error while '1.e10' compilation: could not parse the input starting '.e10'",
      "test 1.0" -> "Error while 'test 1.0' compilation: could not parse the input starting 'test 1.0'",
      "5 + test(1.0)" -> "Error while '5 + test(1.0)' compilation: could not parse the input starting 'test(1.0)'",
      "1e-" -> "Error while '1e-' compilation: could not parse the input starting '1e-'",
      "1-" -> "Error while '1-' compilation: (right to left parsing) any value or the expression in brackets is expected but symbol BinaryOp(-) encountered",
      "1e 10" -> "Error while '1e 10' compilation: could not parse the input starting '1e 10'",
      "" -> "Error while '' compilation: (right to left parsing) any value or the expression in brackets is expected but start of the function encountered",
      "(1" -> "Error while '(1' compilation: (right to left parsing) start of the function is expected but symbol OpenedBracket(() encountered",
      "-1)" -> "Error while '-1)' compilation: (right to left parsing) '(' bracket is expected but start of the function encountered",
      "[(-1])" -> "Error while '[(-1])' compilation: (right to left parsing) '[' bracket is expected but symbol OpenedBracket(() encountered",
      "5 * / 2" -> "Error while '5 * / 2' compilation: (right to left parsing) any value or the expression in brackets is expected but symbol BinaryOp(*) encountered",
    )) {
      case (fsf, error) =>
        stringFunction.evaluate(fsf, 0).awaitReady.failed.get.getMessage shouldBe error
    }
  }
}
