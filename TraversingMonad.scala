package nl.ing.app.calendars.migration

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

trait TraversingMonad[M[_]] {
  def unit[X](x: X): M[X]

  def bind[X, Y](mx: M[X])(f: X => M[Y]): M[Y]

  def map[X, Y](mx: M[X])(f: X => Y): M[Y] = bind(mx)(x => unit(f(x)))

  //added to prevent stack-overflows
  def traverse[A, B, T[X] <: TraversableOnce[X]](in: T[A])(fn: A => M[B])(implicit cbf: CanBuildFrom[T[A], B, T[B]]): M[T[B]]
}

object TraversingMonad {

  def traverse[A, T[X] <: TraversableOnce[X], M[_], B](
    in: T[A])(
    fn: A => M[B])(
    implicit cbf: CanBuildFrom[T[A], B, T[B]],
    M: TraversingMonad[M]): M[T[B]] =
    M.traverse(in)(fn)

  implicit class MonadOps[M[_], X](mx: M[X])(implicit M: TraversingMonad[M]) {
    def flatMap[Y](f: X => M[Y]): M[Y] = M.bind(mx)(f)

    def map[Y](f: X => Y): M[Y] = M.map(mx)(f)

    def flatMap2[Y, Z](my: M[Y])(f: (X, Y) => M[Z]): M[Z] = flatMap(x => M.bind(my)(f(x, _)))

    def flatMap3[Y, Z, A](my: M[Y],mz: M[Z])(f: (X, Y, Z) => M[A]): M[A] = flatMap2(my)((x, y) => M.bind(mz)(f(x, y, _)))
  }

  def lift[M[_], X](x: X)(implicit M: TraversingMonad[M]): M[X] = M.unit(x)

}