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

import org.scalatest.{EitherValues, Inspectors}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Try

/*
 * Created by Serhiy Shamshetdinov
 * at 10.04.2024 16:11
 */

trait TestBase extends AnyFlatSpec with Matchers with EitherValues with Inspectors {
  implicit class AwaitFuture[T](t: Future[T]) {
    def await: T = Await.result(t, 1 minute)
    def awaitReady: Try[T] = {
      Await.ready(t, 1 minute)
      t.value.get
    }
  }
}
