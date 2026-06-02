package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen

case class RedisAction(
    ctx: ScenarioContext,
    next: Action,
    clientPool: RedisClientPool,
    commandFactory: Session => io.gatling.commons.validation.Validation[RedisCommand],
    saveAsVar: Option[String],
    reqName: Option[String],
) extends ChainableAction with NameGen {

  override val name: String = genName("redisAction")

  private val statsEnabled: Boolean = reqName.isDefined
  private val clock                 = ctx.coreComponents.clock

  override def execute(session: Session): Unit =
    try {
      commandFactory(session) match {
        case Success(command) =>
          val start  = if (statsEnabled) clock.nowMillis else 0L
          val result = clientPool.withClient(client => command.execute(client))
          val end    = if (statsEnabled) clock.nowMillis else 0L

          val updatedSession = RedisAction.updateSessionWithResult(session, saveAsVar, result)

          if (statsEnabled) {
            statsEngine.logResponse(
              session.scenario,
              session.groups,
              reqName.get,
              start,
              end,
              OK,
              None,
              None,
            )
          }

          next ! updatedSession

        case Failure(message) =>
          logger.error("Redis {} expression resolution failed: {}", name, message)
          if (statsEnabled) {
            statsEngine.logResponse(
              session.scenario,
              session.groups,
              reqName.get,
              clock.nowMillis,
              clock.nowMillis,
              KO,
              None,
              Some(message),
            )
          }
          next ! session.markAsFailed
      }
    } catch {
      case e: Exception =>
        logger.error("Redis {} failed", name, e)
        if (statsEnabled) {
          statsEngine.logResponse(
            session.scenario,
            session.groups,
            reqName.getOrElse(name),
            clock.nowMillis,
            clock.nowMillis,
            KO,
            None,
            Some(e.getMessage),
          )
        }
        ctx.coreComponents.statsEngine.logRequestCrash(
          session.scenario,
          session.groups,
          name,
          e.getMessage,
        )
        next ! session.markAsFailed
    }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
}

object RedisAction {
  private[redis] def updateSessionWithResult(
      session: Session,
      saveAsVar: Option[String],
      result: Option[Any],
  ): Session =
    saveAsVar match {
      case Some(varName) => result.fold(session)(value => session.set(varName, value))
      case None          => session
    }
}
