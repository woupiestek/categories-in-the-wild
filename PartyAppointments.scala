package nl.ing.app.calendars.migration

import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.RegularStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.querybuilder.QueryBuilder.{ eq => eqs }
import nl.ing.app.calendars.migration.Cassandric.planner
import nl.ing.app.calendars.migration.TraversingMonad._
import scala.collection.JavaConverters._
import scala.language.higherKinds

object PartyAppointments {

  def cleanUp[M[_] : Cassandric : TraversingMonad]: M[RecordCount] = {
    def isCancelled(rs: ResultSet) = rs.asScala.exists(row => "Cancelled".equalsIgnoreCase(row.getString("state")))

    def byKey(psState: PreparedStatement, psDelete: PreparedStatement, key: Row): M[Boolean] =
      planner.execution(bindUUIDKey(psState.bind(), key)).flatMap {rs =>
        if (isCancelled(rs)) planner.execution(bindPartyKey(psDelete.bind(), key)).map(_ => true)
        else lift(false)
      }

    planner.execution(selectAllKeys).flatMap3(
      planner.preparation(selectStateFromActivitiesUUID),
      planner.preparation(deleteKey))((keys, psState, psDelete) => traverse(keys.asScala)(byKey(psState, psDelete, _)))
      .map(iterable => RecordCount(processed = iterable.size, delete = iterable.count(x => x)))
  }

  val tableName = "appointments_by_party_id"
  val country = "country"
  val partyid = "partyid"
  val endtime = "endtime"
  val starttime = "starttime"
  val appointmentid = "appointmentid"
  val keyColumns = Vector(country, partyid, endtime, starttime, appointmentid)
  val selectAllKeys: RegularStatement = select(keyColumns: _*).from(tableName)
  val deleteKey: RegularStatement = keyColumns.foldLeft(delete().from(tableName).where())((x, y) => x.and(eqs(y, bindMarker(y))))

  val activityid = "activityid"
  val state = "state"
  val activitiesUUID = "activities_uuid"
  val selectStateFromActivitiesUUID: RegularStatement =
    select(state)
      .from(activitiesUUID)
      .where(eqs(country, bindMarker(country)))
      .and(eqs(activityid, bindMarker(activityid)))

  def bindPartyKey(boundStatement: BoundStatement, row: Row): BoundStatement = boundStatement
    .setString(country, row.getString(country))
    .setString(partyid, row.getString(partyid))
    .setTimestamp(endtime, row.getTimestamp(endtime))
    .setTimestamp(starttime, row.getTimestamp(starttime))
    .setUUID(appointmentid, row.getUUID(appointmentid))

  def bindUUIDKey(boundStatement: BoundStatement, row: Row): BoundStatement = boundStatement
    .setString(country, row.getString(country))
    .setUUID(activityid, row.getUUID(appointmentid))
}
