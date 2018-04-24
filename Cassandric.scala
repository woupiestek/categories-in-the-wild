package nl.ing.app.calendars.migration

import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.RegularStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Statement
import scala.language.higherKinds

trait Cassandric[M[_]] {

  def execution(statement: String): M[ResultSet]

  def execution(statement: Statement): M[ResultSet]

  def preparation(statement: String): M[PreparedStatement]

  def preparation(statement: RegularStatement): M[PreparedStatement]

}

object Cassandric {

  def planner[M[_]](implicit M: Cassandric[M]): Cassandric[M] = M

}






