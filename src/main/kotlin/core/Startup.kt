package core

import org.apache.log4j.Logger
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.system.exitProcess

var isDatabaseConnected = false
lateinit var databaseUser: String
lateinit var databasePass: String
lateinit var jdbcurl: String

object StartupRoutine {
    private val logger = logger<StartupRoutine>()

    fun start() {
        internet()
        database()
        logger.info("Startup routine done Database Connection: $isDatabaseConnected")
    }


    private fun internet() {
        if (!checkInternetAccess()) {
            Logger.getRootLogger().fatal("No internet access!")
            exitProcess(1)
        }
        logger.info("Connected to internet.")
    }

    private fun database() {
        val url: String? = System.getenv("JDBC_DATABASE_URL")
        try {
            if (url == null) {
                backupThroughCmd()
            } else {
                val up = DatabaseControl.extractUserPassFromUrl(url)
                DriverManager.getConnection(url, up.first, up.second)
                //Memoize
                jdbcurl = url
                databaseUser = up.first
                databasePass = up.second

                isDatabaseConnected = true
            }
        } catch (noConnection: SQLException) {
            backupThroughCmd()
        }
    }

    private fun backupThroughCmd() {
        try {
            val dbUrl = ConfigurationAccessor.getConfig("DATABASE_URL")
            val trip = DatabaseControl.dbToJDBC(dbUrl)
            DriverManager.getConnection(trip.first, trip.second, trip.third).close()
            //Memoize
            jdbcurl = trip.first
            databaseUser = trip.second
            databasePass = trip.third

            isDatabaseConnected = true
            return
        } catch (noConnection: SQLException) {
            Logger.getRootLogger().fatal("Not able to connect to heroku.", noConnection)
            exitProcess(0)
        }
    }
}
