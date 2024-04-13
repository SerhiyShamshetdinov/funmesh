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

import MicroserviceRole.Num
import ua.org.sands.funmesh.server.StringFunction._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

/*
 * Created by Serhiy Shamshetdinov
 * at 05.04.2024 16:03
 */

/*
 * Translates & evaluates function of 1 (x) variable defined as a string. BNF like:
 * [...] = optional
 * (..|..) = one of
 *
 * Addition       ::= Multiplication [ ('+' | '-') Addition ] // lowest priority
 * Multiplication ::= Power [ ('*' | '/') Multiplication ]
 * Power          ::= Value [ '^' Value ]
 * Unary          ::= UnaryOperation Unary | Value            // highest priority
 * Value          ::= Constant | Variable | PositiveNumber | '(' Addition ')'
 *
 * In the forward translation the unary '+' and '-' may follow only input start (be the first symbol), opened bracket '(' or any operation,
 * so binary '+' and '-' may be non-first symbol and follow only Value or closed bracket ')'
 *
 * To keep left to right order of the binary operations evaluation while compiling the input symbols are processed right to left
 * that leads to reversing of each Value definition and also to swapping of all binary operation parameters:
 *
 * Unary          ::= Value | Unary UnaryOperation
 * Value          ::= Constant | Variable | PositiveNumber | ')' Addition '('
 *
 */

class StringFunction(unaryOperationToFunction: Map[String, Num => Future[Num]],
                     binaryOperationToFunction: Map[String, (Num, Num) => Future[Num]]) {
  private val cache = collection.mutable.Map[String, Either[String, FunTree]]()

  def evaluate(f: String, num: Num): Future[Num] = {
    val lowerCaseFunction = f.toLowerCase
    val tree = cache.getOrElseUpdate(lowerCaseFunction, compile(lowerCaseFunction))
    tree match {
      case Left(error) => Future.failed(CompilationError(s"Error while '$f' compilation: $error"))
      case Right(tree) => tree.apply(Future.successful(num))
    }
  }

  private def compile(f: String): Either[String, FunTree] =
    translateSymbols(f).flatMap(parseAdditionLayerTree).flatMap {
      case (_, restSymbols) if restSymbols.nonEmpty =>
        Left(s"(right to left parsing) start of the function is expected but symbol ${restSymbols.head} encountered")
      case (tree, _) => Right(tree)
    }

  private val unaryOperationNames = unaryOperationToFunction.keys.toList
  private val binaryOperationNames = binaryOperationToFunction.keys.toList

  /**
   * Translates to List[FunSymbol] and also simplifies unary operations & substitutes Constant Names to numbers
   */
  private def translateSymbols(sf: String): Either[String, List[FunSymbol]] = {
    val len = sf.length

    def isUnarySign(ch: Char, symbols: List[FunSymbol]): Boolean =
      (ch == '+' || ch == '-') && (symbols.isEmpty || symbols.head.isInstanceOf[AnyOp] || symbols.head.isInstanceOf[OpenedBracket])

    @annotation.tailrec
    def translate(i: Int, symbols: List[FunSymbol]): Either[String, List[FunSymbol]] = {
      def parseSymbol[T <: FunSymbol](toSymbol: String => T, names: List[String]): Option[(FunSymbol, Int)] =
        names.foldLeft(Option.empty[(FunSymbol, Int)]) {
          case (res, name) =>
            res orElse {
              // checks the next char after matching name to ensure the end of the found symbol in the input is correct:
              // it maybe any char for the name that ends with non-alpha & non-digit (i.e. symbol char)
              // and should be non-alpha & non-digit char for the name ending with alpha or digit (clear end of the identifier)
              if (sf.startsWith(name, i) && !(i + name.length < sf.length && name.last.isLetterOrDigit && sf(i + name.length).isLetterOrDigit))
                Some(toSymbol(name), i + name.length)
              else None
            }
        }

      def parsePositiveNumber(): Option[(Number, Int)] =
        PosDoubleR.findFirstIn(sf.drop(i)).flatMap( ds =>
          ds.toDoubleOption
            .filterNot(_ => i + ds.length < sf.length && sf(i + ds.length).isLetter) // ensures last digit does not immediately follow letter
            .map(d => (Number(d), i + ds.length))
        )

      lazy val ch = sf(i)
      if (i == len)
        Right(symbols)
      else if (ch.isWhitespace)
        translate(i + 1, symbols)
      else if (isUnarySign(ch, symbols)) {
        if (ch == '+')
          translate(i + 1, symbols) // skips unary '+'
        else if (symbols.nonEmpty && symbols.head == UnaryMinus)
          translate(i + 1, symbols.tail) // skips double unary '-'
        else
          translate(i + 1, UnaryMinus :: symbols)
      } else if (openToClosed.isDefinedAt(ch))
        translate(i + 1, OpenedBracket(ch) :: symbols)
      else if (closedToOpened.isDefinedAt(ch))
        translate(i + 1, ClosedBracket(ch) :: symbols)
      else
        parseSymbol(UnaryOp, unaryOperationNames) orElse
          parseSymbol(BinaryOp, binaryOperationNames) orElse
          parseSymbol(Constants, ConstantNames) orElse
          parseSymbol(Variables, VariableNames) orElse
          parsePositiveNumber() match {
            case Some((symbol, ni)) => translate(ni, symbol :: symbols)
            case _ => Left(s"could not parse the input starting '${sf.drop(i)}'")
          }
    }

    translate(0, Nil)
  }

  private val unaryOperations = unaryOperationToFunction + ("-", (v: Num) => Future.successful(-v)) // adds local unary '-' op
  private val binaryOperations = binaryOperationToFunction

  private type FunTreeAndSymbols = (FunTree, List[FunSymbol])
  private type FunTreeParsing = Either[String, FunTreeAndSymbols]

  private def nextIsBinaryOpSymbol(symbols: List[FunSymbol], opNames: List[String]): Boolean =
    symbols.nonEmpty && (symbols.head match {
      case BinaryOp(name) => opNames.contains(name)
      case _ => false
    })

  private def opNotFoundError(name: String, prefix: String): FunTreeParsing =
    Left(s"$prefix operation '$name' function is not found'")

  private def parsePriorityLayerTree(symbols: List[FunSymbol], parseNextLayerTree: List[FunSymbol] => FunTreeParsing, layerOps: List[String]): FunTreeParsing =
    parseNextLayerTree(symbols).flatMap {
      case (leftTree, leftTailSymbols) if nextIsBinaryOpSymbol(leftTailSymbols, layerOps) =>
        parsePriorityLayerTree(leftTailSymbols.tail, parseNextLayerTree, layerOps).flatMap {
          case (rightTree, rightTailSymbols) =>
            val name = leftTailSymbols.head.asInstanceOf[BinaryOp].name
            binaryOperations.get(name).fold(opNotFoundError(name, "Binary")) { fun =>
              Right((BinaryFun(leftTree, rightTree, fun), rightTailSymbols))
            }
        }
      case other => Right(other)
    }

  private def parseAdditionLayerTree(symbols: List[FunSymbol]): FunTreeParsing =
    parsePriorityLayerTree(symbols, parseMultiplicationLayerTree, AdditionOpNames)

  private def parseMultiplicationLayerTree(symbols: List[FunSymbol]): FunTreeParsing =
    parsePriorityLayerTree(symbols, parsePowerLayerTree, MultiplicationOpNames)

  private def parsePowerLayerTree(symbols: List[FunSymbol]): FunTreeParsing =
    parsePriorityLayerTree(symbols, parseUnaryTree, PowerOpNames)

  private def parseUnaryTree(symbols: List[FunSymbol]): FunTreeParsing =
    parseValueTree(symbols).flatMap(parseUnaryOpsTree)

  private def parseUnaryOpsTree(funTreeAndSymbols: FunTreeAndSymbols): FunTreeParsing =
    funTreeAndSymbols match {
      case (valueTree, valueTailSymbols) if valueTailSymbols.nonEmpty && valueTailSymbols.head.isInstanceOf[UnaryOp] =>
        val name = valueTailSymbols.head.asInstanceOf[UnaryOp].name
        unaryOperations.get(name).fold(opNotFoundError(name, "Unary")) { fun =>
          parseUnaryOpsTree(UnaryFun(valueTree, fun), valueTailSymbols.tail)
        }
      case other => Right(other)
    }

  private def parseError(expected: String, encountered: String): FunTreeParsing =
    Left(s"(right to left parsing) $expected is expected but $encountered encountered")

  private def expectedValueError(encountered: String): FunTreeParsing =
    parseError("any value or the expression in brackets", encountered)

  private val endOfInput = "start of the function"

  private def parseValueTree(symbols: List[FunSymbol]): FunTreeParsing = {
    symbols.headOption.fold(expectedValueError(endOfInput)) {
      case Number(num) => Right((NumberFun(num), symbols.tail))
      case VarX => Right((VarFun, symbols.tail))
      case cb@ClosedBracket(_) => // 'closed bracket' due to reverse symbols order
        parseAdditionLayerTree(symbols.tail).flatMap {
          case (additionTree, additionTailSymbols) =>
            def bracketIsExpected(encountered: String): FunTreeParsing = parseError(s"'${cb.opposite}' bracket", encountered)

            additionTailSymbols.headOption.fold(bracketIsExpected(endOfInput)) {
              case OpenedBracket(bch) if bch == cb.opposite =>
                Right((additionTree, additionTailSymbols.tail))
              case other => bracketIsExpected(s"symbol $other")
            }
        }
      case other => expectedValueError(s"symbol $other")
    }
  }
}

object StringFunction {
  private val PosDoubleR = """^(([0-9]*\.[0-9]+)|[0-9]+)(e[-|+]?[0-9]+)?""".r

  private val AdditionOpNames = List("+", "-")
  private val MultiplicationOpNames = List("*", "/")
  private val PowerOpNames = List("^")

  private val Constants: Map[String, Number] = Map("pi" -> Number(Math.PI), "e" -> Number(Math.E))
  private val ConstantNames = Constants.keys.toList
  private val Variables: Map[String, Value] = Map("x" -> VarX)
  private val VariableNames = Variables.keys.toList

  private case class CompilationError(error: String) extends Exception(error) with NoStackTrace

  private sealed trait FunSymbol

  private trait AnyOp extends FunSymbol

  private case class UnaryOp(name: String) extends AnyOp

  private case class BinaryOp(name: String) extends AnyOp

  private val UnaryMinus = UnaryOp("-")

  private val openToClosed = Map('(' -> ')', '{' -> '}', '[' -> ']')
  private val closedToOpened = openToClosed.map(_.swap)

  private case class OpenedBracket(name: Char) extends FunSymbol {
    def opposite: Char = openToClosed(name)
  }

  private case class ClosedBracket(name: Char) extends FunSymbol {
    def opposite: Char = closedToOpened(name)
  }

  private trait Value extends FunSymbol

  private case class Number(num: Num) extends Value

  private case object VarX extends Value

  private sealed trait FunTree {
    def apply(x: Future[Num]): Future[Num]
  }

  private case class BinaryFun(left: FunTree, right: FunTree, opFunction: (Num, Num) => Future[Num]) extends FunTree {
    override def apply(x: Future[Num]): Future[Num] = {
      val rightFuture = right(x)
      left(x).flatMap { leftValue =>
        rightFuture.flatMap { rightValue =>
          opFunction(rightValue, leftValue) // parameters are swapped due to the reverse processing order of the input FunSymbols
        }
      }
    }
  }

  private case class UnaryFun(tree: FunTree, opFunction: Num => Future[Num]) extends FunTree {
    override def apply(x: Future[Num]): Future[Num] = tree(x).flatMap(opFunction)
  }

  private case class NumberFun(num: Num) extends FunTree {
    private val futureNum = Future.successful(num)

    override def apply(x: Future[Num]): Future[Num] = futureNum
  }

  private case object VarFun extends FunTree {
    override def apply(x: Future[Num]): Future[Num] = x
  }
}
