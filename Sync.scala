package nl.ing.app.calendars.migration.testutils

import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.RegularStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Session
import com.datastax.driver.core.Statement
import nl.ing.app.calendars.migration.Cassandric
import nl.ing.app.calendars.migration.TraversingMonad
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

case class Sync[+X](run: Session => X)

object Sync {

  implicit object SyncIsTraversingMonad extends TraversingMonad[Sync] {
    override def unit[X](x: X): Sync[X] = Sync(_ => x)

    override def bind[X, Y](mx: Sync[X])(f: X => Sync[Y]): Sync[Y] = Sync((session: Session) => f(mx.run(session)).run(session))

    override def traverse[A, B, T[X] <: TraversableOnce[X]](in: T[A])(fn: A => Sync[B])(implicit cbf: CanBuildFrom[T[A], B, T[B]]): Sync[T[B]] =
      Sync(session => in.map((a: A) => fn(a).run(session)).to[T])
  }


  implicit object SyncIsCassandric extends Cassandric[Sync] {

    override def execution(statement: String): Sync[ResultSet] = Sync(_.execute(statement))

    override def execution(statement: Statement): Sync[ResultSet] = Sync(_.execute(statement))

    override def preparation(statement: String): Sync[PreparedStatement] = Sync(_.prepare(statement))

    override def preparation(statement: RegularStatement): Sync[PreparedStatement] = Sync(_.prepare(statement))

  }

}





