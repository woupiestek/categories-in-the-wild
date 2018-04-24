package nl.ing.app.calendars.migration

import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.RegularStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Session
import com.datastax.driver.core.Statement
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import scala.collection.generic.CanBuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.concurrent.Promise
import scala.language.higherKinds

sealed trait Async[+X] {
  protected def run(implicit session: Session, ec: ExecutionContext): Future[X]
}

object Async {

  implicit class SessionOps(session: Session) {
    def execute[X](async: Async[X])(implicit executionContext: ExecutionContext): Future[X] =
      async.run(session, executionContext)
  }

  private class AsyncUnit[X](x: => X) extends Async[X] {
    override def run(implicit session: Session, ec: ExecutionContext): Future[X] = successful(x)
  }

  private class AsyncBind[X, Y](x: => Async[X], f: X => Async[Y]) extends Async[Y] {
    override def run(implicit session: Session, ec: ExecutionContext): Future[Y] = x.run.flatMap((x: X) => f(x).run)
  }

  private class AsyncTraverse[A, B, T[X] <: TraversableOnce[X]](in: => T[A], fn: A => Async[B])(implicit cbf: CanBuildFrom[T[A], B, T[B]]) extends Async[T[B]] {
    override def run(implicit session: Session, ec: ExecutionContext): Future[T[B]] = traverse(in)((a: A) => fn(a).run)
  }

  implicit object AsyncIsTraversingMonad extends TraversingMonad[Async] {
    override def unit[X](x: X): Async[X] = new AsyncUnit[X](x)

    override def bind[X, Y](mx: Async[X])(f: X => Async[Y]): Async[Y] = new AsyncBind(mx, f)

    override def traverse[A, B, T[X] <: TraversableOnce[X]](in: T[A])(fn: A => Async[B])(implicit cbf: CanBuildFrom[T[A], B, T[B]]): Async[T[B]] =
      new AsyncTraverse(in, fn)
  }

  private class AsyncLift[X](f: Session => ListenableFuture[X]) extends Async[X] {
    override def run(implicit session: Session, ec: ExecutionContext): Future[X] = {
      val p = Promise[X]()
      Futures.addCallback(f(session), new FutureCallback[X] {
        def onFailure(t: Throwable): Unit = p failure t

        def onSuccess(v: X): Unit = p success v
      })
      p.future
    }
  }

  implicit object AsyncIsCassandric extends Cassandric[Async] {

    override def execution(statement: String): Async[ResultSet] = new AsyncLift(_.executeAsync(statement))

    override def execution(statement: Statement): Async[ResultSet] = new AsyncLift(_.executeAsync(statement))

    override def preparation(statement: String): Async[PreparedStatement] = new AsyncLift(_.prepareAsync(statement))

    override def preparation(statement: RegularStatement): Async[PreparedStatement] = new AsyncLift(_.prepareAsync(statement))
  }

}