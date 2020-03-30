package core

import org.apache.log4j.Logger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object DatabaseControl {

    //Retrieve SQL (Heroku PostgreSQL) database connection //
    fun getConnection(): Connection {
        if (isDatabaseConnected) {
            try {
                return DriverManager.getConnection(jdbcurl, databaseUser, databasePass)
            } catch (failure: SQLException) {
                Logger.getRootLogger().error("Could not connect to Heroku PostgreSQL with memoized values, retrying with new parsed values")
                try {
                    val trip = getJDBCFromCmd()
                    return DriverManager.getConnection(trip.first, trip.second, trip.third)
                } catch (newFailure: SQLException) {
                    throw IllegalStateException("Could not connect from new parsed values")
                }
            }
        }
        throw IllegalStateException("Impossible to connect.")
    }


    fun getJDBCFromCmd(): Triple<String, String, String> {
        val process = Runtime.getRuntime().exec("C:\\Program Files\\heroku\\bin\\heroku.cmd config:get DATABASE_URL -a celestev5")
        val input = process.inputStream
        val reader = input.bufferedReader()
        val dbUrl = reader.readLine()

        return dbToJDBC(dbUrl)
    }

    //Extract jdbc username and password from jdbcurl
    fun extractUserPassFromUrl(jdbcurl: String): Pair<String, String> {
        val user = jdbcurl.substring(jdbcurl.indexOf("user=") + 5, jdbcurl.indexOf('&'))
        val pass = jdbcurl.substring(jdbcurl.indexOf("password=") + 9, jdbcurl.lastIndexOf('&'))
        return Pair(user, pass)
    }

    //Convert DATABASE_URL envv to JDBC_DATABASE_URL
    fun dbToJDBC(url: String): Triple<String, String, String> {
        val indexOfAt = url.indexOf('@')
        val hostname = url.substring(indexOfAt + 1)
        val secColon = url.indexOf(":", url.indexOf(":") + 1)
        val user = url.substring(url.indexOf('/') + 2, secColon)
        val pass = url.substring(url.indexOf(':', secColon) + 1, indexOfAt)

        val sb = StringBuilder("jdbc:postgresql://")
        sb.append(hostname)
        sb.append("?user=")
        sb.append(user)
        sb.append("&password=")
        sb.append(pass)
        sb.append("&sslmode=require")

        return Triple(sb.toString(), user, pass)
    }

}