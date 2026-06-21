package org.galaxio.gatling.storage

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{
  Connection,
  Driver,
  DriverManager,
  DriverPropertyInfo,
  PreparedStatement,
  ResultSet,
  SQLFeatureNotSupportedException,
  Statement,
}
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

private[storage] object JdbcTestSupport {
  final class RecordingJdbcDriver(urlPrefix: String, rows: Seq[String] = Seq.empty) extends Driver {
    val state: RecordingState = new RecordingState(rows)

    override def acceptsURL(url: String): Boolean = url.startsWith(urlPrefix)

    override def connect(url: String, info: Properties): Connection =
      if (acceptsURL(url)) createConnection() else null

    override def getMajorVersion: Int = 1

    override def getMinorVersion: Int = 0

    override def getPropertyInfo(url: String, info: Properties): Array[DriverPropertyInfo] = Array.empty

    override def jdbcCompliant: Boolean = false

    override def getParentLogger: Logger = Logger.getGlobal

    private def createConnection(): Connection = {
      val connectionHandler = new InvocationHandler {
        private var closed = false

        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "createStatement"  => createStatement()
          case "prepareStatement" => createPreparedStatement()
          case "close"            =>
            if (!closed) {
              closed = true
              state.closeConnection()
            }
            null
          case "isClosed"         => java.lang.Boolean.valueOf(closed)
          case "unwrap"           => null
          case "isWrapperFor"     =>
            java.lang.Boolean.FALSE
          case _                  => defaultValue(method.getReturnType)
        }
      }

      proxyOf(classOf[Connection], connectionHandler)
    }

    private def createStatement(): Statement = {
      val statementHandler = new InvocationHandler {
        private var closed = false

        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "execute"      =>
            val sql = args.headOption.map(_.asInstanceOf[String]).getOrElse("")
            if (sql.startsWith("CREATE TABLE")) {
              state.ddlCount.incrementAndGet()
              state.lastDdl = sql
            }
            java.lang.Boolean.TRUE
          case "executeQuery" =>
            state.queryCount.incrementAndGet()
            createResultSet()
          case "close"        =>
            if (!closed) {
              closed = true
              state.closeStatement()
            }
            null
          case "unwrap"       => null
          case "isWrapperFor" => java.lang.Boolean.FALSE
          case _              => defaultValue(method.getReturnType)
        }
      }

      proxyOf(classOf[Statement], statementHandler)
    }

    private def createPreparedStatement(): PreparedStatement = {
      val statementHandler = new InvocationHandler {
        private var batchCount = 0
        private var closed     = false

        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "setString"    => null
          case "addBatch"     =>
            batchCount += 1
            null
          case "executeBatch" =>
            state.executeBatchCount.incrementAndGet()
            Array.fill(batchCount)(1)
          case "close"        =>
            if (!closed) {
              closed = true
              state.closePreparedStatement()
            }
            null
          case "unwrap"       => null
          case "isWrapperFor" => java.lang.Boolean.FALSE
          case _              => defaultValue(method.getReturnType)
        }
      }

      proxyOf(classOf[PreparedStatement], statementHandler)
    }

    private def createResultSet(): ResultSet = {
      val resultSetHandler = new InvocationHandler {
        private var index  = -1
        private var closed = false

        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "next"         =>
            index += 1
            java.lang.Boolean.valueOf(index < state.rows.length)
          case "getString"    =>
            if (index < 0 || index >= state.rows.length) null else state.rows(index)
          case "close"        =>
            if (!closed) {
              closed = true
              state.closeResultSet()
            }
            null
          case "unwrap"       => null
          case "isWrapperFor" => java.lang.Boolean.FALSE
          case _              => defaultValue(method.getReturnType)
        }
      }

      proxyOf(classOf[ResultSet], resultSetHandler)
    }

    private def proxyOf[T](iface: Class[T], handler: InvocationHandler): T =
      Proxy.newProxyInstance(getClass.getClassLoader, Array(iface), handler).asInstanceOf[T]

    private def defaultValue(returnType: Class[_]): AnyRef =
      if (returnType == java.lang.Boolean.TYPE) java.lang.Boolean.FALSE
      else if (returnType == java.lang.Byte.TYPE) java.lang.Byte.valueOf(0.toByte)
      else if (returnType == java.lang.Short.TYPE) java.lang.Short.valueOf(0.toShort)
      else if (returnType == java.lang.Integer.TYPE) java.lang.Integer.valueOf(0)
      else if (returnType == java.lang.Long.TYPE) java.lang.Long.valueOf(0L)
      else if (returnType == java.lang.Float.TYPE) java.lang.Float.valueOf(0.0f)
      else if (returnType == java.lang.Double.TYPE) java.lang.Double.valueOf(0.0d)
      else if (returnType == java.lang.Character.TYPE) java.lang.Character.valueOf(0.toChar)
      else if (returnType == java.lang.Void.TYPE) null
      else null
  }

  final class RecordingState(dataRows: Seq[String]) {
    val rows                        = dataRows
    @volatile var lastDdl: String   = ""
    val ddlCount                    = new AtomicInteger(0)
    val queryCount                  = new AtomicInteger(0)
    val executeBatchCount           = new AtomicInteger(0)
    val connectionCloseCount        = new AtomicInteger(0)
    val statementCloseCount         = new AtomicInteger(0)
    val preparedStatementCloseCount = new AtomicInteger(0)
    val resultSetCloseCount         = new AtomicInteger(0)

    def closeConnection(): Unit = connectionCloseCount.incrementAndGet()

    def closeStatement(): Unit = statementCloseCount.incrementAndGet()

    def closePreparedStatement(): Unit = preparedStatementCloseCount.incrementAndGet()

    def closeResultSet(): Unit = resultSetCloseCount.incrementAndGet()
  }

  def withRegisteredDriver[T](driver: RecordingJdbcDriver)(body: => T): T = {
    DriverManager.registerDriver(driver)
    try body
    finally DriverManager.deregisterDriver(driver)
  }
}
